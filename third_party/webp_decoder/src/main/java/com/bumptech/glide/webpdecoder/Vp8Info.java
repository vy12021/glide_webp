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

    static final int[] Vp8BitMask = {
        0,
        0x000001, 0x000003, 0x000007, 0x00000f,
        0x00001f, 0x00003f, 0x00007f, 0x0000ff,
        0x0001ff, 0x0003ff, 0x0007ff, 0x000fff,
        0x001fff, 0x003fff, 0x007fff, 0x00ffff,
        0x01ffff, 0x03ffff, 0x07ffff, 0x0fffff,
        0x1fffff, 0x3fffff, 0x7fffff, 0xffffff
    };

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
    Vp8Format format = Vp8Format.Mixed;
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
