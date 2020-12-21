package com.bumptech.glide.webpdecoder;

import androidx.annotation.ColorInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bumptech.glide.webpdecoder.WebpParser.ALL_VALID_FLAGS;

/**
 * A header object containing the number of frames in an animated WEBP image as well as basic
 * metadata like width and height that can be used to decode each individual frame of the WEBP. Can
 * be shared by one or more {@link WebpDecoder}s to play the same
 * animated WEBP in multiple views.
 */
public class WebpHeader {

  /**
   * The "Netscape" loop count which means loop forever.
   */
  public static final int NETSCAPE_LOOP_COUNT_FOREVER         = 0;
  /**
   * Indicates that this header has no "Netscape" loop count.
   */
  public static final int NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST  = -1;

  // current frame
  WebpFrame current;
  // frames container
  private List<WebpFrame> frames = new ArrayList<>();

  // decode status
  @WebpDecoder.WebpDecodeStatus
  int status = WebpDecoder.STATUS_OK;
  // riff chunk size
  long riffSize;
  // ALPH Chunk.
  boolean hasAlpha;
  // ANMF Chunk.
  boolean hasAnimation;
  // EXIF Chunk.
  boolean hasExif;
  // ICCP Chunk.
  boolean hasIccp;
  // XMP Chunk.
  boolean hasXmp;

  // global background color
  @ColorInt
  int bgColor;
  // frame count
  int frameCount;
  // loop repeat times
  int loopCount = NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST;
  // canvas size
  int canvasWidth, canvasHeight;
  // flags for vp8x chunk
  @WebpParser.WebpFeatureFlag
  int featureFlags = ALL_VALID_FLAGS;

  // chunk flag for mark tag parsed
  private boolean[] chunksMark = new boolean[ChunkId.values().length];
  // anim chunk process flag
  boolean isProcessingAnimFrame;
  boolean foundAlphaSubchunk;
  boolean foundImageSubchunk;
  // anmf subchunk flag for mark tag parsed
  private boolean[] anmfSubchunksMark = new boolean[ChunkId.ANMFSubchunk.values().length];  // 0 VP8; 1 VP8L; 2 ALPH.

  void markANMFSubchunk(ChunkId id, boolean flag) {
    anmfSubchunksMark[ChunkId.ANMFSubchunk.get(id).ordinal()] = flag;
  }

  boolean getANMFSubchunkMark(ChunkId id) {
    return anmfSubchunksMark[ChunkId.ANMFSubchunk.get(id).ordinal()];
  }

  void markChunk(ChunkId id, boolean flag) {
    chunksMark[id.ordinal()] = flag;
  }

  boolean getChunkMark(ChunkId id) {
    return chunksMark[id.ordinal()];
  }

  public int getHeight() {
    return canvasHeight;
  }

  public int getWidth() {
    return canvasWidth;
  }

  public int getFrameCount() {
    return frameCount;
  }

  public WebpFrame getFrame(int index) {
    return frames.get(index);
  }

  WebpFrame newFrame() {
    frames.add(current = new WebpFrame(frameCount));
    return current;
  }

  /**
   * Global status code of WEBP data parsing.
   */
  @WebpDecoder.WebpDecodeStatus
  public int getStatus() {
    return status;
  }

  public boolean isAvailable() {
    return hasAnimation
            && frameCount > 0 && frameCount == frames.size()
            && status == WebpDecoder.STATUS_OK;
  }

  String printVp8XInfo() {
    return "Vp8XInfo{" +
            "\n riffSize=" + riffSize +
            ",\n featureFlags=" + featureFlags +
            ",\n hasAlpha=" + hasAlpha +
            ",\n hasAnimation=" + hasAnimation +
            ",\n hasExif=" + hasExif +
            ",\n hasIccp=" + hasIccp +
            ",\n hasXmp=" + hasXmp +
            ",\n canvasWidth=" + canvasWidth +
            ",\n canvasHeight=" + canvasHeight +
            '}';
  }

  @Override
  public String toString() {
    return "WebpHeader{" +
            "\n status=" + status +
            ",\n riffSize=" + riffSize +
            ",\n featureFlags=" + featureFlags +
            ",\n hasAlpha=" + hasAlpha +
            ",\n hasAnimation=" + hasAnimation +
            ",\n hasExif=" + hasExif +
            ",\n hasIccp=" + hasIccp +
            ",\n hasXmp=" + hasXmp +
            ",\n canvasWidth=" + canvasWidth +
            ",\n canvasHeight=" + canvasHeight +
            ",\n frameCount=" + frameCount +
            ",\n loopCount=" + loopCount +
            ",\n frames=" + frames +
            ",\n bgColor=" + bgColor +
            ",\n chunksMark=" + Arrays.toString(chunksMark) +
            '}';
  }
}
