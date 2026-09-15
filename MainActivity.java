package com.viyzo.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {

    private VideoView videoView;
    private TextView likeText;
    private int likeCount = 0;
    private boolean liked = false;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView title(String text, int size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHome();
    }

    private LinearLayout baseLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(18, 18, 22));
        layout.setPadding(dp(12), dp(12), dp(12), dp(12));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);

        setContentView(scroll);
        return layout;
    }

    private void showHome() {

        LinearLayout layout = baseLayout();

        TextView logo = title("VIYZO GO", 30);
        logo.setGravity(Gravity.CENTER);
        layout.addView(logo);

        TextView welcome = new TextView(this);
        welcome.setText(
                "Social Video • Friends • Messages • Creator"
        );
        welcome.setTextColor(Color.LTGRAY);
        welcome.setTextSize(14);
        welcome.setGravity(Gravity.CENTER);
        layout.addView(welcome);

        videoView = new VideoView(this);

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(300)
                );

        videoParams.setMargins(0, dp(15), 0, dp(15));
        layout.addView(videoView, videoParams);

        Button selectVideo = button("🎬 Select / Upload Video");

        selectVideo.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            startActivityForResult(intent, 100);
        });

        layout.addView(selectVideo);

        likeText = new TextView(this);
        likeText.setText("❤️ Like 0");
        likeText.setTextColor(Color.WHITE);
        likeText.setTextSize(18);
        likeText.setPadding(
                dp(12), dp(15), dp(12), dp(10)
        );

        likeText.setOnClickListener(v -> {

            if (!liked) {
                liked = true;
                likeCount++;
            } else {
                liked = false;
                likeCount--;
            }

            likeText.setText("❤️ Like " + likeCount);
        });

        layout.addView(likeText);

        Button comment = button("💬 Comments");
        comment.setOnClickListener(v -> showCommentDialog());
        layout.addView(comment);

        Button share = button("🔗 Share Video");
        share.setOnClickListener(v -> shareVideo());
        layout.addView(share);

        Button messages = button("💬 Messages");
        messages.setOnClickListener(v -> showMessages());
        layout.addView(messages);

        Button notifications = button("🔔 Notifications");
        notifications.setOnClickListener(v -> showNotifications());
        layout.addView(notifications);

        Button profile = button("👤 My Profile");
        profile.setOnClickListener(v -> showProfile());
        layout.addView(profile);

        Button dashboard = button("📊 My Dashboard");
        dashboard.setOnClickListener(v -> showMyDashboard());
        layout.addView(dashboard);

        Button friends = button("👥 Friends / Followers");
        friends.setOnClickListener(v -> showFriends());
        layout.addView(friends);

        Button search = button("🔎 Search Users");
        search.setOnClickListener(v -> showSearch());
        layout.addView(search);

        Button report = button("🚨 Report / Block");
        report.setOnClickListener(v -> showReport());
        layout.addView(report);

        Button settings = button("⚙️ Settings & Privacy");
        settings.setOnClickListener(v -> showSettings());
        layout.addView(settings);

        Button admin = button("🛠️ Admin Dashboard");
        admin.setOnClickListener(v -> showAdminDashboard());
        layout.addView(admin);

        Button logout = button("🚪 Logout");
        logout.setOnClickListener(v -> {
            Toast.makeText(
                    this,
                    "Logout test button",
                    Toast.LENGTH_SHORT
            ).show();
        });
        layout.addView(logout);
    }

    private void showCommentDialog() {

        final EditText input = new EditText(this);
        input.setHint("Write a comment");

        new AlertDialog.Builder(this)
                .setTitle("💬 Comments")
                .setMessage(
                        "Comments screen\n\n" +
                        "• Like comment\n" +
                        "• Reply\n" +
                        "• Delete\n" +
                        "• Report"
                )
                .setView(input)
                .setPositiveButton("POST", (d, w) -> {

                    Toast.makeText(
                            this,
                            "Comment posted (test)",
                            Toast.LENGTH_SHORT
                    ).show();

                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void shareVideo() {

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(
                Intent.EXTRA_TEXT,
                "Watch this video on Viyzo Go"
        );

        startActivity(
                Intent.createChooser(
                        share,
                        "Share Viyzo Video"
                )
        );
    }

    private void showMessages() {

        LinearLayout layout = baseLayout();

        layout.addView(title("💬 Messages", 26));

        Button newChat = button("➕ New Message");
        layout.addView(newChat);

        Button chat1 = button("User 1");
        layout.addView(chat1);

        Button chat2 = button("User 2");
        layout.addView(chat2);

        Button requests = button("📩 Message Requests");
        layout.addView(requests);

        Button back = button("← Back");
        back.setOnClickListener(v -> showHome());
        layout.addView(back);
    }

    private void showNotifications() {

        LinearLayout layout = baseLayout();

        layout.addView(title("🔔 Notifications", 26));

        layout.addView(title(
                "❤️ Someone liked your video",
                17
        ));

        layout.addView(title(
                "👤 Someone followed you",
                17
        ));

        layout.addView(title(
                "💬 New comment received",
                17
        ));

        layout.addView(title(
                "💬 New message received",
                17
        ));

        Button back = button("← Back");
        back.setOnClickListener(v -> showHome());
        layout.addView(back);
    }

    private void showProfile() {

        LinearLayout layout = baseLayout();

        layout.addView(title("👤 MY PROFILE", 28));

        layout.addView(title(
                "Name: Viyzo User",
                18
        ));

        layout.addView(title(
                "Followers: 0",
                18
        ));

        layout.addView(title(
                "Following: 0",
                18
        ));

        layout.addView(title(
                "Likes Received: 0",
                18
        ));

        Button edit = button("✏️ Edit Profile");
        layout.addView(edit);

        Button videos = button("🎬 My Videos");
        layout.addView(videos);

        Button back = button("← Back");
        back.setOnClickListener(v -> showHome());
        layout.addView(back);
    }

    private void showMyDashboard() {

        LinearLayout layout = baseLayout();

        layout.addView(title(
                "📊 MY DASHBOARD",
                28
        ));

        layout.addView(title(
                "Video Views: 0",
                18
        ));

        layout.addView(title(
                "Total Likes: " + likeCount,
                18
        ));

        layout.addView(title(
                "Comments: 0",
                18
        ));

        layout.addView(title(
                "Shares: 0",
                18
        ));

        layout.addView(title(
                "Followers: 0",
                18
        ));

        layout.addView(title(
                "Following: 0",
                18
        ));

        layout.addView(title(
                "Creator Earnings: ₹0",
                18
        ));

        Button analytics = button("📈 Analytics");
        layout.addView(analytics);

        Button back = button("← Back");
        back.setOnClickListener(v -> showHome());
        layout.addView(back);
    }

    private void showFriends() {

        LinearLayout layout = baseLayout();

        layout.addView(title(
                "👥 FRIENDS & FOLLOWERS",
                26
        ));

        Button friends = button("👥 Friends");
        layout.addView(friends);

        Button followers = button("👤 Followers");
        layout.addView(followers);

        Button following = button("➡️ Following");
        layout.addView(following);

        Button requests = button("📩 Friend Requests");
        layout.addView(requests);

        Button blocked = button("🚫 Blocked Users");
        layout.addView(blocked);

        Button back = button("← Back");
        back.setOnClickListener(v -> showHome());
        layout.addView(back);
    }

    private void showSearch() {

        LinearLayout layout = baseLayout();

        layout.addView(title(
                "🔎 SEARCH USERS",
                26
        ));

        EditText search = new EditText(this);
        search.setHint("Search name or username");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.GRAY);
        layout.addView(search);

        Button searchButton = button("🔎 Search");
        layout.addView(searchButton);

        searchButton.setOnClickListener(v -> {

            Toast.makeText(
                    this,
                    "User search test",
                    Toast.LENGTH_SHORT
            ).show();

        });

        Button back = button("← Back");
        back.setOnClickListener(v -> showHome());
        layout.addView(back);
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
                .setTitle("🚨 Report / Block")
                .setItems(options, (dialog, which) -> {

                    Toast.makeText(
                            this,
                            options[which] + " selected",
                            Toast.LENGTH_SHORT
                    ).show();

                })
                .setNegativeButton(
                        "CANCEL",
                        null
                )
                .show();
    }

    private void showSettings() {

        LinearLayout layout = baseLayout();

        layout.addView(title(
                "⚙️ SETTINGS & PRIVACY",
                26
        ));

        Button account = button(
                "🔐 Account & Password"
        );
        layout.addView(account);

        Button privacy = button(
                "🔒 Privacy"
        );
        layout.addView(privacy);

        Button security = button(
                "🛡️ Security"
        );
        layout.addView(security);

        Button notifications = button(
                "🔔 Notification Settings"
        );
        layout.addView(notifications);

        Button messages = button(
                "💬 Message Settings"
        );
        layout.addView(messages);

        Button followers = button(
                "👥 Followers & Following"
        );
        layout.addView(followers);

        Button blocking = button(
                "🚫 Blocking"
        );
        layout.addView(blocking);

        Button content = button(
                "🎬 Content Preferences"
        );
        layout.addView(content);

        Button language = button(
                "🌐 Language"
        );
        layout.addView(language);

        Button data = button(
                "📱 Data Usage"
        );
        layout.addView(data);

        Button help = button(
                "❓ Help & Support"
        );
        layout.addView(help);

        Button terms = button(
                "📄 Terms & Policies"
        );
        layout.addView(terms);

        Button delete = button(
                "⚠️ Delete Account"
        );
        layout.addView(delete);

        Button back = button("← Back");
        back.setOnClickListener(v -> showHome());
        layout.addView(back);
    }

    private void showAdminDashboard() {

        LinearLayout layout = baseLayout();

        layout.addView(title(
                "🛠️ ADMIN DASHBOARD",
                28
        ));

        layout.addView(title(
                "Users: 0",
                18
        ));

        layout.addView(title(
                "Videos: 0",
                18
        ));

        layout.addView(title(
                "Reports: 0",
                18
        ));

        layout.addView(title(
                "Active Users: 0",
                18
        ));

        Button users = button(
                "👥 Manage Users"
        );
        layout.addView(users);

        Button videos = button(
                "🎬 Manage Videos"
        );
        layout.addView(videos);

        Button reports = button(
                "🚨 Manage Reports"
        );
        layout.addView(reports);

        Button disputes = button(
                "⚖️ Disputes"
        );
        layout.addView(disputes);

        Button messages = button(
                "💬 Message Moderation"
        );
        layout.addView(messages);

        Button creators = button(
                "⭐ Creator Management"
        );
        layout.addView(creators);

        Button earnings = button(
                "💰 Creator Earnings"
        );
        layout.addView(earnings);

        Button ads = button(
                "📢 Ads Management"
        );
        layout.addView(ads);

        Button analytics = button(
                "📈 App Analytics"
        );
        layout.addView(analytics);

        Button settings = button(
                "⚙️ App Settings"
        );
        layout.addView(settings);

        Button back = button("← Back");
        back.setOnClickListener(v -> showHome());
        layout.addView(back);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == 100 &&
                resultCode == RESULT_OK &&
                data != null) {

            Uri videoUri = data.getData();

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

                controller.setAnchorView(videoView);

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
