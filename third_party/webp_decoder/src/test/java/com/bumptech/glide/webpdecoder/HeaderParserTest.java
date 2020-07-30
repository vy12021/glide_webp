package com.bumptech.glide.webpdecoder;


import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public class HeaderParserTest {

  File webpFile = new File("D:\\Develop\\Android\\project\\glide\\samples\\webp\\src\\main\\assets\\vip.webp");

  @Test
  public void parseHead() {
    try (RandomAccessFile raf = new RandomAccessFile(webpFile, "r")) {
      WebpParser parser = new WebpParser(raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, raf.length()));
      parser.parse();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void bittest() {
    byte a = (byte) 0x80;
    byte b = (byte) 0xff;
    int i = ((b & 0xff) << 16) | (a & 0xff);
    System.out.println("i: " + i);
  }

}
