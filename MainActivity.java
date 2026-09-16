package com.viyzo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {

    /* =========================================================
       VIYZO GO — ALL-IN-ONE PROTOTYPE
       Keeps the working VideoView select/play flow.
       ========================================================= */

    private VideoView videoView;
    private TextToSpeech tts;
    private AccountDb db;
    private MediaPlayer musicPreview;

    private int likeCount = 0;
    private boolean liked = false;
    private int commentCount = 0;
    private int shareCount = 0;

    private String currentName = "Viyzo User";
    private String currentUsername = "";
    private String currentDob = "";
    private String currentPhone = "";
    private String currentEmail = "";
    private String selectedLanguageName = "English";
    private int friendCount = 0;
    private String postAs = "Personal ID";
    private String pageName = "";
    private Locale selectedVoiceLocale = Locale.ENGLISH;

    private Uri selectedVideoUri;
    private Uri selectedMusicUri;
    private Uri selectedPhotoUri;
    private Uri selectedProfilePhotoUri;

    private static final int REQ_VIDEO = 100;
    private static final int REQ_MUSIC = 101;
    private static final int REQ_PHOTO = 102;
    private static final int REQ_PROFILE_PHOTO = 103;
    private static final int REQ_STATUS_PHOTO = 104;

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(dp(8), dp(7), dp(8), dp(7));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout page() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(14));
        root.setBackgroundColor(Color.rgb(18,18,22));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        return root;
    }

    private void title(LinearLayout r, String s) {
        TextView t = text(s, 27);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        r.addView(t);
    }

    private void addBack(LinearLayout r) {
        Button b = button("← BACK");
        b.setOnClickListener(v -> showHome());
        r.addView(b);
    }

    private Button voiceButton(String message) {
        Button b = button("🔊 LISTEN");
        b.setOnClickListener(v -> speak(message));
        return b;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        db = new AccountDb();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(selectedVoiceLocale);
            }
        });

        showLogin();
    }

    @Override
    protected void onDestroy() {
        if (musicPreview != null) {
            try { musicPreview.release(); } catch (Exception ignored) {}
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private void speak(String message) {
        if (tts == null || message == null || message.isEmpty()) return;

        int result = tts.setLanguage(selectedVoiceLocale);
        if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH);
        }

        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "VIYZO_GUIDE");
    }

    /* ================= LOGIN ================= */

    private void showLogin() {
        LinearLayout r = page();

        title(r, "VIYZO GO");
        r.addView(text("VIDEO • FRIENDS • MESSAGES • CREATOR", 14));
        r.addView(voiceButton("Welcome to Viyzo Go. Enter your email and password to log in."));

        EditText email = new EditText(this);
        email.setHint("Email");
        email.setTextColor(Color.WHITE);
        email.setHintTextColor(Color.GRAY);
        email.setInputType(33);
        r.addView(email);

        EditText password = new EditText(this);
        password.setHint("Password");
        password.setTextColor(Color.WHITE);
        password.setHintTextColor(Color.GRAY);
        password.setInputType(129);
        r.addView(password);

        Button login = button("LOGIN");
        login.setOnClickListener(v -> {
            if (email.getText().toString().trim().isEmpty()
                    || password.getText().toString().isEmpty()) {
                Toast.makeText(this, "Email and password required", Toast.LENGTH_SHORT).show();
                speak("Email and password are required.");
                return;
            }

            currentEmail = email.getText().toString().trim();
            showHome();
        });
        r.addView(login);

        Button create = button("CREATE NEW ACCOUNT");
        create.setOnClickListener(v -> showSignup());
        r.addView(create);

        Button forgot = button("FORGOT PASSWORD");
        forgot.setOnClickListener(v ->
                showSettingInfo("FORGOT PASSWORD",
                        "Password reset is a prototype screen. Real secure reset needs an online authentication service."));
        r.addView(forgot);

        r.addView(text("Prototype login • Online authentication will be connected later", 12));
    }

    /* ================= ACCOUNT CREATION ================= */

    private void showSignup() {
        LinearLayout r = page();

        title(r, "CREATE VIYZO ACCOUNT");
        r.addView(voiceButton("We will create your Viyzo account step by step."));

        final String[] accountType = {"Personal Account"};

        Button type = button("👤 PERSONAL ACCOUNT");
        type.setOnClickListener(v -> {
            String[] options = {"Personal Account", "Creator Account"};
            new AlertDialog.Builder(this)
                    .setTitle("ACCOUNT TYPE")
                    .setSingleChoiceItems(options, 0, (d, which) -> {
                        accountType[0] = options[which];
                        type.setText(which == 0
                                ? "👤 PERSONAL ACCOUNT"
                                : "🎬 CREATOR ACCOUNT");
                        speak("Selected " + options[which]);
                        d.dismiss();
                    }).show();
        });
        r.addView(type);

        EditText name = field("Full name");
        r.addView(name);
        r.addView(voiceButton("Enter your full name."));

        EditText username = field("Username");
        r.addView(username);
        r.addView(voiceButton("Choose your unique Viyzo username."));

        EditText email = field("Email");
        email.setInputType(33);
        r.addView(email);
        r.addView(voiceButton("Enter your email address."));

        EditText phone = field("Mobile number");
        phone.setInputType(2);
        r.addView(phone);
        r.addView(voiceButton("Enter your mobile number."));

        TextView dob = text("🎂 Date of birth: Not selected", 16);
        r.addView(dob);

        Button dobButton = button("📅 SELECT DATE OF BIRTH");
        dobButton.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();

            DatePickerDialog picker = new DatePickerDialog(
                    this,
                    (view, year, month, day) -> {
                        String value = year + "-" + (month + 1) + "-" + day;
                        dob.setTag(value);
                        dob.setText("🎂 Date of birth: " + day + "/" + (month + 1) + "/" + year);
                        speak("Date of birth selected.");
                    },
                    2000, 0, 1
            );

            picker.getDatePicker().setMaxDate(now.getTimeInMillis());
            picker.show();
            speak("Choose your date of birth.");
        });
        r.addView(dobButton);

        final String[] gender = {"Not specified"};
        Button genderButton = button("⚧ GENDER (OPTIONAL)");
        genderButton.setOnClickListener(v -> {
            String[] options = {"Woman", "Man", "Non-binary", "Prefer not to say"};
            new AlertDialog.Builder(this)
                    .setTitle("GENDER")
                    .setSingleChoiceItems(options, -1, (d, which) -> {
                        gender[0] = options[which];
                        genderButton.setText("⚧ " + options[which]);
                        d.dismiss();
                    }).show();
        });
        r.addView(genderButton);

        EditText password = field("Password");
        password.setInputType(129);
        r.addView(password);
        r.addView(voiceButton("Create a strong password."));

        EditText confirm = field("Confirm password");
        confirm.setInputType(129);
        r.addView(confirm);
        r.addView(voiceButton("Enter the same password again."));

        Button language = button("🌐 LANGUAGE: " + selectedLanguageName);
        language.setOnClickListener(v -> showLanguagePicker(language));
        r.addView(language);

        Button create = button("CREATE ACCOUNT");
        create.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String u = username.getText().toString().trim();
            String e = email.getText().toString().trim();
            String p = phone.getText().toString().trim();
            String pass = password.getText().toString();

            if (n.isEmpty() || u.isEmpty() || e.isEmpty() || p.isEmpty()
                    || dob.getTag() == null || pass.isEmpty()) {
                Toast.makeText(this, "Please complete all required fields.", Toast.LENGTH_SHORT).show();
                speak("Please complete all required fields.");
                return;
            }

            if (!pass.equals(confirm.getText().toString())) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
                speak("The passwords do not match.");
                return;
            }

            currentName = n;
            currentUsername = u;
            currentEmail = e;
            currentPhone = p;
            currentDob = String.valueOf(dob.getTag());

            try {
                SQLiteDatabase database = db.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("name", n);
                values.put("username", u);
                values.put("email", e);
                values.put("phone", p);
                values.put("dob", currentDob);
                values.put("gender", gender[0]);
                values.put("account_type", accountType[0]);
                values.put("language", selectedLanguageName);
                values.put("password", pass); // PROTOTYPE ONLY
                database.insert("accounts", null, values);
            } catch (Exception ex) {
                Toast.makeText(this, "Account saved locally for prototype.", Toast.LENGTH_SHORT).show();
            }

            showFriendSuggestions();
        });
        r.addView(create);

        Button back = button("← BACK TO LOGIN");
        back.setOnClickListener(v -> showLogin());
        r.addView(back);

        r.addView(text(
                "Important: this prototype uses local storage. Password storage must be replaced with secure online authentication before release.",
                11));
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setPadding(dp(10), dp(5), dp(10), dp(5));
        return e;
    }

    private void showFriendSuggestions() {
        LinearLayout r = page();
        title(r, "👥 FIND PEOPLE");
        r.addView(text("Choose people you want to follow, or skip this step.", 15));

        String[] people = {
                "Viyzo Creator", "Music Fans", "Travel Videos",
                "Comedy Videos", "Sports Videos", "News & Updates"
        };

        for (String person : people) {
            Button follow = button("➕ FOLLOW  " + person);
            follow.setOnClickListener(v -> {
                follow.setText("✓ FOLLOWING  " + person);
                speak("You are now following " + person + ".");
            });
            r.addView(follow);
        }

        Button done = button("DONE — GO TO HOME");
        done.setOnClickListener(v -> showHome());
        r.addView(done);
    }

    /* ================= HOME ================= */

    private void showHome() {
        LinearLayout r = page();

        title(r, "VIYZO GO");
        TextView sub = text("VIDEO • FRIENDS • MESSAGES • CREATOR", 13);
        sub.setGravity(Gravity.CENTER);
        r.addView(sub);

        TextView feed = text("FOR YOU  •  FOLLOWING  •  TRENDING", 14);
        feed.setGravity(Gravity.CENTER);
        r.addView(feed);

        videoView = new VideoView(this);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(250));
        vp.setMargins(0, dp(8), 0, dp(8));
        r.addView(videoView, vp);

        /* KEEPING THE WORKING VIDEO SELECTION FLOW */
        Button select = button("🎬 SELECT / UPLOAD VIDEO");
        select.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQ_VIDEO);
        });
        r.addView(select);

        Button postAsButton = button("📝 POST AS: " + postAs);
        postAsButton.setOnClickListener(v -> showPostAs());
        r.addView(postAsButton);

        Button friends = button("👥 FRIENDS " + friendCount + " / 5000");
        friends.setOnClickListener(v -> addFriendTest());
        r.addView(friends);

        Button pageButton = button("📄 MY PAGE");
        pageButton.setOnClickListener(v -> showPageManager());
        r.addView(pageButton);

        Button liveButton = button("🔴 LIVE VIDEO");
        liveButton.setOnClickListener(v -> showLiveSetup());
        r.addView(liveButton);

        Button music = button("🎵 ADD MUSIC / SAFE MUSIC LIBRARY");
        music.setOnClickListener(v -> showMusicLibrary());
        r.addView(music);

        Button photo = button("🖼️ PHOTO POST");
        photo.setOnClickListener(v -> selectPhotoPost());
        r.addView(photo);

        Button status = button("🟢 STATUS 24H");
        status.setOnClickListener(v -> showStatusCreator());
        r.addView(status);

        Button mention = button("＠ MENTION / TAG PEOPLE");
        mention.setOnClickListener(v -> showMention());
        r.addView(mention);

        LinearLayout a = row();
        Button like = button("❤️ LIKE " + likeCount);
        like.setOnClickListener(v -> {
            liked = !liked;
            if (liked) likeCount++;
            else if (likeCount > 0) likeCount--;
            like.setText((liked ? "❤️ LIKED " : "❤️ LIKE ") + likeCount);
        });

        Button comment = button("💬 COMMENT " + commentCount);
        comment.setOnClickListener(v -> {
            showComments();
            commentCount++;
        });
        addRow(a, like, comment);
        r.addView(a);

        LinearLayout b = row();
        Button share = button("🔗 SHARE");
        share.setOnClickListener(v -> {
            shareCount++;
            shareVideo();
        });

        Button save = button("🔖 SAVE");
        save.setOnClickListener(v -> Toast.makeText(this, "Saved - prototype", Toast.LENGTH_SHORT).show());
        addRow(b, share, save);
        r.addView(b);

        LinearLayout c = row();
        Button profile = button("👤 PROFILE");
        profile.setOnClickListener(v -> showProfile());

        Button friends = button("👥 FRIENDS");
        friends.setOnClickListener(v -> showFriends());
        addRow(c, profile, friends);
        r.addView(c);

        LinearLayout d = row();
        Button notif = button("🔔 NOTIFICATIONS");
        notif.setOnClickListener(v -> showNotifications());

        Button search = button("🔎 SEARCH");
        search.setOnClickListener(v -> showSearch());
        addRow(d, notif, search);
        r.addView(d);

        LinearLayout e = row();
        Button dashboard = button("📊 MY DASHBOARD");
        dashboard.setOnClickListener(v -> showMyDashboard());

        Button creator = button("💰 CREATOR");
        creator.setOnClickListener(v -> showCreatorDashboard());
        addRow(e, dashboard, creator);
        r.addView(e);

        LinearLayout f = row();
        Button settings = button("⚙️ SETTINGS");
        settings.setOnClickListener(v -> showSettings());

        Button messages = button("💬 MESSAGES");
        messages.setOnClickListener(v -> showMessages());
        addRow(f, settings, messages);
        r.addView(f);

        LinearLayout g = row();
        Button report = button("🚨 REPORT / BLOCK");
        report.setOnClickListener(v -> showReport());

        Button admin = button("🛠️ ADMIN");
        admin.setOnClickListener(v -> showAdminDashboard());
        addRow(g, report, admin);
        r.addView(g);

        Button account = button("👤 ACCOUNT: " + currentName);
        account.setOnClickListener(v -> showAccountDetails());
        r.addView(account);

        Button logout = button("🚪 LOGOUT");
        logout.setOnClickListener(v -> showLogin());
        r.addView(logout);

        r.addView(text(
                "Music rule: Viyzo should only publish tracks that are Viyzo Original, public-domain, properly licensed, or otherwise authorized. Code cannot make copyrighted music automatically license-free.",
                11));
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(3), 0, dp(3));
        return r;
    }

    private void addRow(LinearLayout r, Button a, Button b) {
        r.addView(a, new LinearLayout.LayoutParams(0, dp(58), 1));
        r.addView(b, new LinearLayout.LayoutParams(0, dp(58), 1));
    }

    /* ================= VIDEO ================= */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        if (requestCode == REQ_VIDEO) {
            selectedVideoUri = uri;

            if (videoView != null) {
                MediaController controller = new MediaController(this);
                controller.setAnchorView(videoView);
                videoView.setMediaController(controller);
                videoView.setVideoURI(selectedVideoUri);

                videoView.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    videoView.start();
                });

                videoView.requestFocus();
            }

            Toast.makeText(this, "Video selected and playing.", Toast.LENGTH_SHORT).show();
            speak("Your video has been selected.");
        }

        if (requestCode == REQ_MUSIC) {
            selectedMusicUri = uri;
            Toast.makeText(this,
                    "Audio selected. It is not automatically mixed into the video in this prototype.",
                    Toast.LENGTH_LONG).show();
            previewMusic(uri);
        }

        if (requestCode == REQ_PHOTO || requestCode == REQ_STATUS_PHOTO) {
            selectedPhotoUri = uri;
            Toast.makeText(this, "Photo selected.", Toast.LENGTH_SHORT).show();
        }

        if (requestCode == REQ_PROFILE_PHOTO) {
            selectedProfilePhotoUri = uri;
            Toast.makeText(this, "Profile photo selected.", Toast.LENGTH_SHORT).show();
        }
    }

    /* ================= MUSIC ================= */

    private void showMusicLibrary() {
        LinearLayout r = page();
        title(r, "🎵 VIYZO MUSIC");

        r.addView(text(
                "SAFE MUSIC SYSTEM: use only Viyzo Original, public-domain, royalty-free with compatible terms, or properly licensed tracks.",
                13));

        Button original = button("🎶 VIYZO ORIGINAL MUSIC");
        original.setOnClickListener(v ->
                showSettingInfo("VIYZO ORIGINAL MUSIC",
                        "This category is intended for tracks owned or commissioned by Viyzo."));
        r.addView(original);

        Button publicDomain = button("🏛️ PUBLIC DOMAIN");
        publicDomain.setOnClickListener(v ->
                showSettingInfo("PUBLIC DOMAIN",
                        "Only tracks that are genuinely public domain in the relevant countries should be placed here."));
        r.addView(publicDomain);

        Button royalty = button("✅ ROYALTY-FREE / LICENSED");
        royalty.setOnClickListener(v ->
                showSettingInfo("ROYALTY-FREE / LICENSED",
                        "Store tracks only when their license permits the intended Viyzo use, including commercial use where applicable."));
        r.addView(royalty);

        Button choose = button("📂 CHOOSE MY OWN AUDIO");
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("audio/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQ_MUSIC);
        });
        r.addView(choose);

        Button stop = button("⏹️ STOP MUSIC PREVIEW");
        stop.setOnClickListener(v -> stopMusicPreview());
        r.addView(stop);

        addBack(r);
    }

    private void previewMusic(Uri uri) {
        stopMusicPreview();

        try {
            musicPreview = MediaPlayer.create(this, uri);
            if (musicPreview != null) {
                musicPreview.setOnCompletionListener(mp -> stopMusicPreview());
                musicPreview.start();
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Cannot preview this audio.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopMusicPreview() {
        if (musicPreview != null) {
            try {
                if (musicPreview.isPlaying()) musicPreview.stop();
            } catch (Exception ignored) {}
            try { musicPreview.release(); } catch (Exception ignored) {}
            musicPreview = null;
        }
    }

    /* ================= PHOTO / STATUS / MENTION ================= */

    private void selectPhotoPost() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PHOTO);
    }

    private void showStatusCreator() {
        LinearLayout r = page();
        title(r, "🟢 STATUS 24 HOURS");

        EditText status = field("Write your status");
        status.setMinLines(3);
        r.addView(status);

        Button photo = button("🖼️ ADD STATUS PHOTO");
        photo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_STATUS_PHOTO);
        });
        r.addView(photo);

        Button post = button("POST STATUS");
        post.setOnClickListener(v ->
                Toast.makeText(this, "Status posted for 24 hours - prototype.", Toast.LENGTH_SHORT).show());
        r.addView(post);

        addBack(r);
    }

    private void showMention() {
        LinearLayout r = page();
        title(r, "＠ MENTION PEOPLE");

        EditText s = field("@username");
        r.addView(s);

        Button add = button("ADD MENTION");
        add.setOnClickListener(v ->
                Toast.makeText(this, "Mention added: " + s.getText().toString(), Toast.LENGTH_SHORT).show());
        r.addView(add);

        addBack(r);
    }

    /* ================= SOCIAL ================= */

    private void showComments() {
        EditText input = field("Write a comment");

        new AlertDialog.Builder(this)
                .setTitle("💬 COMMENTS")
                .setMessage("Reply • Like • Delete • Report")
                .setView(input)
                .setPositiveButton("POST",
                        (d, w) -> Toast.makeText(this, "Comment posted - prototype", Toast.LENGTH_SHORT).show())
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void shareVideo() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "Watch this video on Viyzo Go");
        startActivity(Intent.createChooser(share, "Share Viyzo Video"));
    }

    private void showMessages() {
        LinearLayout r = page();
        title(r, "💬 MESSAGES");

        r.addView(button("➕ NEW MESSAGE"));
        r.addView(button("📩 MESSAGE REQUESTS"));
        r.addView(button("USER 1"));
        r.addView(button("USER 2"));
        r.addView(button("🔐 MESSAGE PRIVACY"));

        addBack(r);
    }

    private void showNotifications() {
        LinearLayout r = page();
        title(r, "🔔 NOTIFICATIONS");

        r.addView(text("❤️ Someone liked your video", 17));
        r.addView(text("👤 Someone followed you", 17));
        r.addView(text("💬 New comment received", 17));
        r.addView(text("📩 New message received", 17));
        r.addView(text("🎬 Creator notification", 17));

        addBack(r);
    }

    private void showProfile() {
        LinearLayout r = page();
        title(r, "👤 MY PROFILE");

        r.addView(text("Name: " + currentName, 18));
        r.addView(text("Username: @" + currentUsername, 18));
        r.addView(text("Followers: 0", 18));
        r.addView(text("Following: 0", 18));
        r.addView(text("Likes Received: " + likeCount, 18));

        Button photo = button("🖼️ CHANGE PROFILE PHOTO");
        photo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_PROFILE_PHOTO);
        });
        r.addView(photo);

        r.addView(button("✏️ EDIT PROFILE"));
        r.addView(button("🎬 MY VIDEOS"));
        r.addView(button("🟢 MY STATUS"));

        addBack(r);
    }

    private void showFriends() {
        LinearLayout r = page();
        title(r, "👥 FRIENDS & FOLLOWERS");

        r.addView(button("👥 FRIENDS"));
        r.addView(button("👤 FOLLOWERS"));
        r.addView(button("➡️ FOLLOWING"));
        r.addView(button("📩 FRIEND REQUESTS"));
        r.addView(button("🚫 BLOCKED USERS"));

        addBack(r);
    }

    private void showSearch() {
        LinearLayout r = page();
        title(r, "🔎 SEARCH");

        EditText s = field("Search name, username, hashtag");
        r.addView(s);

        Button search = button("🔎 SEARCH");
        search.setOnClickListener(v ->
                Toast.makeText(this,
                        "Search: " + s.getText().toString() + " - prototype",
                        Toast.LENGTH_SHORT).show());
        r.addView(search);

        addBack(r);
    }

    /* ================= DASHBOARDS ================= */

    private void showMyDashboard() {
        LinearLayout r = page();
        title(r, "📊 MY DASHBOARD");

        r.addView(text("Video Views: 0", 18));
        r.addView(text("Total Likes: " + likeCount, 18));
        r.addView(text("Comments: " + commentCount, 18));
        r.addView(text("Shares: " + shareCount, 18));
        r.addView(text("Followers: 0", 18));
        r.addView(text("Following: 0", 18));
        r.addView(text("Creator Earnings: ₹0", 18));
        r.addView(text("Monetization: Not connected", 18));

        addBack(r);
    }

    private void showCreatorDashboard() {
        LinearLayout r = page();
        title(r, "💰 CREATOR DASHBOARD");

        r.addView(text("Creator status: Prototype", 17));
        r.addView(text("Views: 0", 17));
        r.addView(text("Watch time: 0", 17));
        r.addView(text("Followers: 0", 17));
        r.addView(text("Estimated earnings: ₹0", 17));
        r.addView(text("Payout account: Not connected", 17));

        r.addView(button("📈 ANALYTICS"));
        r.addView(button("💵 MONETIZATION"));
        r.addView(button("🏦 PAYOUT SETTINGS"));

        addBack(r);
    }

    private void showAdminDashboard() {
        LinearLayout r = page();
        title(r, "🛠️ ADMIN DASHBOARD");

        r.addView(text("User management", 17));
        r.addView(text("Video moderation", 17));
        r.addView(text("Reports & disputes", 17));
        r.addView(text("Creator monetization", 17));
        r.addView(text("Music rights / catalog management", 17));
        r.addView(text("Announcements", 17));

        r.addView(button("👥 MANAGE USERS"));
        r.addView(button("🎬 MODERATE VIDEOS"));
        r.addView(button("🚨 REVIEW REPORTS"));

        addBack(r);
    }

    /* ================= SETTINGS ================= */

    private void showPostAs() {
        String[] items = pageName.isEmpty() ? new String[]{"Personal ID"} : new String[]{"Personal ID", "Page: " + pageName};
        new AlertDialog.Builder(this).setTitle("POST AS").setItems(items, (d,w) -> { postAs=items[w]; speak("Posting as " + postAs); showHome(); }).show();
    }

    private void addFriendTest() {
        if (friendCount >= 5000) { toast("Friends limit reached: 5,000. You can still follow users."); return; }
        friendCount++; toast("Friend added: " + friendCount + "/5000");
    }

    private void showPageManager() {
        LinearLayout r=page(); title(r,"📄 MY PAGE");
        EditText name=field("Page name"); if(!pageName.isEmpty()) name.setText(pageName); r.addView(name);
        addSettingButton(r,"PAGE FEATURES","Profile photo\nCover photo\nBio\nFollowers\nVideos / Reels\nLive\nComments\nMessages\nNotifications\nManagers / Admin");
        Button save=button("SAVE PAGE"); save.setOnClickListener(v->{pageName=name.getText().toString().trim(); if(pageName.isEmpty()){toast("Enter a page name");return;} toast("Page saved: "+pageName);}); r.addView(save);
        addSettingButton(r,"PAGE MONETIZATION","Eligibility\nEarnings\nPayout status");
        Button back=button("BACK TO HOME"); back.setOnClickListener(v->showHome()); r.addView(back); setContentView(r);
    }

    private void showLiveSetup() {
        LinearLayout r=page(); title(r,"🔴 LIVE VIDEO");
        addSettingButton(r,"LIVE CONTROLS","Camera + microphone permission\nLive title\nAudience\nLive comments\nReactions\nViewer count\nEnd live\nSave replay");
        EditText t=field("Live title"); r.addView(t);
        Button start=button("START TEST LIVE"); start.setOnClickListener(v->{toast("Test Live started. Online live streaming needs a streaming server."); speak("Live started");}); r.addView(start);
        Button end=button("END LIVE"); end.setOnClickListener(v->{toast("Live ended"); speak("Live ended");}); r.addView(end);
        Button back=button("BACK TO HOME"); back.setOnClickListener(v->showHome()); r.addView(back); setContentView(r);
    }

    private void showSettings() {
        LinearLayout r = page();
        title(r, "⚙️ SETTINGS & PRIVACY");

        addSettingButton(r, "🔐 ACCOUNT & PASSWORD",
                "Change password\nChange email\nEdit account name\nLogin activity");

        addSettingButton(r, "🔒 PRIVACY",
                "Profile privacy\nWho can message you\nPrivate account\nActivity visibility");

        addSettingButton(r, "🛡️ SECURITY",
                "Login alerts\nTwo-step verification\nActive sessions\nSecurity checkup");

        addSettingButton(r, "🔔 NOTIFICATION SETTINGS",
                "Likes\nComments\nFollowers\nMessages\nCreator notifications");

        addSettingButton(r, "💬 MESSAGE SETTINGS",
                "Message requests\nWho can message you\nRead receipts");

        addSettingButton(r, "👥 FOLLOWERS & FOLLOWING",
                "Manage followers\nFollowing list\nFriend requests\nRemove follower");

        addSettingButton(r, "🚫 BLOCKING",
                "Blocked users\nBlocked messages\nBlocked videos");

        addSettingButton(r, "🎬 CONTENT PREFERENCES",
                "Recommendations\nSensitive content\nTopics\nNot interested");

        Button language = button("🌐 LANGUAGE: " + selectedLanguageName);
        language.setOnClickListener(v -> showLanguagePicker(language));
        r.addView(language);

        addSettingButton(r, "📱 DATA USAGE",
                "Data saver\nVideo quality\nAutoplay\nWi-Fi only");

        addSettingButton(r, "❓ HELP & SUPPORT",
                "Help Center\nReport a problem\nContact support\nAccount help");

        addSettingButton(r, "📄 TERMS & POLICIES",
                "Terms\nPrivacy Policy\nCommunity Guidelines\nCreator Policy");

        Button delete = button("⚠️ DELETE ACCOUNT");
        delete.setOnClickListener(v -> showDeleteAccountDialog());
        r.addView(delete);

        Button back = button("← BACK TO HOME");
        back.setOnClickListener(v -> showHome());
        r.addView(back);
    }

    private void addSettingButton(LinearLayout r, String label, String info) {
        Button b = button(label);
        b.setOnClickListener(v -> showSettingInfo(label, info));
        r.addView(b);
    }

    private void showSettingInfo(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ DELETE ACCOUNT")
                .setMessage("This prototype can remove the local account. A real online account deletion flow must delete server data according to the final privacy policy.")
                .setPositiveButton("DELETE LOCAL TEST", (d, w) -> {
                    try {
                        db.getWritableDatabase().delete("accounts", null, null);
                    } catch (Exception ignored) {}
                    Toast.makeText(this, "Local test account deleted.", Toast.LENGTH_SHORT).show();
                    showLogin();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    /* ================= LANGUAGE + VOICE ================= */

    private void showLanguagePicker(Button target) {
        Locale[] locales = Locale.getAvailableLocales();

        ArrayList<String> names = new ArrayList<>();
        final ArrayList<Locale> usable = new ArrayList<>();

        for (Locale loc : locales) {
            String name = loc.getDisplayLanguage(loc);
            if (name != null && !name.isEmpty() && !names.contains(name)) {
                names.add(name);
                usable.add(loc);
            }
        }

        ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) order.add(i);

        Collections.sort(order, (a, b) ->
                names.get(a).compareToIgnoreCase(names.get(b)));

        String[] display = new String[order.size()];
        Locale[] sortedLocales = new Locale[order.size()];

        for (int i = 0; i < order.size(); i++) {
            display[i] = names.get(order.get(i));
            sortedLocales[i] = usable.get(order.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("🌐 ALL AVAILABLE LANGUAGES")
                .setItems(display, (d, which) -> {
                    selectedLanguageName = display[which];
                    selectedVoiceLocale = sortedLocales[which];

                    if (tts != null) tts.setLanguage(selectedVoiceLocale);

                    target.setText("🌐 LANGUAGE: " + selectedLanguageName);
                    speak("Language changed to " + selectedLanguageName + ".");
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    /* ================= ACCOUNT ================= */

    private void showAccountDetails() {
        LinearLayout r = page();
        title(r, "👤 ACCOUNT");

        r.addView(text("Name: " + currentName, 17));
        r.addView(text("Username: @" + currentUsername, 17));
        r.addView(text("Email: " + currentEmail, 17));
        r.addView(text("Mobile: " + currentPhone, 17));
        r.addView(text("Date of birth: " + currentDob, 17));
        r.addView(text("Language: " + selectedLanguageName, 17));

        addBack(r);
    }

    /* ================= REPORT / BLOCK ================= */

    private void showReport() {
        String[] options = {
                "Report User", "Report Video", "Spam",
                "Harassment", "Copyright", "Block User"
        };

        new AlertDialog.Builder(this)
                .setTitle("🚨 REPORT / BLOCK")
                .setItems(options,
                        (d, w) -> Toast.makeText(this,
                                options[w] + " - submitted as prototype",
                                Toast.LENGTH_SHORT).show())
                .setNegativeButton("CANCEL", null)
                .show();
    }

    /* ================= LOCAL DATABASE ================= */

    private class AccountDb extends SQLiteOpenHelper {

        AccountDb() {
            super(MainActivity.this, "viyzo_go.db", null, 2);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS pages (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE,followers INTEGER DEFAULT 0)");
            db.execSQL(
                    "CREATE TABLE accounts (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "name TEXT," +
                            "username TEXT UNIQUE," +
                            "email TEXT UNIQUE," +
                            "phone TEXT," +
                            "dob TEXT," +
                            "gender TEXT," +
                            "account_type TEXT," +
                            "language TEXT," +
                            "password TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS pages (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE,followers INTEGER DEFAULT 0)");
        }
    }
}
