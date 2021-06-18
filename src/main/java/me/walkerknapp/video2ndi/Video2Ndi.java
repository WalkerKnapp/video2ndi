package me.walkerknapp.video2ndi;

import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Video2Ndi {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: [path to video]");
            return;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(3);

        DecodeState decodeState = new DecodeState(args[0]);
        NdiSendState sendState = new NdiSendState(Paths.get(args[0]).getFileName().toString(), decodeState);

        DemuxRunner demuxRunner = new DemuxRunner(decodeState);
        AudioDecodeRunner audioDecodeRunner = new AudioDecodeRunner(decodeState, sendState);
        VideoDecodeRunner videoDecodeRunner = new VideoDecodeRunner(decodeState, sendState);

        executorService.submit(demuxRunner);
        executorService.submit(audioDecodeRunner);
        executorService.submit(videoDecodeRunner);

        demuxRunner.awaitCompletion();
        audioDecodeRunner.close();
        videoDecodeRunner.close();

        executorService.shutdown();
    }
}
