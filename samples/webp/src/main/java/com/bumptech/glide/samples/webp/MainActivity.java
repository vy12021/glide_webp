package com.bumptech.glide.samples.webp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

/**
 * Displays an Webp image loaded from an android raw resource.
 */
public class MainActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    findViewById(R.id.btn_list).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(getApplicationContext(), WebpActivity.class);
        startActivity(intent);
      }
    });
    findViewById(R.id.btn_reload).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        reload();
      }
    });
    reload();
  }

  private void reload() {
    Glide.with(MainActivity.this)
            .load("https://img-ws.doupai.cc/bhb/2020/10/13/09/3b77f0f5d5d28dc319359d18bbc6906c.webp")
            .skipMemoryCache(true)
            .into((ImageView) findViewById(R.id.image_view));
  }

}
