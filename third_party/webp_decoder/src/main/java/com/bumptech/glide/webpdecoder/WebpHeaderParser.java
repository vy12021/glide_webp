package com.bumptech.glide.webpdecoder;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_FORMAT_ERROR;

/**
 * A class responsible for creating {@link WebpHeader}s from data
 * representing animated WEBPs.
 */
public class WebpHeaderParser {
  private static final String TAG = "WebpHeaderParser";

  /** The minimum frame delay in hundredths of a second. */
  static final int MIN_FRAME_DELAY = 2;
  /**
   * The default frame delay in hundredths of a second.
   * This is used for WEBPs with frame delays less than the minimum.
   */
  static final int DEFAULT_FRAME_DELAY = 10;

  private static final int MAX_BLOCK_SIZE = 256;
  // Raw data read working array.
  private final byte[] block = new byte[MAX_BLOCK_SIZE];

  private ByteBuffer rawData;
  private WebpHeader header;
  private int blockSize = 0;

  public WebpHeaderParser setData(@NonNull ByteBuffer data) {
    reset();
    rawData = data.asReadOnlyBuffer();
    rawData.position(0);
    rawData.order(ByteOrder.LITTLE_ENDIAN);
    return this;
  }

  public WebpHeaderParser setData(@Nullable byte[] data) {
    if (data != null) {
      setData(ByteBuffer.wrap(data));
    } else {
      rawData = null;
      header.status = WebpDecoder.STATUS_OPEN_ERROR;
    }
    return this;
  }

  public void clear() {
    rawData = null;
    header = null;
  }

  private void reset() {
    rawData = null;
    Arrays.fill(block, (byte) 0);
    header = new WebpHeader();
    blockSize = 0;
  }

  @NonNull
  public WebpHeader parseHeader() {
    if (rawData == null) {
      throw new IllegalStateException("You must call setData() before parseHeader()");
    }
    if (err()) {
      return header;
    }

    readHeader();
    if (!err()) {
      readContents();
      if (header.frameCount < 0) {
        header.status = STATUS_FORMAT_ERROR;
      }
    }

    return header;
  }

  /**
   * Determines if the WEBP is animated by trying to read in the first 2 frames
   * This method re-parses the data even if the header has already been read.
   */
  public boolean isAnimated() {
    readHeader();
    if (!err()) {
      readContents(2 /* maxFrames */);
    }
    return header.frameCount > 1;
  }

  /**
   * Main file parser. Reads WEBP content blocks.
   */
  private void readContents() {
    readContents(Integer.MAX_VALUE /* maxFrames */);
  }

  /**
   * Main file parser. Reads WEBP content blocks. Stops after reading maxFrames
   */
  private void readContents(int maxFrames) {
    // Read WEBP file content blocks.
    boolean done = false;
    while (!(done || err() || header.frameCount > maxFrames)) {
      int code = read();
      switch (code) {
        case 0:
          // The Graphic Control Extension is optional, but will always come first if it exists.
          // If one did exist, there will be a non-null current frame which we should use.
          // However if one did not exist, the current frame will be null
          // and we must create it here. See issue #134.
          if (header.currentFrame == null) {
            header.currentFrame = new WebpFrame();
          }
          readBitmap();
          break;
        default:
          header.status = STATUS_FORMAT_ERROR;
      }
    }
  }

  /**
   * Reads next frame image.
   */
  private void readBitmap() {
    // (sub)image position & size.
    header.currentFrame.ix = readShort();
    header.currentFrame.iy = readShort();
    header.currentFrame.iw = readShort();
    header.currentFrame.ih = readShort();

    // Save this as the decoding position pointer.
    header.currentFrame.bufferFrameStart = rawData.position();

    // False decode pixel data to advance buffer.
    skipImageData();

    if (err()) {
      return;
    }

    header.frameCount++;
    // Add image to frame.
    header.frames.add(header.currentFrame);
  }

  /**
   * Reads WEBP file header information.
   */
  private void readHeader() {
    StringBuilder id = new StringBuilder();
    for (int i = 0; i < 6; i++) {
      id.append((char) read());
    }
    if (!id.toString().startsWith("WEBP")) {
      header.status = STATUS_FORMAT_ERROR;
    }
  }

  /**
   * Skips LZW image data for a single frame to advance buffer.
   */
  private void skipImageData() {
    // lzwMinCodeSize
    read();
    // data sub-blocks
    skip();
  }

  /**
   * Skips variable length blocks up to and including next zero length block.
   */
  private void skip() {
    int blockSize;
    do {
      blockSize = read();
      int newPosition = Math.min(rawData.position() + blockSize, rawData.limit());
      rawData.position(newPosition);
    } while (blockSize > 0);
  }

  /**
   * Reads next variable length block from input.
   */
  private void readBlock() {
    blockSize = read();
    int n = 0;
    if (blockSize > 0) {
      int count = 0;
      try {
        while (n < blockSize) {
          count = blockSize - n;
          rawData.get(block, n, count);
          n += count;
        }
      } catch (Exception e) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
          Log.d(TAG,
              "Error Reading Block n: " + n + " count: " + count + " blockSize: " + blockSize, e);
        }
        header.status = STATUS_FORMAT_ERROR;
      }
    }
  }

  /**
   * Reads a single byte from the input stream.
   */
  private int read() {
    int currByte = 0;
    try {
      currByte = rawData.get();
    } catch (Exception e) {
      header.status = STATUS_FORMAT_ERROR;
    }
    return currByte;
  }

  /**
   * Reads next 16-bit value, LSB first.
   */
  private int readShort() {
    // Read 16-bit value.
    return rawData.getShort();
  }

  private boolean err() {
    return header.status != WebpDecoder.STATUS_OK;
  }

}
