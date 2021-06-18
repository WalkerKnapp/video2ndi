package me.walkerknapp.video2ndi;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;

import java.io.Closeable;
import java.io.IOException;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class DecodeState implements Closeable {
    public static class StreamInfo {
        public int streamIndex;

        public AVStream stream;
        public AVCodec codec;
        public AVCodecContext decoderContext;

        public AVFrame decodedFrame;
    }

    public AVFormatContext demuxerContext;

    public StreamInfo videoStreamInfo;
    public boolean hasVideoStream;
    public StreamInfo audioStreamInfo;
    public boolean hasAudioStream;

    public PacketPool packetPool;

    public DecodeState(String videoFilePath) {
        setupDemuxer(videoFilePath);

        this.packetPool = new PacketPool(16);
    }

    private void setupDemuxer(String videoFilePath) {
        int ret;

        // Open a context for demuxing the mp4
        if ((demuxerContext = avformat_alloc_context()) == null) {
            throw new IllegalStateException("Cannot allocate format context for video decoding.");
        }

        // Open the video file at the path we specify
        if ((ret = avformat_open_input(demuxerContext, videoFilePath, null, null)) < 0) {
            throw new IllegalStateException("Cannot open video at path " + videoFilePath + ": " + FFmpegUtilities.formatAvError(ret));
        }
        if ((ret = avformat_find_stream_info(demuxerContext, (AVDictionary) null)) < 0) {
            throw new IllegalStateException("Could not read information from video file: " + FFmpegUtilities.formatAvError(ret));
        }

        videoStreamInfo = openStream(AVMEDIA_TYPE_VIDEO);
        audioStreamInfo = openStream(AVMEDIA_TYPE_AUDIO);

        this.hasVideoStream = videoStreamInfo != null;
        this.hasAudioStream = audioStreamInfo != null;
    }

    public StreamInfo openStream(int mediaType) {
        int ret;

        // Try to find a stream of mediaType from our demuxer
        if ((ret = av_find_best_stream(demuxerContext, mediaType, -1, -1, (AVCodec) null, 0)) < 0) {
            System.err.println("Could not find stream of type " + mediaType + " in stream: " + FFmpegUtilities.formatAvError(ret));
            return null;
        }

        StreamInfo streamInfo = new StreamInfo();

        streamInfo.streamIndex = ret;
        streamInfo.stream = demuxerContext.streams(streamInfo.streamIndex);

        // Find a decoder for the stream
        if ((streamInfo.codec = avcodec_find_decoder(streamInfo.stream.codecpar().codec_id())) == null) {
            System.err.println("Could not find a decoder for stream of type " + mediaType
                    + " for codec " + avcodec_get_name(streamInfo.stream.codecpar().codec_id()).getString());
            return null;
        }

        // Allocate an instance of that decoder
        if ((streamInfo.decoderContext = avcodec_alloc_context3(streamInfo.codec)) == null) {
            System.err.println("Could not allocate a decoder for stream of type " + mediaType
                    + " for codec " + avcodec_get_name(streamInfo.stream.codecpar().codec_id()).getString());
            return null;
        }

        // Copy the needed parameters to that decoder
        if ((ret = avcodec_parameters_to_context(streamInfo.decoderContext, streamInfo.stream.codecpar())) < 0) {
            System.err.println("Could not copy parameters to decoder context: " + FFmpegUtilities.formatAvError(ret));
            return null;
        }
        if (mediaType == AVMEDIA_TYPE_VIDEO) {
            // Framerate isn't in codecpar, for some reason
            streamInfo.decoderContext.framerate(streamInfo.stream.r_frame_rate());
        }

        // Finally, open our decoder for this stream
        if ((ret = avcodec_open2(streamInfo.decoderContext, streamInfo.codec, (AVDictionary) null)) < 0) {
            System.err.println("Could not open decoder of type " + mediaType + " in stream: " + FFmpegUtilities.formatAvError(ret));
            return null;
        }

        if ((streamInfo.decodedFrame = av_frame_alloc()) == null) {
            System.err.println("Could not allocate a frame for decoder of type " + mediaType + " in stream.");
            return null;
        }

        return streamInfo;
    }

    @Override
    public void close() throws IOException {
        avformat_close_input(demuxerContext);

        packetPool.close();

        avformat_free_context(demuxerContext);
    }
}
