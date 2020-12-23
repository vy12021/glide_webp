package com.bumptech.glide.webpdecoder;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ByteBufferReader
 */
final class ByteBufferReader {

  private byte[] buffer;
  private final ByteBuffer rawData;

  public ByteBufferReader(ByteBuffer buffer, ByteOrder order) {
    this.rawData = buffer.isReadOnly() ? buffer : buffer.asReadOnlyBuffer();
    this.rawData.position(0);
    this.rawData.order(order);
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
    rawData = ByteBuffer.wrap(bytes, offset, len);
    rawData.order(order);
  }

  public int skip(int offset) {
    return skipTo(position() + offset);
  }

  public int skipTo(int position) {
    rawData.position(position);
    return position;
  }

  public int position() {
    return rawData.position();
  }

  public int remaining() {
    return rawData.remaining();
  }

  public int size() {
    return rawData.limit();
  }

  public void clear() {
    rawData.clear();
  }

  public ByteBuffer buffer() {
    return rawData.duplicate();
  }

  public void getBytes(byte[] buffer) {
    rawData.mark();
    readBytes(buffer);
    rawData.reset();
  }

  public void getBytesFrom(int index, byte[] buffer) {
    rawData.mark();
    rawData.position(index);
    rawData.get(buffer);
    rawData.reset();
  }

  public byte[] getBytes(int len) {
    return getBytesFrom(rawData.position(), len);
  }

  public byte[] getBytesFrom(int index, int len) {
    rawData.mark();
    byte[] bytes = readBytesFrom(index, len);
    rawData.reset();
    return bytes;
  }

  public void readBytes(byte[] buffer) {
    rawData.get(buffer);
  }

  public void readBytesFrom(int index, byte[] buffer) {
    rawData.position(index);
    rawData.get(buffer);
  }

  public byte[] readBytes(int len) {
    return readBytesFrom(rawData.position(), len);
  }

  public byte[] readBytesFrom(int index, int len) {
    rawData.position(index);
    byte[] bytes = new byte[len];
    rawData.get(bytes);
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
  
  public long getUnsignedInt() {
    return getUnsignedInt(4);
  }

  public int getInt(int len) {
    rawData.mark();
    int ret = readIntFrom(rawData.position(), len);
    rawData.reset();
    return ret;
  }

  public long getUnsignedInt(int len) {
    rawData.mark();
    long ret = readUnsignedIntFrom(rawData.position(), len);
    rawData.reset();
    return ret;
  }

  public int getIntFrom(int index) {
    return rawData.getInt(index);
  }

  public long getUnsignedIntFrom(int index) {
    rawData.mark();
    long ret = readUnsignedIntFrom(index);
    rawData.reset();
    return ret;
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

  public long readUnsignedInt() {
    return readUnsignedIntFrom(rawData.position(), 4);
  }

  public int readInt(int len) {
    return readIntFrom(rawData.position(), len);
  }

  public int readIntFrom(int index) {
    rawData.position(index);
    return rawData.getInt();
  }

  public long readUnsignedIntFrom(int index) {
    return readUnsignedIntFrom(index, 4);
  }

  public int readIntFrom(int index, int len) {
    rawData.position(index);
    ensureBlock(len);
    rawData.get(buffer, 0, len);
    return getIntWithLen(buffer, len);
  }

  public long readUnsignedIntFrom(int index, int len) {
    rawData.position(index);
    ensureBlock(len);
    rawData.get(buffer, 0, len);
    return getLongWithLen(buffer, len);
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
    rawData.mark();
    BigInteger ret = readUnsignedLong();
    rawData.reset();
    return ret;
  }

  public BigInteger getUnsignedLongFrom(int position) {
    long value = readLongFrom(position);
    return unsignedLong(value);
  }

  public int getIntWithLen(byte[] bytes, int len) {
    int ret = 0;
    int i = len;
    if (ByteOrder.LITTLE_ENDIAN == rawData.order()) {
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
    if (ByteOrder.LITTLE_ENDIAN == rawData.order()) {
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
    rawData.get(buffer, 0, len);
    return new String(buffer, 0, len);
  }

  public boolean readEquals(String tag) {
    return readEquals(rawData.position(), tag);
  }

  public boolean readEquals(int index, String tag) {
    char[] chars = tag.toCharArray();
    ensureBlock(chars.length);
    rawData.position(index);
    rawData.get(buffer, 0, chars.length);
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] != buffer[i]) {
        return false;
      }
    }
    return true;
  }

  private void ensureBlock(int size) {
    if (null == buffer || size > buffer.length) {
      buffer = new byte[size];
    }
  }

}
