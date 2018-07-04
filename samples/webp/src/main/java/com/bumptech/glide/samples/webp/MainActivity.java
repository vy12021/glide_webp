package com.bumptech.glide.samples.webp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.webpdecoder.Helper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * Displays an Webp image loaded from an android raw resource.
 */
public class MainActivity extends Activity {

    static {
        System.loadLibrary("webpparser");
    }

  private static final String TAG = "WebpActivity";
  private String dir;
  private ImageView imageView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    imageView = findViewById(R.id.image_view);
    Button button = findViewById(R.id.btn_reload);
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        reload();
      }
    });
    dir = getCacheDir().getAbsolutePath();
    copyFile();
    Helper.setStdoutFile("/storage/emulated/0/glide.log");
  }

  private void reload() {
    Log.w(TAG, "reloading");
    Glide.with(this).load(dir + "/test_3.webp").into(imageView);
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
      os = new FileOutputStream(dir + "/test_3.webp");
      is = getAssets().open("test_3.webp");
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
