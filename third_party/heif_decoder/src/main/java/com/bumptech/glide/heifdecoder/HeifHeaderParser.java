package com.bumptech.glide.heifdecoder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A class responsible for creating {@link HeifHeader}s from data
 * representing animated HEIFs.
 * ------------------------------------------------------------------------------
 * HEIF layout is:
 */
public class HeifHeaderParser {
  private static final String TAG = "HeifHeaderParser";

  /**
   * The minimum frame delay in ms.
   */
  static final int MIN_FRAME_DELAY = 20;
  /**
   * The default frame delay in ms.
   * This is used for WEBPs with frame delays less than the minimum.
   */
  static final int DEFAULT_FRAME_DELAY = 100;

  private ByteBufferReader bufferReader;
  private ByteBuffer rawData;
  private HeifHeader header;

  public HeifHeaderParser setData(@NonNull ByteBuffer data) {
    reset();
    if (!data.isDirect()) {
      throw new IllegalArgumentException("ByteBuffer must be direct allocated");
    }
    rawData = data.asReadOnlyBuffer();
    rawData.position(0);
    rawData.order(ByteOrder.LITTLE_ENDIAN);
    bufferReader = new ByteBufferReader(rawData);
    return this;
  }

  public HeifHeaderParser setData(@Nullable byte[] data) {
    if (data != null) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
      buffer.put(data);
      setData(buffer);
    } else {
      rawData = null;
      header.status = HeifDecoder.STATUS_OPEN_ERROR;
    }
    return this;
  }

  ByteBuffer getRawData() {
    return rawData;
  }

  public void clear() {
    rawData.clear();
    rawData = null;
    header = null;
  }

  private void reset() {
    rawData = null;
    header = new HeifHeader();
  }

  @NonNull
  public HeifHeader parseHeader() {
    if (rawData == null) {
      throw new IllegalStateException("You must call setData() before parseHeader()");
    }
    if (err()) {
      return header;
    }
    readHeader();
    return header;
  }

  /**
   * Determines if the WEBP is animated by trying to read in the first 2 frames
   * This method re-parses the data even if the header has already been read.
   */
  public boolean isAnimated() {
    return header.frameCount > 1;
  }

  /**
   * Reads WEBP file header information.
   */
  private void readHeader() {
    logd("webp header info: " + this.header.toString());
  }

  private void validate() {
  }

  private void loge(String msg) {
    if (Log.isLoggable(TAG, Log.ERROR)) {
      Log.e(TAG, msg);
    }
  }

  private void logd(String msg) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, msg);
    }
  }

  private void logw(String msg) {
    if (Log.isLoggable(TAG, Log.WARN)) {
      Log.w(TAG, msg);
    }
  }
  private boolean err() {
    return header.status != HeifDecoder.STATUS_OK;
  }

}
