package com.bumptech.glide.webpdecoder;

import java.util.Stack;

/**
 * ChunkData in Webp header
 */
public class ChunkData {

  // start position in whole ByteBuffer
  final int start;
  // chunk size
  public int size;
  // chunk type
  public ChunkId id = ChunkId.UNKNOWN;
  // data offset without chunk header
  final int payloadOffset = 8;
  // raw buffer
  private ByteBufferReader reader;
  // position mark
  private Stack<Integer> marks = new Stack<>();

  ChunkData(ByteBufferReader reader) {
    this.reader = reader;
    start = reader.position();
  }

  public int dataStart() {
    return start + payloadOffset;
  }

  public int dataEnd() {
    return start + size;
  }

  public void skip2Start() {
    reader.skipTo(start);
  }

  public void skip2Data() {
    reader.skipTo(start + payloadOffset);
  }

  public void skip2End() {
    reader.skipTo(dataEnd());
  }

  int position() {
    return reader.position();
  }

  int remaining() {
    return reader.remaining();
  }

  void skip(int offset) {
    reader.skip(offset);
  }

  void get(byte[] buffer) {
    reader.getBytes(buffer);
  }

  byte get(int index) {
    return reader.getByteFrom(index);
  }

  byte getBy(int offset) {
    return get(position() + offset);
  }

  void getBy(int offset, byte[] buffer) {
    reader.getBytesFrom(position() + offset, buffer);
  }

  byte get() {
    return reader.getByte();
  }

  int save() {
    marks.push(position());
    return marks.size() - 1;
  }

  void restore2State(int state) {
    reader.skipTo(marks.remove(state));
  }

  void restore() {
    restore2State(marks.size() - 1);
  }

}
