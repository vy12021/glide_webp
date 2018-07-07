package com.bumptech.glide.webpdecoder;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ByteBufferReader
 */
final class ByteBufferReader {

  private byte[] block;
  private final ByteBuffer rawData;

  public ByteBufferReader(ByteBuffer rawData) {
    this.rawData = rawData;
  }

  public ByteBufferReader(byte[] bytes) {
    this(bytes, 0, bytes.length);
  }

  public ByteBufferReader(ByteOrder order, byte[] bytes) {
    this(order, bytes, 0, bytes.length);
  }

  public ByteBufferReader(byte[] bytes, int offset, int len) {
    this(ByteOrder.BIG_ENDIAN, bytes, offset, len);
  }

  public ByteBufferReader(ByteOrder order, byte[] bytes, int offset, int len) {
    this.rawData = ByteBuffer.wrap(bytes, offset, len);
    this.rawData.order(order);
  }

  private void ensureBlock(int size) {
    if (null == block || size > block.length) {
      block = new byte[size];
    }
  }

  public int skip(int offset) {
    this.rawData.position(this.rawData.position() + offset);
    return this.rawData.position();
  }

  public int skipTo(int position) {
    this.rawData.position(position);
    return this.rawData.position();
  }

  public byte[] getBytes(int len) {
    return getBytesFrom(this.rawData.position(), len);
  }

  public byte[] getBytesFrom(int index, int len) {
    this.rawData.mark();
    byte[] bytes = readBytesFrom(index, len);
    this.rawData.reset();
    return bytes;
  }

  public byte[] readBytes(int len) {
    return readBytesFrom(this.rawData.position(), len);
  }

  public byte[] readBytesFrom(int index, int len) {
    this.rawData.position(index);
    byte[] bytes = new byte[len];
    this.rawData.get(bytes);
    return bytes;
  }

  public byte getByte() {
    rawData.mark();
    byte ret = readByte();
    rawData.reset();
    return ret;
  }

  public byte readByte() {
    return rawData.get();
  }

  public byte getByteFrom(int index) {
    return rawData.get(index);
  }

  public byte readByteFrom(int index) {
    rawData.position(index);
    return rawData.get();
  }

  public int getShort() {
    rawData.mark();
    int ret = readShort();
    rawData.reset();
    return ret;
  }

  public int readShort() {
    return rawData.getShort();
  }

  public int getInt() {
    return getInt(4);
  }

  public int getInt(int len) {
    rawData.mark();
    int ret = readIntFrom(rawData.position(), len);
    rawData.reset();
    return ret;
  }

  public int getIntFrom(int index) {
    return rawData.getInt(index);
  }

  public int getIntFrom(int index, int len) {
    rawData.mark();
    int ret = readIntFrom(index, len);
    rawData.reset();
    return ret;
  }

  public int readInt() {
    return readInt(4);
  }

  public int readInt(int len) {
    return readIntFrom(rawData.position(), len);
  }

  public int readIntFrom(int index) {
    rawData.position(index);
    return rawData.getInt();
  }

  public int readIntFrom(int index, int len) {
    rawData.position(index);
    rawData.get(block, 0, len);
    return getIntWithLen(block, len);
  }

  public long getLong() {
    rawData.mark();
    long ret = rawData.getLong();
    rawData.reset();
    return ret;
  }

  public long getLongFrom(int position) {
    rawData.mark();
    long ret = readLongFrom(position);
    rawData.reset();
    return ret;
  }

  public long readLong() {
    return rawData.getLong();
  }

  public long readLongFrom(int position) {
    rawData.position(position);
    return rawData.getLong();
  }

  public BigInteger unsignedLong(long value) {
    if (value >= 0) {
      return BigInteger.valueOf(value);
    }
    long lowValue = value & 0x7fffffffffffffffL;
    return BigInteger.valueOf(lowValue)
            .add(BigInteger.valueOf(Long.MAX_VALUE))
            .add(BigInteger.ONE);
  }

  public BigInteger readUnsignedLong() {
    long value = readLong();
    return unsignedLong(value);
  }

  public BigInteger readUnsignedLongFrom(int position) {
    long value = readLongFrom(position);
    return unsignedLong(value);
  }

  public BigInteger getUnsignedLong() {
    this.rawData.mark();
    BigInteger ret = readUnsignedLong();
    this.rawData.reset();
    return ret;
  }

  public BigInteger getUnsignedLongFrom(int position) {
    long value = readLongFrom(position);
    return unsignedLong(value);
  }

  public int getIntWithLen(byte[] bytes, int len) {
    int ret = 0;
    int i = len;
    if (ByteOrder.LITTLE_ENDIAN == this.rawData.order()) {
      for (--i; i >= 0; --i) {
        ret |= ((bytes[i] & 0xff) << (i * 8));
      }
    } else {
      for (--i, --len; i >= 0; --i) {
        ret |= ((bytes[len - i] & 0xff) << ((len - i) * 8));
      }
    }
    return ret;
  }

  public long getLongWithLen(byte[] bytes, int len) {
    long ret = 0;
    int i = len;
    if (ByteOrder.LITTLE_ENDIAN == this.rawData.order()) {
      for (--i; i >= 0; --i) {
        ret |= ((bytes[i] & 0xff) << (i * 8));
      }
    } else {
      for (--i, --len; i >= 0; --i) {
        ret |= ((bytes[len - i] & 0xff) << ((len - i) * 8));
      }
    }
    return ret;
  }

  public boolean getEquals(String tag) {
    return getEquals(rawData.position(), tag);
  }

  public boolean getEquals(int index, String tag) {
    rawData.mark();
    boolean ret = readEquals(index, tag);
    rawData.reset();
    return ret;
  }

  public String getString(int len) {
    rawData.mark();
    String ret = readString(len);
    rawData.reset();
    return ret;
  }

  public String readString(int len) {
    ensureBlock(len);
    rawData.get(block, 0, len);
    return new String(block, 0, len);
  }

  public boolean readEquals(String tag) {
    return readEquals(rawData.position(), tag);
  }

  public boolean readEquals(int index, String tag) {
    char[] chars = tag.toCharArray();
    ensureBlock(chars.length);
    rawData.position(index);
    rawData.get(block, 0, chars.length);
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] != block[i]) return false;
    }
    return true;
  }

}
