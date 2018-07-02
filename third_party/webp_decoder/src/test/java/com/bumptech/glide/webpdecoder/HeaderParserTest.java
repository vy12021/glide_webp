package com.bumptech.glide.webpdecoder;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class HeaderParserTest {

    @Test
    public void parseHead() {
        File webpFile = new File("C:\\Users\\Leo\\Develop\\Android\\Project\\glide-webp-support\\samples\\webp\\src\\main\\assets\\test_3.webp");
        WebpHeaderParser parser = new WebpHeaderParser();
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(webpFile, "r");
            byte[] buffer = new byte[4096];
            int readLength;
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) raf.length());
            while (-1 != (readLength = raf.read(buffer))) {
                byteBuffer.put(buffer, 0, readLength);
            }
            parser.setData(byteBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != raf) {
                    raf.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
