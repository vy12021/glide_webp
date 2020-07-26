package com.bumptech.glide.webpdecoder;

import android.graphics.Bitmap;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class WebpFetcher {

  private final String webpFile;
  private long nativePointer;
  private WebpParser parser;
  private WebpHeader header;

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
    parser = new WebpParser(data);
    header = parser.parse();
    if (WebpDecoder.STATUS_OK == header.status) {
      nativePointer = StandardWebpDecoder.nativeInitWebpParser(data);
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
