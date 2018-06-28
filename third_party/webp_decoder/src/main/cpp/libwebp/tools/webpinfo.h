//
// Created by Leo on 3/28/2018.
//

#ifndef GLIDE_PARENT_WEBPINFO_H
#define GLIDE_PARENT_WEBPINFO_H

#include "imageio/imageio_util.h"
#include "src/webp/decode.h"
#include "src/webp/format_constants.h"
#include "src/webp/mux_types.h"

typedef enum {
    WEBP_INFO_OK = 0,
    WEBP_INFO_TRUNCATED_DATA,
    WEBP_INFO_PARSE_ERROR,
    WEBP_INFO_INVALID_PARAM,
    WEBP_INFO_BITSTREAM_ERROR,
    WEBP_INFO_MISSING_DATA,
    WEBP_INFO_INVALID_COMMAND
} WebPInfoStatus;

typedef enum ChunkID {
    CHUNK_VP8,
    CHUNK_VP8L,
    CHUNK_VP8X,
    CHUNK_ALPHA,
    CHUNK_ANIM,
    CHUNK_ANMF,
    CHUNK_ICCP,
    CHUNK_EXIF,
    CHUNK_XMP,
    CHUNK_UNKNOWN,
    CHUNK_TYPES = CHUNK_UNKNOWN
} ChunkID;

typedef struct {
    size_t start_;
    size_t end_;
    const uint8_t *buf_;
} MemBuffer;

typedef struct {
    size_t offset_;
    size_t size_;
    const uint8_t *payload_;
    ChunkID id_;
} ChunkData;

typedef struct WebPInfo {
    int canvas_width_;
    int canvas_height_;
    int loop_count_;
    int num_frames_;
    int chunk_counts_[CHUNK_TYPES];
    int anmf_subchunk_counts_[3];  // 0 VP8; 1 VP8L; 2 ALPH.
    uint32_t bgcolor_;
    int feature_flags_;
    int has_alpha_;
    // Used for parsing ANMF chunks.
    int frame_width_, frame_height_;
    size_t anim_frame_data_size_;
    int is_processing_anim_frame_, seen_alpha_subchunk_, seen_image_subchunk_;
    // Print output control.
    int quiet_, show_diagnosis_, show_summary_;
    int parse_bitstream_;
} WebPInfo;

/**
 * init
 * @param webp_info
 */
void WebPInfoInit(WebPInfo *const webp_info);

/**
 * read data from file
 * @param filename  file path
 * @param webp_data
 * @return
 */
int ReadFileToWebPData(const char *const filename,
                       WebPData *const webp_data);

WebPInfoStatus ParseLossySegmentHeader(const WebPInfo *const webp_info,
                                       const uint8_t *const data,
                                       size_t data_size,
                                       uint64_t *const bit_pos);

WebPInfoStatus ParseLossyFilterHeader(const WebPInfo *const webp_info,
                                      const uint8_t *const data,
                                      size_t data_size,
                                      uint64_t *const bit_pos);

WebPInfoStatus ParseLossyHeader(const ChunkData *const chunk_data,
                                const WebPInfo *const webp_info);

WebPInfoStatus ParseLosslessTransform(WebPInfo *const webp_info,
                                      const uint8_t *const data,
                                      size_t data_size,
                                      uint64_t *const bit_pos);

WebPInfoStatus ParseLosslessHeader(const ChunkData *const chunk_data,
                                   WebPInfo *const webp_info);

WebPInfoStatus ParseAlphaHeader(const ChunkData *const chunk_data,
                                WebPInfo *const webp_info);

WebPInfoStatus ParseRIFFHeader(const WebPInfo *const webp_info,
                               MemBuffer *const mem);

WebPInfoStatus ParseChunk(const WebPInfo *const webp_info,
                          MemBuffer *const mem,
                          ChunkData *const chunk_data);

WebPInfoStatus ProcessVP8XChunk(const ChunkData *const chunk_data,
                                WebPInfo *const webp_info);

WebPInfoStatus ProcessANIMChunk(const ChunkData *const chunk_data,
                                WebPInfo *const webp_info);

WebPInfoStatus ProcessANMFChunk(const ChunkData *const chunk_data,
                                WebPInfo *const webp_info);

WebPInfoStatus ProcessImageChunk(const ChunkData *const chunk_data,
                                 WebPInfo *const webp_info);

WebPInfoStatus ProcessALPHChunk(const ChunkData *const chunk_data,
                                WebPInfo *const webp_info);

WebPInfoStatus ProcessICCPChunk(const ChunkData *const chunk_data,
                                WebPInfo *const webp_info);

WebPInfoStatus ProcessChunk(const ChunkData *const chunk_data,
                            WebPInfo *const webp_info);

WebPInfoStatus Validate(const WebPInfo *const webp_info);

void ShowSummary(const WebPInfo *const webp_info);

WebPInfoStatus AnalyzeWebP(WebPInfo *const webp_info,
                           const WebPData *webp_data);

const char* GetWebPInfoDesc(WebPInfo *const webp_info, char* info);

#endif //GLIDE_PARENT_WEBPINFO_H
