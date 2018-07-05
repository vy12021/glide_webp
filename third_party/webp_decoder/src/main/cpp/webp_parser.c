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

#define JAVA_METHOD_CONSTRUCTOR "<init>"
#define JAVA_CLS_WEBP_HEADER "com/bumptech/glide/webpdecoder/WebpHeader"
#define JAVA_CLS_WEBP_FRAME "com/bumptech/glide/webpdecoder/WebpFrame"

struct WebpParser {

    WebPDemuxer *demuxer;
    WebPIterator iterator;

};

typedef struct WebpParser WebpParser;

JNI_STATIC_METHOD(PACKAGE_ROOT, Helper, setStdoutFile, jboolean)
(JNIEnv *env, jclass class, jstring file) {
    const char *in_file;
    jboolean ret = JNI_TRUE;
    in_file = (*env)->GetStringUTFChars(env, file, &ret);
    if (!RedirectStdout(in_file)) {
        LOGE("setStdoutFile", "failed to setStdoutFile");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNI_STATIC_METHOD(PACKAGE_ROOT, StandardWebpDecoder, nativeGetWebpInfo, jobject)
(JNIEnv *env, jclass clazz, jstring file) {
    const char *in_file;
    jboolean flag = JNI_TRUE;
    in_file = (*env)->GetStringUTFChars(env, file, &flag);
    int quiet = 0, show_diag = 0, show_summary = 0;
    int parse_bitstream = 0;
    WebPInfoStatus webp_info_status;
    WebPInfo webp_info;
    WebPData webp_data;
    WebPInfoInit(&webp_info);
    webp_info.quiet_ = quiet;
    webp_info.show_diagnosis_ = show_diag;
    webp_info.show_summary_ = show_summary;
    webp_info.parse_bitstream_ = parse_bitstream;
    if (in_file == NULL || !ReadFileToWebPData(in_file, &webp_data)) {
        webp_info_status = WEBP_INFO_INVALID_COMMAND;
        LOGE(TO_STRING(PACKAGE_ROOT), "Failed to open input file %s.\n", in_file);
    }
    if (!webp_info.quiet_) printf("File: %s\n", in_file);
    webp_info_status = AnalyzeWebP(&webp_info, &webp_data);

    switch (webp_info_status) {
        case WEBP_INFO_OK:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse complete: %s", GetWebPInfoDesc(&webp_info, NULL));
            break;
        case WEBP_INFO_PARSE_ERROR:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp PARSE_ERROR");
            break;
        case WEBP_INFO_BITSTREAM_ERROR:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse BITSTREAM_ERROR");
            break;
        case WEBP_INFO_INVALID_COMMAND:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse INVALID_COMMAND");
            break;
        case WEBP_INFO_INVALID_PARAM:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse INVALID_PARAM");
            break;
        case WEBP_INFO_MISSING_DATA:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse MISSING_DATA");
            break;
        case WEBP_INFO_TRUNCATED_DATA:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse TRUNCATED_DATA");
            break;
    }
    WebPDataClear(&webp_data);
    LOGE(TO_STRING(PACKAGE_ROOT), "输入文件: %s", in_file);
    jclass jcls = (*env)->FindClass(env, JAVA_CLS_WEBP_HEADER);
    jmethodID construct = (*env)->GetMethodID(env, jcls, JAVA_METHOD_CONSTRUCTOR, "()V");
    jobject jinfo = (*env)->NewObject(env, jcls, construct);
    return jinfo;
}

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
        LOGE("webp_parser", "nativeInitWebpDemux failed!");
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
        LOGE("webp_parser", "Null pointer for demux");
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
    //
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

JNI_STATIC_METHOD(PACKAGE_ROOT, StandardWebpDecoder, nativeGetWebpFrameByBytes, jintArray)
(JNIEnv *env, jclass class, jlong parser_pointer, jbyteArray pixels, jint size,
 jint scaled_width, jint scaled_height, jint stride, jint frame_index){
    int index = frame_index;
    WebpParser *webpParser;
    if (parser_pointer) {
        webpParser = (WebpParser *) parser_pointer;
    } else {
        LOGE("nativeGetWebpFrameByBytes", "Null pointer for demux");
        return NULL;
    }
    if (!pixels || size == 0) {
        LOGE("nativeGetWebpFrameByBytes", "nativeGetWebpFrameByBytes: Invalid pixel bytes!");
        return NULL;
    }
    WebPDecoderConfig config;
    WebPInitDecoderConfig(&config);
    if(!WebPDemuxGetFrame(webpParser->demuxer, index, &webpParser->iterator)) {
        LOGE("nativeGetWebpFrameByBytes", "WebPDemuxGetFrame() fail...");
        return NULL;
    }
    VP8StatusCode status = WebPGetFeatures(webpParser->iterator.fragment.bytes,
                                           webpParser->iterator.fragment.size, &config.input);
    if(status != VP8_STATUS_OK) {
        LOGE("nativeGetWebpFrameByBytes", "WebPGetFeatures() fail...");
        return NULL;
    }
    //
    config.options.flip = 0;
    config.options.bypass_filtering = 1;
    config.options.no_fancy_upsampling = 1;

    config.options.use_scaling = 1;
    config.options.scaled_width = scaled_width;
    config.options.scaled_height = scaled_height;

    config.output.width  = config.input.width;
    config.output.height = config.input.height;
    config.output.colorspace = MODE_rgbA;
    config.output.is_external_memory = 1;
    config.output.private_memory = pixels;
    config.output.u.RGBA.stride  = stride;
    config.output.u.RGBA.rgba  = config.output.private_memory;
    config.output.u.RGBA.size  = config.output.height * (size_t) stride;
    status = WebPDecode(webpParser->iterator.fragment.bytes,
                        webpParser->iterator.fragment.size, &config);
    WebPFreeDecBuffer(&config.output);
    if (VP8_STATUS_OK != status) {
        LOGE("nativeGetWebpFrameByBytes", "WebPDecode failed!");
        return NULL;
    }
    return pixels;
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