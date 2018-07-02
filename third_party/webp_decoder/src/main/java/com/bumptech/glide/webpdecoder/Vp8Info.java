package com.bumptech.glide.webpdecoder;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * vp8 decode from image chunk
 */
class Vp8Info {

    final static int VP8_STATUS_OK                      = 0;
    final static int VP8_STATUS_OUT_OF_MEMORY           = 1;
    final static int VP8_STATUS_INVALID_PARAM           = 2;
    final static int VP8_STATUS_BITSTREAM_ERROR         = 3;
    final static int VP8_STATUS_UNSUPPORTED_FEATURE     = 4;
    final static int VP8_STATUS_SUSPENDED               = 5;
    final static int VP8_STATUS_USER_ABORT              = 6;
    final static int VP8_STATUS_NOT_ENOUGH_DATA         = 7;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {VP8_STATUS_OK, VP8_STATUS_OUT_OF_MEMORY, VP8_STATUS_INVALID_PARAM,
            VP8_STATUS_BITSTREAM_ERROR, VP8_STATUS_UNSUPPORTED_FEATURE, VP8_STATUS_SUSPENDED,
            VP8_STATUS_USER_ABORT, VP8_STATUS_NOT_ENOUGH_DATA})
    @interface Vp8ParseStatus {
    }

    final static int COMPRESS_MIXED         = 0;
    final static int COMPRESS_LOSSY         = 1;
    final static int COMPRESS_LOSSLESS      = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {COMPRESS_MIXED, COMPRESS_LOSSY, COMPRESS_LOSSLESS})
    @interface Vp8CompressFormat {
    }

    @Vp8ParseStatus
    int status = VP8_STATUS_OK;

    // Width in pixels, as read from the bitstream.
    int width;
    // Height in pixels, as read from the bitstream.
    int height;
    // True if the bitstream contains an alpha channel.
    boolean hasAlpha;
    // True if the bitstream is an animation.
    boolean hasAnimation;
    // 0 = undefined (/mixed), 1 = lossy, 2 = lossless
    @Vp8CompressFormat
    int format = COMPRESS_MIXED;
    // padding for later use
    int pad[] = new int[5];

    @Override
    public String toString() {
        return "Vp8Info{" +
                "status=" + status +
                ", width=" + width +
                ", height=" + height +
                ", hasAlpha=" + hasAlpha +
                ", hasAnimation=" + hasAnimation +
                ", format=" + format +
                ", pad=" + Arrays.toString(pad) +
                '}';
    }
}
