package com.bumptech.glide.webpdecoder;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class HeaderParserTest {

    @Test
    public void parseHead() {
        File webpFile = new File("C:\\Users\\Leo\\Develop\\Android\\Project\\glide-webp-support\\samples\\webp\\src\\main\\assets\\test_3.webp");
        WebpHeaderParser parser = new WebpHeaderParser();
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(webpFile, "r");
            parser.setData(raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, raf.length()));
            parser.parseHeader();
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
