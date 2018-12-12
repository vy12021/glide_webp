package com.bumptech.glide.samples.webp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

/**
 * Displays an Webp image loaded from an android raw resource.
 */
public class MainActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Glide.with(MainActivity.this)
            .load("https://img.zishuovideo.com/e54006b57ed32ccccb48a150f7c45a93.webp")
            .apply(new RequestOptions()
                    .transforms(new BitmapTransformation[] {new CenterCrop(), new RoundedCorners(100)}
                    ))
            .into((ImageView) findViewById(R.id.image_view));

    findViewById(R.id.btn_list).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(getApplicationContext(), WebpActivity.class);
        startActivity(intent);
      }
    });
  }

}
