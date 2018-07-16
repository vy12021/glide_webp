package com.bumptech.glide.samples.heif;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.nokia.heif.example.HEIFExample;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * Displays an Heif image loaded from an android raw resource.
 */
public class MainActivity extends Activity {

  private String dir;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    findViewById(R.id.btn_reload).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        /*Intent intent = new Intent(getApplicationContext(), HeifActivity.class);
        startActivity(intent);*/
        reload();
      }
    });
    dir = getCacheDir().getAbsolutePath();
    copyFile();
  }

  private void reload() {
    System.loadLibrary("heifparser");
    HEIFExample.loadSingleImage(dir + File.separator + "autumn_1440x960.heic");
  }

  private ByteBuffer readFile(String webpFile) {
    RandomAccessFile raf = null;
    try {
      raf = new RandomAccessFile(webpFile, "r");
      ByteBuffer byteBuffer = ByteBuffer.allocateDirect((int) raf.length());
      int len;
      while (-1 != (len = raf.getChannel().read(byteBuffer)) && 0 != len) {}
      return byteBuffer;
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
    return null;
  }

  private void copyFile() {
    OutputStream os = null;
    InputStream is = null;
    try {
      os = new FileOutputStream(dir + "/autumn_1440x960.heic");
      is = getAssets().open("autumn_1440x960.heic");
      byte[] buffer = new byte[4096];
      int len;
      while ((len = is.read(buffer)) != -1) {
        os.write(buffer, 0, len);
      }
      os.flush();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (null != os) os.close();
        if (null != is) is.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

}
