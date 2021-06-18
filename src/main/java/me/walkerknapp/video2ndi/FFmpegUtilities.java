package me.walkerknapp.video2ndi;

import me.walkerknapp.devolay.DevolayFrameFourCCType;
import org.bytedeco.javacpp.BytePointer;

import static org.bytedeco.ffmpeg.global.avutil.*;

public class FFmpegUtilities {
    public static DevolayFrameFourCCType pixFmtToFourCC(int pixFmt) {
        switch (pixFmt) {
            case AV_PIX_FMT_UYVY422: return DevolayFrameFourCCType.UYVY;
            case AV_PIX_FMT_NV12: return DevolayFrameFourCCType.NV12;
            case AV_PIX_FMT_YUV420P: return DevolayFrameFourCCType.I420;
            case AV_PIX_FMT_BGRA: return DevolayFrameFourCCType.BGRA;
            case AV_PIX_FMT_BGR0: return DevolayFrameFourCCType.BGRX;
            case AV_PIX_FMT_RGBA: return DevolayFrameFourCCType.RGBA;
            case AV_PIX_FMT_RGB0: return DevolayFrameFourCCType.RGBX;
            default:
                // TODO: If we get here, we would need to use FFmpeg's swscale to turn the pixel format into something NDI can understand.
                throw new IllegalArgumentException("Cannot handle pixel format of " + av_get_pix_fmt_name(pixFmt).getString());
        }
    }

    public static String formatAvError(int error) {
        BytePointer bytePointer = new BytePointer(1024);
        av_make_error_string(bytePointer, 1024, error);
        return bytePointer.getString();
    }
}
