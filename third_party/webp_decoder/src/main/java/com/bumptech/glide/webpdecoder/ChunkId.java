package com.bumptech.glide.webpdecoder;

/**
 * Webp chunk tag defines
 */
public enum ChunkId {

  VP8("VP8 "),
  VP8L("VP8L"),
  VP8X("VP8X"),
  ALPHA("ALPH"),
  ANIM("ANIM"),
  ANMF("ANMF"),
  ICCP("ICCP"),
  EXIF("EXIF"),
  XMP("XMP "),
  UNKNOWN("UNKN");

  String tag;

  ChunkId(String tag) {
    this.tag = tag;
  }

  static ChunkId getByTag(String tag) {
    for (ChunkId id : ChunkId.values()) {
      if (id.tag.equals(tag)) {
        return id;
      }
    }
    return UNKNOWN;
  }

  enum ANMFSubchunk {

    VP8(ChunkId.VP8),
    VP8L(ChunkId.VP8L),
    ALPHA(ChunkId.ALPHA);

    ChunkId id;

    ANMFSubchunk(ChunkId id) {
      this.id = id;
    }

    static ANMFSubchunk get(ChunkId id) {
      return ANMFSubchunk.valueOf(id.name());
    }

  }
}
