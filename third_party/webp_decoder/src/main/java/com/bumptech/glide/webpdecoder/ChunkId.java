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

  static final int CHUNK_ID_TYPES = 10;

  String tag;

  ChunkId(String tag) {
    this.tag = tag;
  }

  public static ChunkId getByName(String name) {
    ChunkId ret = UNKNOWN;
    for (ChunkId tag : ChunkId.values()) {
      if (tag.tag.equals(name)) {
        ret = tag;
        break;
      }
    }
    return ret;
  }

}
