package me.walkerknapp.video2ndi;

import org.bytedeco.ffmpeg.avcodec.AVPacket;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;

public class PacketPool implements Closeable {
    private final LinkedBlockingQueue<AVPacket> freePackets;
    private final LinkedBlockingQueue<AVPacket> audioQueue;
    private final LinkedBlockingQueue<AVPacket> videoQueue;

    public PacketPool(int poolSize) {
        this.freePackets = new LinkedBlockingQueue<>();
        this.audioQueue = new LinkedBlockingQueue<>();
        this.videoQueue = new LinkedBlockingQueue<>();

        for (int i = 0; i < poolSize; i++) {
            AVPacket packet = av_packet_alloc();
            if (packet == null) {
                throw new IllegalStateException("Failed to allocate a packet for demuxing.");
            }
            freePackets.add(packet);
        }
    }

    public AVPacket popCleanPacket() {
        return freePackets.poll();
    }

    public void pushCleanPacket(AVPacket packet) {
        freePackets.add(packet);
    }

    public void queueVideoPacket(AVPacket packet) {
        videoQueue.add(packet);
    }

    public AVPacket popVideoPacket() {
        return this.videoQueue.poll();
    }

    public void queueAudioPacket(AVPacket packet) {
        audioQueue.add(packet);
    }

    public AVPacket popAudioPacket() {
        return this.audioQueue.poll();
    }

    @Override
    public void close() throws IOException {
        for (AVPacket packet : freePackets) {
            av_packet_free(packet);
        }
    }
}
