package com.viyzo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.MediaController;

public class MainActivity extends Activity {

    private VideoView videoView;
    private TextView likeText;

    private int likeCount = 0;
    private boolean liked = false;

    private int dp(int value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }

    private TextView makeText(String text, int size) {

        TextView textView = new TextView(this);

        textView.setText(text);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(size);
        textView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        textView.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
        );

        return textView;
    }

    private Button makeButton(String text) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);

        return button;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        showHome();
    }

    private LinearLayout createPage() {

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(20)
        );

        content.setBackgroundColor(
                Color.rgb(18, 18, 22)
        );

        ScrollView scrollView =
                new ScrollView(this);

        scrollView.addView(content);

        setContentView(scrollView);

        return content;
    }

    private void showHome() {

        LinearLayout layout =
                createPage();

        TextView logo =
                makeText("VIYZO GO", 30);

        logo.setGravity(Gravity.CENTER);

        layout.addView(logo);

        TextView subtitle =
                new TextView(this);

        subtitle.setText(
                "Social Video • Friends • Messages • Creator"
        );

        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);

        layout.addView(subtitle);

        videoView =
                new VideoView(this);

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(300)
                );

        videoParams.setMargins(
                0,
                dp(15),
                0,
                dp(15)
        );

        layout.addView(
                videoView,
                videoParams
        );

        Button selectVideo =
                makeButton(
                        "SELECT / UPLOAD VIDEO"
                );

        selectVideo.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            Intent.ACTION_OPEN_DOCUMENT
                    );

            intent.setType("video/*");

            intent.addCategory(
                    Intent.CATEGORY_OPENABLE
            );

            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            intent.addFlags(
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            );

            startActivityForResult(
                    intent,
                    100
            );
        });

        layout.addView(selectVideo);

        likeText =
                makeText(
                        "LIKE 0",
                        18
                );

        likeText.setOnClickListener(v -> {

            if (!liked) {

                liked = true;
                likeCount++;

                likeText.setText(
                        "LIKED  " + likeCount
                );

            } else {

                liked = false;

                if (likeCount > 0) {
                    likeCount--;
                }

                likeText.setText(
                        "LIKE  " + likeCount
                );
            }
        });

        layout.addView(likeText);

        Button comment =
                makeButton("COMMENTS");

        comment.setOnClickListener(
                v -> showComments()
        );

        layout.addView(comment);

        Button share =
                makeButton("SHARE VIDEO");

        share.setOnClickListener(
                v -> shareVideo()
        );

        layout.addView(share);

        Button messages =
                makeButton("MESSAGES");

        messages.setOnClickListener(
                v -> showMessages()
        );

        layout.addView(messages);

        Button notifications =
                makeButton("NOTIFICATIONS");

        notifications.setOnClickListener(
                v -> showNotifications()
        );

        layout.addView(notifications);

        Button profile =
                makeButton("MY PROFILE");

        profile.setOnClickListener(
                v -> showProfile()
        );

        layout.addView(profile);

        Button dashboard =
                makeButton("MY DASHBOARD");

        dashboard.setOnClickListener(
                v -> showMyDashboard()
        );

        layout.addView(dashboard);

        Button friends =
                makeButton("FRIENDS / FOLLOWERS");

        friends.setOnClickListener(
                v -> showFriends()
        );

        layout.addView(friends);

        Button search =
                makeButton("SEARCH USERS");

        search.setOnClickListener(
                v -> showSearch()
        );

        layout.addView(search);

        Button report =
                makeButton("REPORT / BLOCK");

        report.setOnClickListener(
                v -> showReport()
        );

        layout.addView(report);

        Button settings =
                makeButton(
                        "SETTINGS & PRIVACY"
                );

        settings.setOnClickListener(
                v -> showSettings()
        );

        layout.addView(settings);

        Button admin =
                makeButton(
                        "ADMIN DASHBOARD"
                );

        admin.setOnClickListener(
                v -> showAdminDashboard()
        );

        layout.addView(admin);

        Button logout =
                makeButton("LOGOUT");

        logout.setOnClickListener(v -> {

            Toast.makeText(
                    this,
                    "Logout test",
                    Toast.LENGTH_SHORT
            ).show();
        });

        layout.addView(logout);
    }

    private void showComments() {

        EditText input =
                new EditText(this);

        input.setHint(
                "Write a comment"
        );

        new AlertDialog.Builder(this)
                .setTitle("COMMENTS")
                .setMessage(
                        "Comments\n\n" +
                        "Like comment\n" +
                        "Reply\n" +
                        "Delete comment\n" +
                        "Report comment"
                )
                .setView(input)
                .setPositiveButton(
                        "POST",
                        (dialog, which) -> {

                            Toast.makeText(
                                    this,
                                    "Comment posted - TEST",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .setNegativeButton(
                        "CANCEL",
                        null
                )
                .show();
    }

    private void shareVideo() {

        Intent shareIntent =
                new Intent(
                        Intent.ACTION_SEND
                );

        shareIntent.setType(
                "text/plain"
        );

        shareIntent.putExtra(
                Intent.EXTRA_TEXT,
                "Watch this video on Viyzo Go"
        );

        startActivity(
                Intent.createChooser(
                        shareIntent,
                        "Share Viyzo Video"
                )
        );
    }

    private void showMessages() {

        LinearLayout layout =
                createPage();

        layout.addView(
                makeText(
                        "MESSAGES",
                        28
                )
        );

        Button newMessage =
                makeButton(
                        "NEW MESSAGE"
                );

        layout.addView(newMessage);

        Button user1 =
                makeButton("USER 1");

        layout.addView(user1);

        Button user2 =
                makeButton("USER 2");

        layout.addView(user2);

        Button requests =
                makeButton(
                        "MESSAGE REQUESTS"
                );

        layout.addView(requests);

        addBackButton(layout);
    }

    private void showNotifications() {

        LinearLayout layout =
                createPage();

        layout.addView(
                makeText(
                        "NOTIFICATIONS",
                        28
                )
        );

        layout.addView(
                makeText(
                        "Someone liked your video",
                        17
                )
        );

        layout.addView(
                makeText(
                        "Someone followed you",
                        17
                )
        );

        layout.addView(
                makeText(
                        "New comment received",
                        17
                )
        );

        layout.addView(
                makeText(
                        "New message received",
                        17
                )
        );

        addBackButton(layout);
    }

    private void showProfile() {

        LinearLayout layout =
                createPage();

        layout.addView(
                makeText(
                        "MY PROFILE",
                        28
                )
        );

        layout.addView(
                makeText(
                        "Name: Viyzo User",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Followers: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Following: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Likes Received: " + likeCount,
                        18
                )
        );

        Button edit =
                makeButton(
                        "EDIT PROFILE"
                );

        layout.addView(edit);

        Button videos =
                makeButton(
                        "MY VIDEOS"
                );

        layout.addView(videos);

        addBackButton(layout);
    }

    private void showMyDashboard() {

        LinearLayout layout =
                createPage();

        layout.addView(
                makeText(
                        "MY DASHBOARD",
                        28
                )
        );

        layout.addView(
                makeText(
                        "Video Views: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Total Likes: " + likeCount,
                        18
                )
        );

        layout.addView(
                makeText(
                        "Comments: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Shares: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Followers: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Following: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Creator Earnings: ₹0",
                        18
                )
        );

        Button analytics =
                makeButton(
                        "ANALYTICS"
                );

        layout.addView(analytics);

        addBackButton(layout);
    }

    private void showFriends() {

        LinearLayout layout =
                createPage();

        layout.addView(
                makeText(
                        "FRIENDS / FOLLOWERS",
                        26
                )
        );

        layout.addView(
                makeButton("FRIENDS")
        );

        layout.addView(
                makeButton("FOLLOWERS")
        );

        layout.addView(
                makeButton("FOLLOWING")
        );

        layout.addView(
                makeButton("FRIEND REQUESTS")
        );

        layout.addView(
                makeButton("BLOCKED USERS")
        );

        addBackButton(layout);
    }

    private void showSearch() {

        LinearLayout layout =
                createPage();

        layout.addView(
                makeText(
                        "SEARCH USERS",
                        26
                )
        );

        EditText searchBox =
                new EditText(this);

        searchBox.setHint(
                "Search name or username"
        );

        searchBox.setTextColor(
                Color.WHITE
        );

        searchBox.setHintTextColor(
                Color.GRAY
        );

        layout.addView(searchBox);

        Button searchButton =
                makeButton("SEARCH");

        layout.addView(searchButton);

        searchButton.setOnClickListener(
                v -> Toast.makeText(
                        this,
                        "Search test",
                        Toast.LENGTH_SHORT
                ).show()
        );

        addBackButton(layout);
    }

    private void showReport() {

        String[] options = {
                "Report User",
                "Report Video",
                "Spam",
                "Harassment",
                "Copyright",
                "Block User"
        };

        new AlertDialog.Builder(this)
                .setTitle(
                        "REPORT / BLOCK"
                )
                .setItems(
                        options,
                        (dialog, which) -> {

                            Toast.makeText(
                                    this,
                                    options[which],
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                )
                .setNegativeButton(
                        "CANCEL",
                        null
                )
                .show();
    }

    private void showSettings() {

        LinearLayout layout =
                createPage();

        layout.addView(
                makeText(
                        "SETTINGS & PRIVACY",
                        26
                )
        );

        layout.addView(
                makeButton(
                        "ACCOUNT & PASSWORD"
                )
        );

        layout.addView(
                makeButton(
                        "PRIVACY"
                )
        );

        layout.addView(
                makeButton(
                        "SECURITY"
                )
        );

        layout.addView(
                makeButton(
                        "NOTIFICATION SETTINGS"
                )
        );

        layout.addView(
                makeButton(
                        "MESSAGE SETTINGS"
                )
        );

        layout.addView(
                makeButton(
                        "FOLLOWERS & FOLLOWING"
                )
        );

        layout.addView(
                makeButton(
                        "BLOCKING"
                )
        );

        layout.addView(
                makeButton(
                        "CONTENT PREFERENCES"
                )
        );

        layout.addView(
                makeButton(
                        "LANGUAGE"
                )
        );

        layout.addView(
                makeButton(
                        "DATA USAGE"
                )
        );

        layout.addView(
                makeButton(
                        "HELP & SUPPORT"
                )
        );

        layout.addView(
                makeButton(
                        "TERMS & POLICIES"
                )
        );

        layout.addView(
                makeButton(
                        "DELETE ACCOUNT"
                )
        );

        addBackButton(layout);
    }

    private void showAdminDashboard() {

        LinearLayout layout =
                createPage();

        layout.addView(
                makeText(
                        "ADMIN DASHBOARD",
                        28
                )
        );

        layout.addView(
                makeText(
                        "Users: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Videos: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Reports: 0",
                        18
                )
        );

        layout.addView(
                makeText(
                        "Active Users: 0",
                        18
                )
        );

        layout.addView(
                makeButton(
                        "MANAGE USERS"
                )
        );

        layout.addView(
                makeButton(
                        "MANAGE VIDEOS"
                )
        );

        layout.addView(
                makeButton(
                        "MANAGE REPORTS"
                )
        );

        layout.addView(
                makeButton(
                        "DISPUTES"
                )
        );

        layout.addView(
                makeButton(
                        "MESSAGE MODERATION"
                )
        );

        layout.addView(
                makeButton(
                        "CREATOR MANAGEMENT"
                )
        );

        layout.addView(
                makeButton(
                        "CREATOR EARNINGS"
                )
        );

        layout.addView(
                makeButton(
                        "ADS MANAGEMENT"
                )
        );

        layout.addView(
                makeButton(
                        "APP ANALYTICS"
                )
        );

        layout.addView(
                makeButton(
                        "APP SETTINGS"
                )
        );

        addBackButton(layout);
    }

    private void addBackButton(
            LinearLayout layout
    ) {

        Button back =
                makeButton("BACK");

        back.setOnClickListener(
                v -> showHome()
        );

        layout.addView(back);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (
                requestCode == 100 &&
                resultCode == RESULT_OK &&
                data != null
        ) {

            Uri videoUri =
                    data.getData();

            if (videoUri != null) {

                try {

                    getContentResolver()
                            .takePersistableUriPermission(
                                    videoUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );

                } catch (Exception ignored) {
                }

                MediaController controller =
                        new MediaController(this);

                controller.setAnchorView(
                        videoView
                );

                videoView.setMediaController(
                        controller
                );

                videoView.setVideoURI(
                        videoUri
                );

                videoView.setOnPreparedListener(
                        mp -> {

                            mp.setLooping(true);

                            videoView.start();
                        }
                );

                videoView.requestFocus();
            }
        }
    }
}
