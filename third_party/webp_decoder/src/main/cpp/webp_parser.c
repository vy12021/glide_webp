//
// Created by Leo on 3/20/2018.
//

#include <stdio.h>
#include <string.h>
#include <android/bitmap.h>
#include "src/webp/demux.h"
#include "libwebp/jni_runtime.h"
#include "libwebp/src/webp/mux_types.h"
#include "libwebp/tools/webpinfo.h"
#include "tag.h"

#ifdef __cplusplus
extern "C" {
#endif

struct WebpParser {

    WebPDemuxer *demuxer;
    WebPIterator iterator;

};

typedef struct WebpParser WebpParser;

JNI_STATIC_METHOD(PACKAGE_ROOT, StandardWebpDecoder, nativeInitWebpParser, jlong)
(JNIEnv *env, jclass class, jobject byte_buffer) {
    uint8_t* buffer = (*env)->GetDirectBufferAddress(env, byte_buffer);
    size_t capacity = (size_t) (*env)->GetDirectBufferCapacity(env, byte_buffer);
    WebPData webPData;
    webPData.bytes = buffer;
    webPData.size = capacity;
    WebpParser* parser = (WebpParser *)malloc(sizeof(WebpParser));
    parser->demuxer = WebPDemux(&webPData);
    if (!parser->demuxer) {
        LOGE("webp_parser", "nativeInitWebpParser failed!");
    }
    return (jlong) parser;
}

JNI_STATIC_METHOD(PACKAGE_ROOT, StandardWebpDecoder, nativeGetWebpFrame, jint)
(JNIEnv *env, jclass class, jlong parser_pointer, jobject bitmap, jint frame_index){
    int index = frame_index;
    WebpParser *webpParser;
    if (parser_pointer) {
        webpParser = (WebpParser *) parser_pointer;
    } else {
        LOGE("webp_parser", "Null pointer of parser");
        return 0;
    }
    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    if (bitmapInfo.height * bitmapInfo.height == 0) {
        LOGE("webp_parser", "nativeGetWebpFrame: Invalid bitmap!");
        return 0;
    }
    WebPDecoderConfig config;
    WebPInitDecoderConfig(&config);
    if(!WebPDemuxGetFrame(webpParser->demuxer, index, &webpParser->iterator)) {
        LOGE("nativeGetWebpFrame", "WebPDemuxGetFrame() fail...");
        return 0;
    }
    VP8StatusCode status = WebPGetFeatures(webpParser->iterator.fragment.bytes,
                                           webpParser->iterator.fragment.size, &config.input);
    if(status != VP8_STATUS_OK) {
        LOGE("nativeGetWebpFrame", "WebPGetFeatures() fail...");
        return 0;
    }

    config.options.flip = 0;
    config.options.bypass_filtering = 1;
    config.options.no_fancy_upsampling = 1;
    config.options.use_scaling = 1;
    config.options.scaled_width = bitmapInfo.width;
    config.options.scaled_height = bitmapInfo.height;

    config.output.width  = config.input.width;
    config.output.height = config.input.height;
    config.output.colorspace = MODE_rgbA;
    config.output.is_external_memory = 1;
    void *pixels;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    config.output.private_memory = pixels;
    config.output.u.RGBA.stride  = bitmapInfo.stride;
    config.output.u.RGBA.rgba  = pixels;
    config.output.u.RGBA.size  = bitmapInfo.height * bitmapInfo.stride;
    status = WebPDecode(webpParser->iterator.fragment.bytes,
                        webpParser->iterator.fragment.size, &config);
    AndroidBitmap_unlockPixels(env, bitmap);
    WebPFreeDecBuffer(&config.output);
    if (VP8_STATUS_OK != status) {
        LOGE("webp_parser", "WebPDecode of nativeGetWebpFrame failed!");
        return 0;
    }
    return 1;
}

JNI_STATIC_METHOD(PACKAGE_ROOT, StandardWebpDecoder, nativeReleaseParser, void)
(JNIEnv *env, jclass class, jlong demux_pointer) {
    if (demux_pointer) {
        free((void *)demux_pointer);
    }
}

#ifdef __cplusplus
}
#endif