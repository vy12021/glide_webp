package com.bumptech.glide.samples.webp;

import android.annotation.SuppressLint;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Displays an Webp image loaded from an android raw resource.
 */
@SuppressLint("Registered")
public class WebpActivity extends Activity {

  private static final String TAG = "WebpActivity";

  List<String> webpUris = new ArrayList<>();
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
      ImageView imageView = new SquareImageView(parent.getContext());
      imageView.setPadding(0, 50, 0, 50);
      return new Holder(imageView);
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

  @Override
  protected void onDestroy() {
    super.onDestroy();
    Glide.tearDown();
  }
}
