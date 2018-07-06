package com.bumptech.glide.samples.webp;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.io.File;
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

  private static final String TAG = "WebpActivity";

  private static final String assetFile = "test_1.webp";

  private String dir;
  private RecyclerView recyclerView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    recyclerView = findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
    recyclerView.setAdapter(new Adapter());
    Button button = findViewById(R.id.btn_reload);
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        reload();
      }
    });
    dir = getCacheDir().getAbsolutePath();
    copyFile();
  }

  private void reload() {
    Log.w(TAG, "reloading");
  }

  private class Adapter extends RecyclerView.Adapter<Holder> {
    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      return new Holder(new ImageView(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
      Glide.with(MainActivity.this).load(dir + File.separator + assetFile).into((ImageView) holder.itemView);
    }

    @Override
    public int getItemCount() {
      return 1;
    }
  }

  class Holder extends RecyclerView.ViewHolder {

    Holder(View itemView) {
      super(itemView);
    }
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
      os = new FileOutputStream(dir + File.separator + assetFile);
      is = getAssets().open(assetFile);
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
