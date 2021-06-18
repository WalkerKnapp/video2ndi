package me.walkerknapp.video2ndi;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.javacpp.BytePointer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN;

public class AudioDecodeRunner implements Runnable {

    private final DecodeState decodeState;
    private final NdiSendState sendState;

    private final int audioSampleSize;
    private final int audioPlanes;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public AudioDecodeRunner(DecodeState decodeState, NdiSendState sendState) {
        this.decodeState = decodeState;
        this.sendState = sendState;

        // Determine the size of data we will read from the FFmpeg stream
        switch (decodeState.audioStreamInfo.decoderContext.sample_fmt()) {
            case AV_SAMPLE_FMT_FLTP:
                audioPlanes = decodeState.audioStreamInfo.decoderContext.channels();
                audioSampleSize = av_get_bytes_per_sample(decodeState.audioStreamInfo.decoderContext.sample_fmt());
                break;
            case AV_SAMPLE_FMT_S16:
            case AV_SAMPLE_FMT_S32:
            case AV_SAMPLE_FMT_FLT:
                audioPlanes = 1;
                audioSampleSize = av_get_bytes_per_sample(decodeState.audioStreamInfo.decoderContext.sample_fmt()) * decodeState.audioStreamInfo.decoderContext.channels();
                break;
            default:
                throw new IllegalArgumentException("Cannot decode audio format "
                        + av_get_sample_fmt_name(decodeState.audioStreamInfo.decoderContext.sample_fmt()));
        }
    }

    @Override
    public void run() {
        try {
            int ret;

            while (running.get()) {
                AVPacket encodedPacket = null;
                while (encodedPacket == null && running.get()) {
                    encodedPacket = decodeState.packetPool.popAudioPacket();
                }
                if (!running.get()) {
                    break;
                }

                // Send the packet to the audio decoder
                if ((ret = avcodec_send_packet(decodeState.audioStreamInfo.decoderContext, encodedPacket)) < 0) {
                    throw new IllegalStateException("Failed to send packet to audio decoder: " + FFmpegUtilities.formatAvError(ret));
                }

                // Receive frames from the decoder
                while (ret >= 0) {
                    ret = avcodec_receive_frame(decodeState.audioStreamInfo.decoderContext, decodeState.audioStreamInfo.decodedFrame);

                    if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
                        // The content is finished or another packet is needed
                        break;
                    } else if (ret < 0) {
                        throw new IllegalStateException("Failed to decode audio frame: " + FFmpegUtilities.formatAvError(ret));
                    }

                    int outputAudioPlaneSize = audioSampleSize * decodeState.audioStreamInfo.decodedFrame.nb_samples();
                    ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioPlanes * outputAudioPlaneSize);
                    BytePointer buffer = new BytePointer(directBuffer);
                    for (int i = 0; i < audioPlanes; i++) {
                        buffer.position((long) i * outputAudioPlaneSize)
                                .put(decodeState.audioStreamInfo.decodedFrame.data(i).limit(outputAudioPlaneSize));
                    }

                    sendState.sendAudioFrameBySampleFmt(directBuffer, outputAudioPlaneSize,
                            decodeState.audioStreamInfo.decodedFrame.nb_samples(),
                            decodeState.audioStreamInfo.decoderContext.sample_fmt());
                }

                // Release the packet
                av_packet_unref(encodedPacket);
                decodeState.packetPool.pushCleanPacket(encodedPacket);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void close() {
        this.running.set(false);
    }
}
