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
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private LinearLayout fixedBottomNav;

    /* =========================================================
       VIYZO GO — ALL-IN-ONE PROTOTYPE
       Keeps the working VideoView select/play flow.
       ========================================================= */

    private VideoView videoView;
    private TextToSpeech tts;
    private AccountDb db;
    private MediaPlayer musicPreview;

    // Firebase Authentication + Firestore
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseStorage firebaseStorage;
    private StorageReference storageRoot;
    private AdView bannerAd;

    // Monetization architecture: real AdMob revenue is paid to the Viyzo AdMob account.
    // Creator earnings are calculated only from server-side eligible events; never fake a payout.
    private long eligibleViews = 0;
    private double creatorEstimatedEarnings = 0.0;

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
    private String pageBio = "";
    private int followerCount = 0;
    private boolean privateAccount = false;
    private boolean dataSaver = false;
    private boolean autoplay = true;
    private String videoMode = "FULL WIDTH";
    private String audience = "Public";
    private boolean notificationsOn = true;
    private boolean messageRequestsOn = true;
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
        root.setPadding(dp(10), dp(10), dp(10), dp(90));
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

        // Firebase is already configured in the Android build.
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();
        storageRoot = firebaseStorage.getReference();

        // Google test ads are used in this build. Replace with your own AdMob IDs before release.
        MobileAds.initialize(this, status -> {});

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
        if (bannerAd != null) { try { bannerAd.destroy(); } catch (Exception ignored) {} }
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
        removeFixedBottomNavigation();
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
            String loginEmail = email.getText().toString().trim();
            String loginPassword = password.getText().toString();

            if (loginEmail.isEmpty() || loginPassword.isEmpty()) {
                Toast.makeText(this, "Email and password required", Toast.LENGTH_SHORT).show();
                speak("Email and password are required.");
                return;
            }

            login.setEnabled(false);
            login.setText("LOGGING IN...");

            firebaseAuth.signInWithEmailAndPassword(loginEmail, loginPassword)
                    .addOnCompleteListener(this, task -> {
                        login.setEnabled(true);
                        login.setText("LOGIN");

                        if (task.isSuccessful()) {
                            FirebaseUser user = firebaseAuth.getCurrentUser();
                            currentEmail = loginEmail;

                            if (user != null) {
                                firestore.collection("users")
                                        .document(user.getUid())
                                        .get()
                                        .addOnSuccessListener(doc -> {
                                            if (doc.exists()) {
                                                String savedName = doc.getString("name");
                                                String savedUsername = doc.getString("username");
                                                if (savedName != null && !savedName.isEmpty()) {
                                                    currentName = savedName;
                                                }
                                                if (savedUsername != null) {
                                                    currentUsername = savedUsername;
                                                }
                                            }
                                            Toast.makeText(this, "Login successful.", Toast.LENGTH_SHORT).show();
                                            speak("Login successful. Welcome back to Viyzo Go.");
                                            showHome();
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Login successful.", Toast.LENGTH_SHORT).show();
                                            speak("Login successful.");
                                            showHome();
                                        });
                            } else {
                                Toast.makeText(this, "Login successful.", Toast.LENGTH_SHORT).show();
                                showHome();
                            }
                        } else {
                            String message = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Login failed.";
                            Toast.makeText(this, "Login failed: " + message, Toast.LENGTH_LONG).show();
                            speak("Login failed. Please check your email and password.");
                        }
                    });
        });
        r.addView(login);

        Button create = button("CREATE NEW ACCOUNT");
        create.setOnClickListener(v -> showSignup());
        r.addView(create);

        Button forgot = button("FORGOT PASSWORD");
        forgot.setOnClickListener(v -> {
            String resetEmail = email.getText().toString().trim();

            if (resetEmail.isEmpty()) {
                Toast.makeText(this, "Enter your email first.", Toast.LENGTH_SHORT).show();
                speak("Enter your email address first.");
                return;
            }

            firebaseAuth.sendPasswordResetEmail(resetEmail)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Password reset email sent.", Toast.LENGTH_LONG).show();
                            speak("Password reset email sent.");
                        } else {
                            String message = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Could not send reset email.";
                            Toast.makeText(this, "Reset failed: " + message, Toast.LENGTH_LONG).show();
                            speak("Password reset could not be sent.");
                        }
                    });
        });
        r.addView(forgot);

        r.addView(text("Firebase online login • Email/password authentication", 12));
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

            create.setEnabled(false);
            create.setText("CREATING ACCOUNT...");

            // Firebase Authentication securely stores the password.
            // We intentionally do NOT save the password in the local SQLite database.
            firebaseAuth.createUserWithEmailAndPassword(e, pass)
                    .addOnCompleteListener(this, task -> {
                        create.setEnabled(true);
                        create.setText("CREATE ACCOUNT");

                        if (!task.isSuccessful()) {
                            String message = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Account creation failed.";
                            Toast.makeText(this,
                                    "Account creation failed: " + message,
                                    Toast.LENGTH_LONG).show();
                            speak("Account creation failed. Please check your details.");
                            return;
                        }

                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user == null) {
                            Toast.makeText(this, "Account created, but user session was not found.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        Map<String, Object> profile = new HashMap<>();
                        profile.put("uid", user.getUid());
                        profile.put("name", n);
                        profile.put("username", u);
                        profile.put("email", e);
                        profile.put("phone", p);
                        profile.put("dob", currentDob);
                        profile.put("gender", gender[0]);
                        profile.put("accountType", accountType[0]);
                        profile.put("language", selectedLanguageName);
                        profile.put("followersCount", 0);
                        profile.put("followingCount", 0);
                        profile.put("likesReceived", 0);
                        profile.put("eligibleViews", 0);
                        profile.put("estimatedEarnings", 0.0);
                        profile.put("monetizationStatus", "NOT_ELIGIBLE");
                        profile.put("payoutStatus", "NOT_CONNECTED");
                        profile.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

                        firestore.collection("users")
                                .document(user.getUid())
                                .set(profile)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this,
                                            "Viyzo account created online.",
                                            Toast.LENGTH_SHORT).show();
                                    speak("Your Viyzo account has been created.");
                                    showFriendSuggestions();
                                })
                                .addOnFailureListener(e2 -> {
                                    Toast.makeText(this,
                                            "Account created, but profile sync failed: " + e2.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                    speak("Account created. Profile sync needs another attempt.");
                                    showFriendSuggestions();
                                });
                    });
        });
        r.addView(create);

        Button back = button("← BACK TO LOGIN");
        back.setOnClickListener(v -> showLogin());
        r.addView(back);

        r.addView(text(
                "Firebase stores the account password securely. Profile details are saved in Firestore.",
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

        // Header: Facebook-like structure, but with Viyzo branding.
        LinearLayout header = row();
        Button menu = button("☰");
        menu.setTextSize(27);
        menu.setOnClickListener(v -> showQuickMenu());
        Button brand = button("VIYZO GO");
        brand.setTextSize(25);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        Button add = button("＋");
        add.setTextSize(25);
        add.setOnClickListener(v -> showCreateMenu());
        Button search = button("⌕");
        search.setTextSize(27);
        search.setOnClickListener(v -> showSearch());
        Button msg = button("●");
        msg.setTextSize(20);
        msg.setOnClickListener(v -> showMessages());
        header.addView(menu, new LinearLayout.LayoutParams(0, dp(58), 0.18f));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(58), 0.42f));
        header.addView(add, new LinearLayout.LayoutParams(0, dp(58), 0.13f));
        header.addView(search, new LinearLayout.LayoutParams(0, dp(58), 0.13f));
        header.addView(msg, new LinearLayout.LayoutParams(0, dp(58), 0.14f));
        r.addView(header);

        // Composer.
        LinearLayout composer = row();
        TextView avatar = text("👤", 30);
        avatar.setGravity(Gravity.CENTER);
        composer.addView(avatar, new LinearLayout.LayoutParams(dp(65), dp(64)));
        Button thought = button("What's on your mind?");
        thought.setGravity(Gravity.CENTER_VERTICAL);
        thought.setOnClickListener(v -> showPostComposer());
        composer.addView(thought, new LinearLayout.LayoutParams(0, dp(64), 1));
        Button gallery = button("▧");
        gallery.setTextSize(22);
        gallery.setOnClickListener(v -> selectPhotoPost());
        composer.addView(gallery, new LinearLayout.LayoutParams(dp(64), dp(64)));
        r.addView(composer);

        LinearLayout media = row();
        Button videoPost = button("🎬 Video");
        videoPost.setOnClickListener(v -> selectVideoForPost());
        Button photoPost = button("🖼️ Photo");
        photoPost.setOnClickListener(v -> selectPhotoPost());
        addRow(media, videoPost, photoPost);
        r.addView(media);

        title(r, "Stories");
        LinearLayout stories = row();
        String[] storyNames = {"＋ Create story", "Viyzo Creator", "Friends", "Trending"};
        for (String name : storyNames) {
            Button story = button(name);
            story.setGravity(Gravity.CENTER);
            stories.addView(story, new LinearLayout.LayoutParams(dp(145), dp(130)));
            if (name.startsWith("＋")) story.setOnClickListener(v -> showStatusCreator());
        }
        r.addView(stories);

        LinearLayout tabs = row();
        Button following = button("Following");
        following.setOnClickListener(v -> toast("Following feed selected."));
        Button forYou = button("For You");
        forYou.setOnClickListener(v -> toast("For You feed selected."));
        addRow(tabs, following, forYou);
        r.addView(tabs);
        Button trending = button("Trending");
        trending.setOnClickListener(v -> toast("Trending feed selected."));
        r.addView(trending);

        // Main video post. The existing VideoView selection/playback flow is retained.
        LinearLayout post = new LinearLayout(this);
        post.setOrientation(LinearLayout.VERTICAL);
        post.setPadding(dp(8), dp(8), dp(8), dp(8));
        post.setBackgroundColor(Color.rgb(30,30,36));

        post.addView(text("👤 " + currentName + "  •  " + postAs, 18));
        post.addView(text(audience, 13));
        post.addView(text("Share your latest Viyzo video with friends and followers.", 16));

        videoView = new VideoView(this);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(430));
        vp.setMargins(0, dp(6), 0, dp(6));
        post.addView(videoView, vp);

        Button select = button("🎬 SELECT / UPLOAD VIDEO");
        select.setOnClickListener(v -> selectVideoForPost());
        post.addView(select);

        LinearLayout actions1 = row();
        Button like = button("❤️ " + likeCount);
        like.setOnClickListener(v -> {
            liked = !liked;
            if (liked) likeCount++; else if (likeCount > 0) likeCount--;
            like.setText("❤️ " + likeCount);
        });
        Button comment = button("💬 " + commentCount);
        comment.setOnClickListener(v -> { showComments(); commentCount++; comment.setText("💬 " + commentCount); });
        addRow(actions1, like, comment);
        post.addView(actions1);

        LinearLayout actions2 = row();
        Button share = button("↗ SHARE " + shareCount);
        share.setOnClickListener(v -> { shareCount++; share.setText("↗ SHARE " + shareCount); shareVideo(); });
        Button save = button("🔖 SAVE");
        save.setOnClickListener(v -> toast("Saved to your saved items."));
        addRow(actions2, share, save);
        Button whatsapp = button("🟢 WhatsApp");
        whatsapp.setOnClickListener(v -> showWhatsAppShareMenu());
        post.addView(whatsapp);
        post.addView(actions2);
        r.addView(post);

        // Creator tools / posting controls.
        Button postAsButton = button("📝 POST AS: " + postAs);
        postAsButton.setOnClickListener(v -> showPostAs());
        r.addView(postAsButton);

        LinearLayout tools1 = row();
        Button friendsBtn = button("👥 Friends " + friendCount + "/5000");
        friendsBtn.setOnClickListener(v -> showFriends());
        Button pageBtn = button("📄 My Page");
        pageBtn.setOnClickListener(v -> showPageManager());
        addRow(tools1, friendsBtn, pageBtn);
        r.addView(tools1);

        LinearLayout tools2 = row();
        Button live = button("🔴 Live");
        live.setOnClickListener(v -> showLiveSetup());
        Button music = button("🎵 Music");
        music.setOnClickListener(v -> showMusicLibrary());
        addRow(tools2, live, music);
        r.addView(tools2);

        LinearLayout tools3 = row();
        Button status = button("🟢 Status 24H");
        status.setOnClickListener(v -> showStatusCreator());
        Button mention = button("＠ Mention / Tag");
        mention.setOnClickListener(v -> showMention());
        addRow(tools3, status, mention);
        r.addView(tools3);

        r.addView(text("Ads + creator earning architecture: new users can receive ads, while creator payout requires verified eligibility and payout setup.", 11));
        addBannerAd(r);

        // FIXED PHONE BOTTOM NAVIGATION:
        // It stays attached to the bottom of the phone and does not scroll with the feed.
        showFixedBottomNavigation();
    }

    private void showFixedBottomNavigation() {
        removeFixedBottomNavigation();

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.rgb(10, 10, 14));
        nav.setPadding(dp(2), dp(2), dp(2), dp(2));

        Button home = navButton("⌂\nHome");
        Button reels = navButton("▶\nReels");
        Button fr = navButton("👥\nFriends");
        Button pages = navButton("▣\nPages");
        Button alerts = navButton("🔔\nAlerts");
        Button profile = navButton("👤\nProfile");

        reels.setOnClickListener(v -> showReels());
        fr.setOnClickListener(v -> showFriends());
        pages.setOnClickListener(v -> showPageManager());
        alerts.setOnClickListener(v -> showNotifications());
        profile.setOnClickListener(v -> showProfile());

        nav.addView(home);
        nav.addView(reels);
        nav.addView(fr);
        nav.addView(pages);
        nav.addView(alerts);
        nav.addView(profile);

        fixedBottomNav = nav;

        android.view.WindowManager.LayoutParams lp =
                new android.view.WindowManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(66),
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        lp.token = getWindow().getDecorView().getWindowToken();

        try {
            getWindowManager().addView(nav, lp);
        } catch (Exception ignored) {
            // Fallback: keep the app usable if the overlay cannot be attached.
        }
    }

    private Button navButton(String label) {
        Button b = button(label);
        b.setTextSize(10);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setSingleLine(false);
        b.setIncludeFontPadding(false);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(60), 1));
        return b;
    }

    private void removeFixedBottomNavigation() {
        if (fixedBottomNav != null) {
            try {
                getWindowManager().removeViewImmediate(fixedBottomNav);
            } catch (Exception ignored) {
            }
            fixedBottomNav = null;
        }
    }

    private void selectVideoForPost() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_VIDEO);
    }

    private void showReels() {
        LinearLayout r = page();
        title(r, "▶ REELS");
        r.addView(text("Short vertical videos will appear here. The same selected video player is used for testing.", 15));
        Button select = button("🎬 SELECT REEL VIDEO");
        select.setOnClickListener(v -> selectVideoForPost());
        r.addView(select);
        addBack(r);
    }

    private void showPostComposer() {
        LinearLayout r = page();
        title(r, "CREATE POST");
        r.addView(text("POST AS: " + postAs, 17));
        EditText body = field("What's on your mind?");
        body.setMinLines(4);
        r.addView(body);
        Button audienceButton = button("👁 AUDIENCE: " + audience);
        audienceButton.setOnClickListener(v -> {
            String[] a = {"Public", "Friends", "Followers", "Only me"};
            new AlertDialog.Builder(this).setTitle("AUDIENCE").setItems(a, (d,w) -> { audience=a[w]; audienceButton.setText("👁 AUDIENCE: "+audience); }).show();
        });
        r.addView(audienceButton);
        Button post = button("PUBLISH POST");
        post.setOnClickListener(v -> { if(body.getText().toString().trim().isEmpty()){toast("Write something first.");return;} toast("Post created locally for prototype."); showHome(); });
        r.addView(post);
        Button video = button("🎬 ADD VIDEO"); video.setOnClickListener(v -> selectVideoForPost()); r.addView(video);
        Button photo = button("🖼️ ADD PHOTO"); photo.setOnClickListener(v -> selectPhotoPost()); r.addView(photo);
        addBack(r);
    }

    private void showCreateMenu() {
        String[] items = {"Create Post", "Upload Video", "Photo Post", "Status 24H", "Go Live", "Create Page"};
        new AlertDialog.Builder(this).setTitle("CREATE").setItems(items, (d,w) -> {
            if(w==0) showPostComposer(); else if(w==1) selectVideoForPost(); else if(w==2) selectPhotoPost(); else if(w==3) showStatusCreator(); else if(w==4) showLiveSetup(); else showPageManager();
        }).show();
    }

    private void showQuickMenu() {
        String[] items = {"Profile", "Friends", "Pages", "Messages", "Notifications", "Dashboard", "Settings & Privacy", "Help & Support", "Logout"};
        new AlertDialog.Builder(this).setTitle("☰ VIYZO MENU").setItems(items, (d,w) -> {
            switch(w){
                case 0:showProfile();break;
                case 1:showFriends();break;
                case 2:showPageManager();break;
                case 3:showMessages();break;
                case 4:showNotifications();break;
                case 5:showMyDashboard();break;
                case 6:showSettings();break;
                case 7:showHelpSupport();break;
                default:
                    firebaseAuth.signOut();
                    currentName = "Viyzo User";
                    currentUsername = "";
                    currentEmail = "";
                    showLogin();
                    break;
            }
        }).show();
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

    /* ================= EXTRA UI SETTINGS ================= */

    private void showHelpSupport() {
        LinearLayout r=page(); title(r,"❓ HELP & SUPPORT");
        r.addView(button("HELP CENTER"));
        r.addView(button("REPORT A PROBLEM"));
        r.addView(button("CONTACT SUPPORT"));
        r.addView(button("ACCOUNT HELP"));
        addBack(r);
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
        r.addView(text("Use the same selected Viyzo video without asking the user to upload it again. The video is uploaded once to Firebase Storage and the status stores its download URL.", 13));

        Button useVideo = button("🎬 USE MY SELECTED VIDEO");
        useVideo.setOnClickListener(v -> {
            if (selectedVideoUri == null) {
                toast("Select a video first from Home.");
                return;
            }
            uploadVideoAsStatus(selectedVideoUri);
        });
        r.addView(useVideo);

        EditText status = field("Write your status (optional)");
        status.setMinLines(3);
        r.addView(status);

        Button photo = button("🖼️ ADD STATUS PHOTO");
        photo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQ_STATUS_PHOTO);
        });
        r.addView(photo);

        Button post = button("POST STATUS FOR 24 HOURS");
        post.setOnClickListener(v -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user == null) { toast("Please log in first."); return; }
            Map<String,Object> data = new HashMap<>();
            data.put("uid", user.getUid());
            data.put("username", currentUsername);
            data.put("text", status.getText().toString().trim());
            data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            data.put("expiresAt", new java.util.Date(System.currentTimeMillis() + 24L*60L*60L*1000L));
            data.put("type", "text");
            firestore.collection("statuses").add(data)
                    .addOnSuccessListener(x -> { toast("Status is live for 24 hours."); speak("Your Viyzo status is live for 24 hours."); })
                    .addOnFailureListener(e -> toast("Status failed: " + e.getMessage()));
        });
        r.addView(post);
        addBack(r);
    }

    private void uploadVideoAsStatus(Uri uri) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) { toast("Please log in first."); return; }
        String uid = user.getUid();
        String fileName = "status_" + System.currentTimeMillis() + ".mp4";
        StorageReference ref = storageRoot.child("statuses/" + uid + "/" + fileName);
        StorageMetadata metadata = new StorageMetadata.Builder().setContentType("video/mp4").build();
        toast("Uploading video to Status...");
        ref.putFile(uri, metadata).continueWithTask((Continuation<com.google.firebase.storage.UploadTask.TaskSnapshot, Task<Uri>>) task -> {
            if (!task.isSuccessful()) throw task.getException();
            return ref.getDownloadUrl();
        }).addOnSuccessListener(downloadUri -> {
            Map<String,Object> data = new HashMap<>();
            data.put("uid", uid);
            data.put("username", currentUsername);
            data.put("mediaUrl", downloadUri.toString());
            data.put("type", "video");
            data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            data.put("expiresAt", new java.util.Date(System.currentTimeMillis() + 24L*60L*60L*1000L));
            firestore.collection("statuses").add(data)
                    .addOnSuccessListener(x -> { toast("Video added to Viyzo Status for 24 hours."); speak("Your video is now in Viyzo Status."); })
                    .addOnFailureListener(e -> toast("Status database save failed: " + e.getMessage()));
        }).addOnFailureListener(e -> toast("Status upload failed: " + e.getMessage()));
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
        if (selectedVideoUri == null) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, "Watch this video on Viyzo Go");
            startActivity(Intent.createChooser(share, "Share Viyzo Video"));
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("video/*");
        share.putExtra(Intent.EXTRA_STREAM, selectedVideoUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { share.setClipData(android.content.ClipData.newRawUri("Viyzo Video", selectedVideoUri)); } catch (Exception ignored) {}
        startActivity(Intent.createChooser(share, "Share Viyzo Video"));
    }

    private void shareToPackage(String packageName, String label) {
        if (selectedVideoUri == null) { toast("Select a video first."); return; }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("video/*");
        share.putExtra(Intent.EXTRA_STREAM, selectedVideoUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { share.setClipData(android.content.ClipData.newRawUri("Viyzo Video", selectedVideoUri)); } catch (Exception ignored) {}
        share.setPackage(packageName);
        try { startActivity(share); }
        catch (Exception e) { toast(label + " is not installed."); }
    }

    private void showWhatsAppShareMenu() {
        String[] items = {"WhatsApp Chat / Status", "WhatsApp Business Chat / Status", "Other apps"};
        new AlertDialog.Builder(this).setTitle("SHARE VIYZO VIDEO").setItems(items, (d,w) -> {
            if (w == 0) shareToPackage("com.whatsapp", "WhatsApp");
            else if (w == 1) shareToPackage("com.whatsapp.w4b", "WhatsApp Business");
            else shareVideo();
        }).show();
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
        r.addView(text("Monetization: AdMob + creator eligibility architecture", 18));
        r.addView(text("Eligible views: " + eligibleViews, 18));
        r.addView(text("Estimated creator earnings: ₹" + String.format(Locale.US, "%.2f", creatorEstimatedEarnings), 18));
        Button earnings = button("💰 OPEN REAL EARNINGS & PAYOUT");
        earnings.setOnClickListener(v -> showEarningsCenter());
        r.addView(earnings);

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
        Button payout = button("🏦 PAYOUT SETTINGS");
        payout.setOnClickListener(v -> showEarningsCenter());
        r.addView(payout);
        r.addView(button("⚠️ Payout is enabled only after verified eligibility and real provider setup."));

        addBack(r);
    }

    private void showEarningsCenter() {
        LinearLayout r = page();
        title(r, "💰 REAL EARNINGS CENTER");
        r.addView(text("This screen is connected to the real backend architecture, but it never invents money. AdMob pays the platform; creator payouts happen only after eligibility and a real payout provider/backend are configured.", 13));
        r.addView(text("Ad revenue source: Google AdMob", 17));
        r.addView(text("Eligible views: " + eligibleViews, 17));
        r.addView(text("Creator estimated earnings: ₹" + String.format(Locale.US, "%.2f", creatorEstimatedEarnings), 17));
        r.addView(text("Payout status: Provider not connected", 17));

        Button refresh = button("🔄 REFRESH MY EARNING RECORD");
        refresh.setOnClickListener(v -> loadEarningsFromFirestore());
        r.addView(refresh);

        Button setup = button("🏦 PAYOUT ACCOUNT SETUP");
        setup.setOnClickListener(v -> toast("Real payout provider keys/account details must be configured on the secure server before money can be sent."));
        r.addView(setup);

        Button admin = button("🛠️ ADMIN REVENUE LEDGER");
        admin.setOnClickListener(v -> showAdminDashboard());
        r.addView(admin);
        addBack(r);
    }

    private void loadEarningsFromFirestore() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) { toast("Please log in first."); return; }
        firestore.collection("creator_earnings").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    Long views = doc.getLong("eligibleViews");
                    Double amount = doc.getDouble("estimatedEarnings");
                    eligibleViews = views == null ? 0 : views;
                    creatorEstimatedEarnings = amount == null ? 0.0 : amount;
                    toast("Earnings record refreshed.");
                    showEarningsCenter();
                })
                .addOnFailureListener(e -> toast("Could not load earnings: " + e.getMessage()));
    }

    private void addBannerAd(LinearLayout r) {
        try {
            bannerAd = new AdView(this);
            bannerAd.setAdSize(AdSize.BANNER);
            // Google demo banner ID. Replace with your real AdMob ad-unit ID before release.
            bannerAd.setAdUnitId("ca-app-pub-3940256099942544/9214589741");
            r.addView(bannerAd, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
            bannerAd.loadAd(new AdRequest.Builder().build());
        } catch (Exception e) {
            Toast.makeText(this, "Ad setup unavailable in this build.", Toast.LENGTH_SHORT).show();
        }
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

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

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
        EditText bio=field("Page bio"); if(!pageBio.isEmpty()) bio.setText(pageBio); r.addView(bio);
        Button photo=button("🖼️ PAGE PROFILE PHOTO"); photo.setOnClickListener(v->selectPagePhoto()); r.addView(photo);
        Button cover=button("🖼️ PAGE COVER PHOTO"); cover.setOnClickListener(v->selectPagePhoto()); r.addView(cover);
        addSettingButton(r,"PAGE FEATURES","Profile photo\nCover photo\nBio\nFollowers\nVideos / Reels\nLive\nComments\nMessages\nNotifications\nManagers / Admin");
        Button save=button("SAVE PAGE"); save.setOnClickListener(v->{pageName=name.getText().toString().trim();pageBio=bio.getText().toString().trim();if(pageName.isEmpty()){toast("Enter a page name");return;}toast("Page saved locally: "+pageName);}); r.addView(save);
        addSettingButton(r,"PAGE FOLLOWERS","Followers: "+followerCount+"\nFollowers are separate from personal friends.");
        addSettingButton(r,"PAGE MONETIZATION","Eligibility\nEarnings\nPayout status\nOnline payout connection will be added later.");
        addBack(r); setContentView(r);
    }

    private void selectPagePhoto() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("image/*"); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(intent,REQ_PROFILE_PHOTO);
    }

    private void showLiveSetup() {
        LinearLayout r=page(); title(r,"🔴 LIVE VIDEO");
        r.addView(text("Live setup is ready as a UI prototype. Real online streaming will be connected with a streaming server later.",14));
        EditText t=field("Live title"); r.addView(t);
        Button aud=button("👁 AUDIENCE: "+audience); aud.setOnClickListener(v->{String[] a={"Public","Friends","Followers","Only me"};new AlertDialog.Builder(this).setTitle("LIVE AUDIENCE").setItems(a,(d,w)->{audience=a[w];aud.setText("👁 AUDIENCE: "+audience);}).show();}); r.addView(aud);
        addSettingButton(r,"LIVE CONTROLS","Camera + microphone\nLive title\nAudience\nLive comments\nReactions\nViewer count\nEnd live\nSave replay");
        Button start=button("🔴 START TEST LIVE"); start.setOnClickListener(v->{toast("Test Live started. Real viewers need the online streaming server.");speak("Live started");}); r.addView(start);
        Button end=button("⏹ END LIVE"); end.setOnClickListener(v->{toast("Live ended.");speak("Live ended");}); r.addView(end);
        addBack(r); setContentView(r);
    }

    private void showSettings() {
        LinearLayout r = page();
        title(r, "⚙️ SETTINGS & PRIVACY");

        Button account = button("🔐 ACCOUNT & PASSWORD");
        account.setOnClickListener(v -> showAccountDetails()); r.addView(account);

        Button privacy = button("🔒 PRIVACY");
        privacy.setOnClickListener(v -> showPrivacySettings()); r.addView(privacy);

        Button security = button("🛡️ SECURITY");
        security.setOnClickListener(v -> showSecuritySettings()); r.addView(security);

        Button notifications = button("🔔 NOTIFICATION SETTINGS");
        notifications.setOnClickListener(v -> showNotificationSettings()); r.addView(notifications);

        Button messages = button("💬 MESSAGE SETTINGS");
        messages.setOnClickListener(v -> showMessageSettings()); r.addView(messages);

        Button follow = button("👥 FOLLOWERS & FOLLOWING");
        follow.setOnClickListener(v -> showFriends()); r.addView(follow);

        Button blocking = button("🚫 BLOCKING");
        blocking.setOnClickListener(v -> showBlockingSettings()); r.addView(blocking);

        Button content = button("🎬 CONTENT PREFERENCES");
        content.setOnClickListener(v -> showContentPreferences()); r.addView(content);

        Button language = button("🌐 LANGUAGE: " + selectedLanguageName);
        language.setOnClickListener(v -> showLanguagePicker(language)); r.addView(language);

        Button data = button("📱 DATA USAGE");
        data.setOnClickListener(v -> showDataUsageSettings()); r.addView(data);

        Button help = button("❓ HELP & SUPPORT");
        help.setOnClickListener(v -> showHelpSupport()); r.addView(help);

        Button terms = button("📄 TERMS & POLICIES");
        terms.setOnClickListener(v -> showTermsPolicies()); r.addView(terms);

        Button delete = button("⚠️ DELETE ACCOUNT");
        delete.setOnClickListener(v -> showDeleteAccountDialog()); r.addView(delete);
        addBack(r);
    }

    private void showPrivacySettings() {
        LinearLayout r=page(); title(r,"🔒 PRIVACY");
        Button p=button("PRIVATE ACCOUNT: " + (privateAccount?"ON":"OFF"));
        p.setOnClickListener(v->{privateAccount=!privateAccount;p.setText("PRIVATE ACCOUNT: "+(privateAccount?"ON":"OFF"));}); r.addView(p);
        Button whoMessage = button("WHO CAN MESSAGE ME"); whoMessage.setOnClickListener(v -> showMessageSettings()); r.addView(whoMessage);
        addSettingButton(r,"PROFILE VISIBILITY","Public profile\nFriends\nFollowers");
        addSettingButton(r,"ACTIVITY VISIBILITY","Online status\nLikes\nComments");
        addBack(r);
    }

    private void showSecuritySettings() {
        LinearLayout r=page(); title(r,"🛡️ SECURITY");
        addSettingButton(r,"LOGIN ALERTS","New login alerts\nDevice alerts");
        addSettingButton(r,"TWO-STEP VERIFICATION","SMS or authenticator will be connected with the online backend later.");
        addSettingButton(r,"ACTIVE SESSIONS","Current device\nOther sessions will appear after online authentication.");
        addSettingButton(r,"SECURITY CHECKUP","Password\nLogin activity\nRecovery details");
        addBack(r);
    }

    private void showNotificationSettings() {
        LinearLayout r=page(); title(r,"🔔 NOTIFICATION SETTINGS");
        Button n=button("NOTIFICATIONS: "+(notificationsOn?"ON":"OFF")); n.setOnClickListener(v->{notificationsOn=!notificationsOn;n.setText("NOTIFICATIONS: "+(notificationsOn?"ON":"OFF"));}); r.addView(n);
        addSettingButton(r,"LIKES","Receive like notifications");
        addSettingButton(r,"COMMENTS","Receive comment notifications");
        addSettingButton(r,"FOLLOWERS","Receive follower notifications");
        addSettingButton(r,"MESSAGES","Receive message notifications");
        addBack(r);
    }

    private void showMessageSettings() {
        LinearLayout r=page(); title(r,"💬 MESSAGE SETTINGS");
        Button n=button("MESSAGE REQUESTS: "+(messageRequestsOn?"ON":"OFF")); n.setOnClickListener(v->{messageRequestsOn=!messageRequestsOn;n.setText("MESSAGE REQUESTS: "+(messageRequestsOn?"ON":"OFF"));}); r.addView(n);
        addSettingButton(r,"WHO CAN MESSAGE YOU","Friends\nFollowers\nEveryone");
        addSettingButton(r,"READ RECEIPTS","On\nOff");
        addSettingButton(r,"MESSAGE REQUESTS","Allow\nDon't allow");
        addBack(r);
    }

    private void showBlockingSettings() {
        LinearLayout r=page(); title(r,"🚫 BLOCKING");
        r.addView(button("BLOCKED USERS"));
        r.addView(button("BLOCKED MESSAGES"));
        r.addView(button("BLOCKED VIDEOS"));
        addBack(r);
    }

    private void showContentPreferences() {
        LinearLayout r=page(); title(r,"🎬 CONTENT PREFERENCES");
        addSettingButton(r,"RECOMMENDATIONS","For You\nFollowing\nTrending");
        addSettingButton(r,"SENSITIVE CONTENT","Standard\nLess\nMore");
        addSettingButton(r,"TOPICS","Video\nMusic\nSports\nNews\nComedy");
        addSettingButton(r,"NOT INTERESTED","Hide this topic\nHide this creator");
        addBack(r);
    }

    private void showDataUsageSettings() {
        LinearLayout r=page(); title(r,"📱 DATA USAGE");
        Button ds=button("DATA SAVER: "+(dataSaver?"ON":"OFF")); ds.setOnClickListener(v->{dataSaver=!dataSaver;ds.setText("DATA SAVER: "+(dataSaver?"ON":"OFF"));}); r.addView(ds);
        Button ap=button("AUTOPLAY: "+(autoplay?"ON":"OFF")); ap.setOnClickListener(v->{autoplay=!autoplay;ap.setText("AUTOPLAY: "+(autoplay?"ON":"OFF"));}); r.addView(ap);
        Button q=button("VIDEO DISPLAY: "+videoMode); q.setOnClickListener(v->{String[] a={"FULL WIDTH","FIT","FILL / CROP"};new AlertDialog.Builder(this).setTitle("VIDEO DISPLAY").setItems(a,(d,w)->{videoMode=a[w];q.setText("VIDEO DISPLAY: "+videoMode);}).show();}); r.addView(q);
        addSettingButton(r,"WI-FI ONLY","Video upload\nVideo playback\nDownloads");
        addBack(r);
    }

    private void showTermsPolicies() {
        LinearLayout r=page(); title(r,"📄 TERMS & POLICIES");
        addSettingButton(r,"TERMS OF SERVICE","Viyzo terms will be finalized before public release.");
        addSettingButton(r,"PRIVACY POLICY","Privacy policy and data handling will be finalized before public release.");
        addSettingButton(r,"COMMUNITY GUIDELINES","Safety, harassment, spam, illegal content and other moderation rules.");
        addSettingButton(r,"CREATOR POLICY","Original content, rights, monetization and payout rules.");
        addBack(r);
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
                    if (firebaseAuth.getCurrentUser() != null) {
                        firebaseAuth.signOut();
                    }
                    Toast.makeText(this, "Local test data deleted.", Toast.LENGTH_SHORT).show();
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
