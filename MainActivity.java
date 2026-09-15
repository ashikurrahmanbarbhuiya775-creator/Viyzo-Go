
package com.viyzo.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
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

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(20, 20, 20, 20);
        layout.setBackgroundColor(Color.rgb(20, 20, 24));

        TextView title = new TextView(this);
        title.setText("VIYZO GO");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        layout.addView(title,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

        videoView = new VideoView(this);

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        videoParams.setMargins(0, 20, 0, 20);
        layout.addView(videoView, videoParams);

        Button selectButton = new Button(this);
        selectButton.setText("SELECT VIDEO");

        selectButton.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            startActivityForResult(intent, 100);
        });

        layout.addView(selectButton);

        setContentView(layout);
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

                try {
                    getContentResolver().takePersistableUriPermission(
                            videoUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception ignored) {
                }

                MediaController controller =
                        new MediaController(this);

                controller.setAnchorView(videoView);

                videoView.setMediaController(controller);
                videoView.setVideoURI(videoUri);

                videoView.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    videoView.start();
                });

                videoView.requestFocus();
            }
        }
    }
}
