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

    Glide.with(this)
            .load("https://img.zishuovideo.com/e54006b57ed32ccccb48a150f7c45a93.webp")
            .into((ImageView) findViewById(R.id.image_view));

    findViewById(R.id.btn_reload).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(getApplicationContext(), WebpActivity.class);
        startActivity(intent);
      }
    });
  }

}
