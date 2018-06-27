package com.bumptech.glide.samples.webp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.bumptech.glide.webpdecoder.StandardWebpDecoder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Displays an Webp image loaded from an android raw resource.
 */
public class MainActivity extends Activity {
  private static final String TAG = "WebpActivity";
  private String dir;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    dir = getCacheDir().getAbsolutePath();
    new Thread(new Runnable() {
      @Override
      public void run() {
        copyFile();
        reload();
      }
    }).start();
  }

  @Override
  protected void onStart() {
    super.onStart();
  }

  private void reload() {
    Log.w(TAG, "reloading");
    StandardWebpDecoder.webpDemux(dir + "/test_3.webp");
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
