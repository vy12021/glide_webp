package com.bumptech.glide.webpdecoder;

import android.graphics.Bitmap;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class WebpFetcher {

  private long nativePointer;
  private final WebpHeaderParser parser = new WebpHeaderParser();
  private WebpHeader header;
  private final String webpFile;

  public WebpFetcher(String webpFile) {
    this.webpFile = webpFile;
  }

  /**
   * first step, parse webp header
   * @return  header info
   * @throws Exception  e
   */
  public WebpHeader parse() throws Exception {
    RandomAccessFile raf = new RandomAccessFile(webpFile, "r");
    ByteBuffer data = ByteBuffer.allocateDirect((int) raf.length());
    byte[] buffer = new byte[4096];
    int len;
    while (-1 != (len = raf.read(buffer))) {
      data.put(buffer, 0, len);
    }
    parser.setData(data);
    header = parser.parseHeader();
    if (WebpDecoder.STATUS_OK == header.status) {
      nativePointer = StandardWebpDecoder.nativeInitWebpParser(parser.getRawData());
    }
    return header;
  }

  /**
   * extract and decode one of frames
   * @param dst fixed size bitmap
   * @param frameIndex index which been read.
   * @return  bitmap of the frame
   */
  public Bitmap getFrame(@Nullable Bitmap dst, @IntRange(from = 1) int frameIndex) {
    if (null == header || WebpDecoder.STATUS_OK != header.status) {
      throw new IllegalStateException("Invalid header info");
    }
    if (null == dst) {
      dst = Bitmap.createBitmap(header.getWidth(), header.getHeight(), Bitmap.Config.ARGB_8888);
    }
    if (nativePointer != 0) {
      StandardWebpDecoder.nativeGetWebpFrame(nativePointer, dst, frameIndex);
    }
    return dst;
  }

  /**
   * release alloc memory
   */
  public void release() {
    if (0 != nativePointer) {
      StandardWebpDecoder.nativeReleaseParser(nativePointer);
    }
    parser.clear();
  }

}
