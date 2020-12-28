package com.bumptech.glide.samples.webp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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
            //.load("https://img-ws.doupai.cc/bhb/2020/12/14/14/a8f0ea530a15da2c1e0beb457b372d37.png?x-oss-process=image/format,webp")
            .load(Uri.parse("file:///android_asset/vip.webp"))
            // .load(Uri.parse("file:///android_asset/anim_coin.webp"))
            //.load("https://im4.ezgif.com/tmp/ezgif-4-a98e5c3d368e.webp")
            .skipMemoryCache(true)
            .into((ImageView) findViewById(R.id.image_view));
  }

}
