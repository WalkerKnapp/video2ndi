package me.walkerknapp.video2ndi;

import me.walkerknapp.devolay.*;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;

import java.nio.ByteBuffer;

import static org.bytedeco.ffmpeg.global.avutil.*;

public class NdiSendState {
    public DevolaySender sender;

    public DevolayAudioFrame audioFrame;
    public DevolayAudioFrameInterleaved16s audioFrameInterleaved16s;
    public DevolayAudioFrameInterleaved32s audioFrameInterleaved32s;
    public DevolayAudioFrameInterleaved32f audioFrameInterleaved32f;

    public ByteBuffer[] videoBuffers;
    public DevolayVideoFrame[] videoFrames;
    public int currentFrame;

    public NdiSendState(String filename, DecodeState decodeState) {
        Devolay.loadLibraries();

        sender = new DevolaySender(filename, null, true, true);

        if (decodeState.hasVideoStream) {
            videoFrames = new DevolayVideoFrame[2];
            videoBuffers = new ByteBuffer[2];
            for (int i = 0; i < videoFrames.length; i++) {
                videoFrames[i] = new DevolayVideoFrame();
                videoFrames[i].setResolution(decodeState.videoStreamInfo.decoderContext.width(),
                        decodeState.videoStreamInfo.decoderContext.height());
                videoFrames[i].setFourCCType(FFmpegUtilities.pixFmtToFourCC(decodeState.videoStreamInfo.decoderContext.pix_fmt()));
                videoFrames[i].setFrameRate(decodeState.videoStreamInfo.decoderContext.framerate().num(),
                        decodeState.videoStreamInfo.decoderContext.framerate().den());
            }
        }

        if (decodeState.hasAudioStream) {
            // Pick which types of NDI frames to use depending on the format of our audio
            AVCodecContext decoderContext = decodeState.audioStreamInfo.decoderContext;
            int sampleFmt = decoderContext.sample_fmt();
            switch (sampleFmt) {
                case AV_SAMPLE_FMT_FLTP:
                    audioFrame = new DevolayAudioFrame();
                    audioFrame.setSampleRate(decoderContext.sample_rate());
                    audioFrame.setChannels(decoderContext.channels());
                    break;
                case AV_SAMPLE_FMT_S16:
                    audioFrameInterleaved16s = new DevolayAudioFrameInterleaved16s();
                    audioFrameInterleaved16s.setSampleRate(decoderContext.sample_rate());
                    audioFrameInterleaved16s.setChannels(decoderContext.channels());
                    break;
                case AV_SAMPLE_FMT_S32:
                    audioFrameInterleaved32s = new DevolayAudioFrameInterleaved32s();
                    audioFrameInterleaved32s.setSampleRate(decoderContext.sample_rate());
                    audioFrameInterleaved32s.setChannels(decoderContext.channels());
                    break;
                case AV_SAMPLE_FMT_FLT:
                    audioFrameInterleaved32f = new DevolayAudioFrameInterleaved32f();
                    audioFrameInterleaved32f.setSampleRate(decoderContext.sample_rate());
                    audioFrameInterleaved32f.setChannels(decoderContext.channels());
                default:
                    throw new IllegalArgumentException("Cannot decode audio format "
                            + av_get_sample_fmt_name(decoderContext.sample_fmt()));
            }
        }
    }

    public void prepareVideoFrames(int bufferSize) {
        for (int i = 0; i < videoFrames.length; i++) {
            videoBuffers[i] = ByteBuffer.allocateDirect(bufferSize);
            videoFrames[i].setData(videoBuffers[i]);
        }
    }

    public void sendAudioFrameBySampleFmt(ByteBuffer buffer, int channelStride, int samples, int sampleFmt) {
        switch (sampleFmt) {
            case AV_SAMPLE_FMT_FLTP:
                audioFrame.setData(buffer);
                audioFrame.setChannelStride(channelStride);
                audioFrame.setSamples(samples);
                sender.sendAudioFrame(audioFrame);
                break;
            case AV_SAMPLE_FMT_S16:
                audioFrameInterleaved16s.setData(buffer);
                audioFrameInterleaved16s.setSamples(samples);
                sender.sendAudioFrameInterleaved16s(audioFrameInterleaved16s);
                break;
            case AV_SAMPLE_FMT_S32:
                audioFrameInterleaved32s.setData(buffer);
                audioFrameInterleaved32s.setSamples(samples);
                sender.sendAudioFrameInterleaved32s(audioFrameInterleaved32s);
                break;
            case AV_SAMPLE_FMT_FLT:
                audioFrameInterleaved32f.setData(buffer);
                audioFrameInterleaved32f.setSamples(samples);
                sender.sendAudioFrameInterleaved32f(audioFrameInterleaved32f);
                break;
            default:
                throw new AssertionError("Audio stream changed sample format, unsupported.");
        }
    }

    public ByteBuffer getCurrentFrameBuffer() {
        return videoBuffers[currentFrame];
    }

    public void sendCurrentFrame() {
        sender.sendVideoFrameAsync(videoFrames[currentFrame]);
        currentFrame = (currentFrame + 1) % videoFrames.length;
    }
}
