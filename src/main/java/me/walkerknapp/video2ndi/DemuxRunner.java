package me.walkerknapp.video2ndi;

import org.bytedeco.ffmpeg.avcodec.AVPacket;

import java.util.concurrent.CompletableFuture;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_ref;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;

public class DemuxRunner implements Runnable {
    private final DecodeState state;

    private final CompletableFuture<Void> onCompletion;

    public DemuxRunner(DecodeState state) {
        this.state = state;

        this.onCompletion = new CompletableFuture<>();
    }

    @Override
    public void run() {
        int ret;

        while (true) {
            AVPacket packet = null;
            while (packet == null) {
                packet = state.packetPool.popCleanPacket();
            }

            // Read a packet from the video file
            if ((ret = av_read_frame(state.demuxerContext, packet)) < 0) {
                if (ret == AVERROR_EOF) {
                    // We've reached the end of the video file
                    break;
                } else {
                    // An error has occurred in demuxing
                    this.onCompletion.complete(null);
                    throw new IllegalStateException("Failed to demux video file: " + FFmpegUtilities.formatAvError(ret));
                }
            }

            // If the buffer is null, we only have a temporary reference to this packet until the next av_read_frame call.
            // If so, before sending it to the encoders, we need to make a copy of it onto a new clean frame and unref this frame.
            if (packet.buf().isNull()) {
                AVPacket alternatePacket = null;
                while (alternatePacket == null) {
                    alternatePacket = state.packetPool.popCleanPacket();
                }

                av_packet_ref(alternatePacket, packet);

                av_packet_unref(packet);
                state.packetPool.pushCleanPacket(packet);

                packet = alternatePacket;
            }

            if (state.hasVideoStream && packet.stream_index() == state.videoStreamInfo.streamIndex) {
                state.packetPool.queueVideoPacket(packet);
            } else if (state.hasAudioStream && packet.stream_index() == state.audioStreamInfo.streamIndex) {
                state.packetPool.queueAudioPacket(packet);
            } else {
                // Immediately release this packet
                av_packet_unref(packet);
                state.packetPool.pushCleanPacket(packet);
            }
        }

        this.onCompletion.complete(null);
    }

    public void awaitCompletion() {
        onCompletion.join();
    }
}
