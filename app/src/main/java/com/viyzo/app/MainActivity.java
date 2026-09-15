package com.viyzo.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.MediaController;

public class MainActivity extends Activity {

    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setGravity(Gravity.CENTER);
        mainLayout.setPadding(20, 20, 20, 20);
        mainLayout.setBackgroundColor(Color.rgb(20, 20, 24));

        TextView title = new TextView(this);
        title.setText("VIYZO GO");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        mainLayout.addView(title);

        videoView = new VideoView(this);

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        mainLayout.addView(videoView, videoParams);

        Button selectVideo = new Button(this);
        selectVideo.setText("SELECT VIDEO");

        selectVideo.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            startActivityForResult(intent, 100);
        });

        mainLayout.addView(selectVideo);

        Button profile = new Button(this);
        profile.setText("PROFILE");

        profile.setOnClickListener(v ->
                title.setText("VIYZO GO\nPROFILE")
        );

        mainLayout.addView(profile);

        Button settings = new Button(this);
        settings.setText("SETTINGS");

        settings.setOnClickListener(v ->
                title.setText("VIYZO GO\nSETTINGS")
        );

        mainLayout.addView(settings);

        setContentView(mainLayout);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 &&
                resultCode == RESULT_OK &&
                data != null) {

            Uri videoUri = data.getData();

            if (videoUri != null) {

                MediaController controller =
                        new MediaController(this);

                controller.setAnchorView(videoView);

                videoView.setMediaController(controller);
                videoView.setVideoURI(videoUri);
                videoView.requestFocus();
                videoView.start();
            }
        }
    }
}
