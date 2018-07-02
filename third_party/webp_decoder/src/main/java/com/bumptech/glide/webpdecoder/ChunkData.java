package com.bumptech.glide.webpdecoder;

import java.nio.ByteBuffer;

/**
 * ChunkData in Webp header
 */
public class ChunkData {

    // start position in whole ByteBuffer
    public int start;
    // chunk size
    public int size;
    // chunk type
    public ChunkId id = ChunkId.UNKNOWN;
    // data offset without chunk header
    public int payloadOffset;
    // raw buffer
    public ByteBuffer rawBuffer;

    public void reset() {
        this.rawBuffer.position(start);
    }

}
