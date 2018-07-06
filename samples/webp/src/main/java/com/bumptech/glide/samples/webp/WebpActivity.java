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
import java.util.ArrayList;
import java.util.List;

/**
 * Displays an Webp image loaded from an android raw resource.
 */
public class WebpActivity extends Activity {

  private static final String TAG = "WebpActivity";

  private static final String assetFile = "test_2.webp";

  List<String> webpUris = new ArrayList<>();
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
    for (int i = 0; i <= 17; i++) {
      webpUris.add("http://www.teslaliu.com/webp (" + i + ").webp");
    }
  }

  private void reload() {
    Log.w(TAG, "reloading");
  }

  private class Adapter extends RecyclerView.Adapter<Holder> {
    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      return new Holder(new SquareImageView(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
      String uri = webpUris.get(((int) (Math.random() * webpUris.size())));
      Glide.with(WebpActivity.this).load(uri).into((ImageView) holder.itemView);
    }

    @Override
    public int getItemCount() {
      return 10000;
    }
  }

  class Holder extends RecyclerView.ViewHolder {

    Holder(View itemView) {
      super(itemView);
    }
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

  @Override
  protected void onDestroy() {
    super.onDestroy();
    Glide.tearDown();
  }
}
