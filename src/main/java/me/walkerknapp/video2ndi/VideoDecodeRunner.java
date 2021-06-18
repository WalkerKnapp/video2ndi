package me.walkerknapp.video2ndi;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.javacpp.BytePointer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class VideoDecodeRunner implements Runnable {

    private final DecodeState decodeState;
    private final NdiSendState sendState;

    private final int[] videoPlaneSizes;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public VideoDecodeRunner(DecodeState decodeState, NdiSendState sendState) {
        this.decodeState = decodeState;
        this.sendState = sendState;

        int pixelFormat = decodeState.videoStreamInfo.decoderContext.pix_fmt();

        videoPlaneSizes = new int[av_pix_fmt_count_planes(pixelFormat)];
        int videoBufferSize = 0;

        for (int j = 0; j < videoPlaneSizes.length; j++) {
            int heightShift = (j == 1 || j == 2) ? av_pix_fmt_desc_get(pixelFormat).log2_chroma_h() : 0;

            videoPlaneSizes[j] = av_image_get_linesize(pixelFormat, decodeState.videoStreamInfo.decoderContext.width(), j)
                    * ((decodeState.videoStreamInfo.decoderContext.height() + (1 << heightShift) - 1) >> heightShift);

            videoBufferSize += videoPlaneSizes[j];
        }

        sendState.prepareVideoFrames(videoBufferSize);
    }

    @Override
    public void run() {
        try {
            int ret;

            while (running.get()) {
                AVPacket encodedPacket = null;
                while (encodedPacket == null && running.get()) {
                    encodedPacket = decodeState.packetPool.popVideoPacket();
                }
                if (!running.get()) {
                    break;
                }

                // Send the packet to the video decoder
                if ((ret = avcodec_send_packet(decodeState.videoStreamInfo.decoderContext, encodedPacket)) < 0) {
                    throw new IllegalStateException("Failed to send packet to video decoder: " + FFmpegUtilities.formatAvError(ret));
                }

                // Receive frames from the decoder
                while (ret >= 0) {
                    ret = avcodec_receive_frame(decodeState.videoStreamInfo.decoderContext, decodeState.videoStreamInfo.decodedFrame);

                    if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
                        // The content is finished or another packet is needed
                        break;
                    } else if (ret < 0) {
                        throw new IllegalStateException("Failed to decode video frame: " + FFmpegUtilities.formatAvError(ret));
                    }

                    //BytePointer bufferPointer = new BytePointer(sendState.getCurrentFrameBuffer());
                    ByteBuffer buffer = sendState.getCurrentFrameBuffer();

                    // Use one continuous buffer to store video data in
                    int continuousIndex = 0;
                    for (int j = 0; j < videoPlaneSizes.length; j++) {
                        BytePointer planeBuffer = decodeState.videoStreamInfo.decodedFrame.data(j);

                        //bufferPointer.position(continuousIndex);
                        buffer.position(continuousIndex);
                        buffer.put(planeBuffer.position(0).limit(videoPlaneSizes[j]).asBuffer());
                        //bufferPointer.put(planeBuffer.position(0).limit(videoPlaneSizes[j]));

                        continuousIndex += videoPlaneSizes[j];
                    }

                    sendState.sendCurrentFrame();
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
