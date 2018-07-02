package com.bumptech.glide.webpdecoder;

import android.support.annotation.ColorInt;
import static com.bumptech.glide.webpdecoder.WebpHeaderParser.*;

/**
 * 解析Webp头部信息
 */
public class WebpInfo {

    int canvasWidth;

    int canvasHeight;

    int loopCount;

    int numFrames;

    int chunkCounts[] = new int[ChunkId.CHUNK_ID_TYPES];

    int anmfSubchunkCounts[] = new int[3];  // 0 VP8; 1 VP8L; 2 ALPH.

    @ColorInt
    int bgColor;

    @WebpFeatureFlag
    int featureFlags = ALL_VALID_FLAGS;

    boolean hasAlpha;

    // Used for parsing ANMF chunks.
    int frameWidth, frameHeight;

    int animFrameDataSize;

}
