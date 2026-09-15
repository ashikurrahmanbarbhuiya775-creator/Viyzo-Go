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
import android.speech.tts.TextToSpeech;
import android.app.DatePickerDialog;
import java.util.Calendar;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends Activity {
    private VideoView videoView;
    private int likeCount = 0;
    private boolean liked = false;
    private TextToSpeech tts;
    private Locale selectedVoiceLocale = Locale.ENGLISH;
    private String selectedLanguageName = "English";

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(8), dp(6), dp(8), dp(6));
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
        root.setPadding(dp(10), dp(10), dp(10), dp(12));
        root.setBackgroundColor(Color.rgb(18,18,22));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        return root;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(selectedVoiceLocale);
            }
        });
        showLogin();
    }

    @Override protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private void speak(String message) {
        if (tts == null || message == null || message.isEmpty()) return;
        int result = tts.setLanguage(selectedVoiceLocale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH);
        }
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "VIYZO_GUIDE");
    }

    private Button voiceButton(String message) {
        Button b = button("🔊 LISTEN");
        b.setOnClickListener(v -> speak(message));
        return b;
    }

    private void showLogin() {
        LinearLayout root = page();
        TextView logo = text("VIYZO GO", 34);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(logo);

        TextView welcome = text("WELCOME TO VIYZO GO", 20);
        welcome.setGravity(Gravity.CENTER);
        root.addView(welcome);

        EditText email = new EditText(this);
        email.setHint("Email");
        email.setTextColor(Color.WHITE);
        email.setHintTextColor(Color.GRAY);
        email.setInputType(33);
        root.addView(email);

        EditText password = new EditText(this);
        password.setHint("Password");
        password.setTextColor(Color.WHITE);
        password.setHintTextColor(Color.GRAY);
        password.setInputType(129);
        root.addView(password);

        Button login = button("LOGIN");
        login.setOnClickListener(v -> {
            if (email.getText().toString().trim().isEmpty()
                    || password.getText().toString().isEmpty()) {
                Toast.makeText(this, "Email and password required", Toast.LENGTH_SHORT).show();
                return;
            }
            showHome();
        });
        root.addView(login);

        Button create = button("CREATE NEW ACCOUNT");
        create.setOnClickListener(v -> showSignup());
        root.addView(create);

        Button forgot = button("FORGOT PASSWORD");
        forgot.setOnClickListener(v ->
                Toast.makeText(this, "Password reset - TEST", Toast.LENGTH_SHORT).show());
        root.addView(forgot);

        TextView note = text("TEST LOGIN — Firebase will be connected later", 12);
        note.setGravity(Gravity.CENTER);
        root.addView(note);
    }

    private void showSignup() {
        LinearLayout root = page();
        TextView title = text("CREATE VIYZO ACCOUNT", 26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        root.addView(voiceButton("Welcome to Viyzo Go. We will create your account step by step."));

        TextView typeLabel = text("ACCOUNT TYPE", 16);
        root.addView(typeLabel);
        Button accountType = button("👤 PERSONAL ACCOUNT");
        final String[] chosenType = {"Personal Account"};
        accountType.setOnClickListener(v -> {
            String[] types = {"Personal Account", "Creator Account"};
            new AlertDialog.Builder(this)
                    .setTitle("ACCOUNT TYPE")
                    .setSingleChoiceItems(types, 0, (d, which) -> {
                        chosenType[0] = types[which];
                        accountType.setText(which == 0 ? "👤 PERSONAL ACCOUNT" : "🎬 CREATOR ACCOUNT");
                        speak("Selected " + types[which]);
                        d.dismiss();
                    }).show();
        });
        root.addView(accountType);

        EditText name = new EditText(this);
        name.setHint("Full name");
        name.setTextColor(Color.WHITE); name.setHintTextColor(Color.GRAY);
        root.addView(name);
        root.addView(voiceButton("Enter your full name."));
        name.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) speak("Enter your full name."); });

        EditText username = new EditText(this);
        username.setHint("Username");
        username.setTextColor(Color.WHITE); username.setHintTextColor(Color.GRAY);
        root.addView(username);
        root.addView(voiceButton("Choose a username for your Viyzo profile."));
        username.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) speak("Choose a username for your Viyzo profile."); });

        EditText email = new EditText(this);
        email.setHint("Email");
        email.setTextColor(Color.WHITE); email.setHintTextColor(Color.GRAY);
        email.setInputType(33);
        root.addView(email);
        root.addView(voiceButton("Enter your email address."));

        EditText phone = new EditText(this);
        phone.setHint("Mobile number");
        phone.setTextColor(Color.WHITE); phone.setHintTextColor(Color.GRAY);
        phone.setInputType(2);
        root.addView(phone);
        root.addView(voiceButton("Enter your mobile number."));

        TextView dob = text("🎂 Date of birth: Not selected", 16);
        root.addView(dob);
        Button dobButton = button("📅 SELECT DATE OF BIRTH");
        dobButton.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
                dob.setText("🎂 Date of birth: " + day + "/" + (month + 1) + "/" + year);
                dob.setTag(year + "-" + (month + 1) + "-" + day);
                speak("Date of birth selected.");
            }, 2000, 0, 1);
            picker.getDatePicker().setMaxDate(now.getTimeInMillis());
            picker.show();
            speak("Choose your date of birth.");
        });
        root.addView(dobButton);

        Button gender = button("⚧ SELECT GENDER (OPTIONAL)");
        final String[] chosenGender = {"Not specified"};
        gender.setOnClickListener(v -> {
            String[] options = {"Woman", "Man", "Non-binary", "Prefer not to say"};
            new AlertDialog.Builder(this).setTitle("GENDER")
                    .setSingleChoiceItems(options, -1, (d, which) -> {
                        chosenGender[0] = options[which];
                        gender.setText("⚧ " + options[which]);
                        d.dismiss();
                    }).show();
        });
        root.addView(gender);

        EditText password = new EditText(this);
        password.setHint("Password");
        password.setTextColor(Color.WHITE); password.setHintTextColor(Color.GRAY);
        password.setInputType(129); root.addView(password);
        root.addView(voiceButton("Create a password with at least eight characters."));

        EditText confirm = new EditText(this);
        confirm.setHint("Confirm password");
        confirm.setTextColor(Color.WHITE); confirm.setHintTextColor(Color.GRAY);
        confirm.setInputType(129); root.addView(confirm);
        root.addView(voiceButton("Enter the same password again."));

        Button language = button("🌐 LANGUAGE: " + selectedLanguageName);
        language.setOnClickListener(v -> showLanguagePicker(language));
        root.addView(language);
        root.addView(voiceButton("Choose the language you want Viyzo Go to use."));

        Button create = button("CREATE ACCOUNT");
        create.setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()
                    || username.getText().toString().trim().isEmpty()
                    || email.getText().toString().trim().isEmpty()
                    || phone.getText().toString().trim().isEmpty()
                    || dob.getTag() == null
                    || password.getText().toString().isEmpty()
                    || confirm.getText().toString().isEmpty()) {
                Toast.makeText(this, "Please complete all required fields", Toast.LENGTH_SHORT).show();
                speak("Please complete all required fields.");
                return;
            }
            if (!password.getText().toString().equals(confirm.getText().toString())) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                speak("The passwords do not match.");
                return;
            }
            if (password.getText().toString().length() < 8) {
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
                speak("Your password must be at least eight characters.");
                return;
            }
            if (!isOldEnough((String) dob.getTag(), 13)) {
                Toast.makeText(this, "You must be at least 13 years old", Toast.LENGTH_SHORT).show();
                speak("You must be at least thirteen years old to create this test account.");
                return;
            }
            speak("Your Viyzo Go account is ready. Next we will show people you can follow.");
            showFriendSuggestions(name.getText().toString().trim(), chosenType[0], chosenGender[0]);
        });
        root.addView(create);

        Button back = button("BACK TO LOGIN");
        back.setOnClickListener(v -> showLogin());
        root.addView(back);
    }

    private boolean isOldEnough(String value, int minimumAge) {
        try {
            String[] p = value.split("-");
            Calendar dob = Calendar.getInstance();
            dob.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            Calendar today = Calendar.getInstance();
            int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--;
            return age >= minimumAge;
        } catch (Exception e) {
            return false;
        }
    }

    private void showFriendSuggestions(String name, String accountType, String gender) {
        LinearLayout root = page();
        TextView title = text("👥 FIND FRIENDS", 26);
        title.setGravity(Gravity.CENTER); root.addView(title);
        root.addView(voiceButton("Your account has been created. Choose people you want to follow, or skip this step."));
        root.addView(text("Welcome, " + name + "!", 19));
        root.addView(text("Account: " + accountType, 15));
        root.addView(text("Gender: " + gender, 14));
        String[] suggestions = {"Viyzo Creator", "Music Fans", "Travel Videos", "Comedy Videos", "Sports Videos"};
        for (String item : suggestions) {
            Button follow = button("➕ FOLLOW  " + item);
            follow.setOnClickListener(v -> {
                follow.setText("✓ FOLLOWING  " + item);
                speak("You are now following " + item + ".");
            });
            root.addView(follow);
        }
        Button skip = button("SKIP FOR NOW");
        skip.setOnClickListener(v -> showHome()); root.addView(skip);
        Button done = button("DONE — GO TO HOME");
        done.setOnClickListener(v -> showHome()); root.addView(done);
    }

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
        Collections.sort(order, (a, b) -> names.get(a).compareToIgnoreCase(names.get(b)));
        String[] display = new String[order.size()];
        Locale[] sortedLocales = new Locale[order.size()];
        for (int i = 0; i < order.size(); i++) {
            display[i] = names.get(order.get(i));
            sortedLocales[i] = usable.get(order.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle("🌐 CHOOSE LANGUAGE")
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

    private void showHome() {
        LinearLayout root = page();
        TextView logo = text("VIYZO GO",30);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(logo);

        TextView sub = text("VIDEO • FRIENDS • MESSAGES • CREATOR",13);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        videoView = new VideoView(this);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(230));
        vp.setMargins(0,dp(8),0,dp(8));
        root.addView(videoView,vp);

        Button select = button("🎬 SELECT / UPLOAD VIDEO");
        select.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent,100);
        });
        root.addView(select);

        LinearLayout r1 = row();
        Button like = button("❤️ LIKE 0");
        like.setOnClickListener(v -> {
            liked = !liked;
            if(liked) likeCount++; else if(likeCount>0) likeCount--;
            like.setText((liked ? "❤️ LIKED " : "❤️ LIKE ") + likeCount);
        });
        Button comment = button("💬 COMMENT");
        comment.setOnClickListener(v -> showComments());
        addRow(r1,like,comment); root.addView(r1);

        LinearLayout r2 = row();
        Button share = button("🔗 SHARE"); share.setOnClickListener(v -> shareVideo());
        Button msg = button("💬 MESSAGE"); msg.setOnClickListener(v -> showMessages());
        addRow(r2,share,msg); root.addView(r2);

        LinearLayout r3 = row();
        Button profile = button("👤 PROFILE"); profile.setOnClickListener(v -> showProfile());
        Button friends = button("👥 FRIENDS"); friends.setOnClickListener(v -> showFriends());
        addRow(r3,profile,friends); root.addView(r3);

        LinearLayout r4 = row();
        Button notif = button("🔔 NOTIFICATIONS"); notif.setOnClickListener(v -> showNotifications());
        Button search = button("🔎 SEARCH"); search.setOnClickListener(v -> showSearch());
        addRow(r4,notif,search); root.addView(r4);

        LinearLayout r5 = row();
        Button dash = button("📊 MY DASHBOARD"); dash.setOnClickListener(v -> showMyDashboard());
        Button settings = button("⚙️ SETTINGS"); settings.setOnClickListener(v -> showSettings());
        addRow(r5,dash,settings); root.addView(r5);

        LinearLayout r6 = row();
        Button report = button("🚨 REPORT / BLOCK"); report.setOnClickListener(v -> showReport());
        Button admin = button("🛠️ ADMIN"); admin.setOnClickListener(v -> showAdminDashboard());
        addRow(r6,report,admin); root.addView(r6);

        Button logout = button("🚪 LOGOUT");
        logout.setOnClickListener(v -> Toast.makeText(this,"Logout test",Toast.LENGTH_SHORT).show());
        root.addView(logout);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private void addRow(LinearLayout r, Button a, Button b) {
        r.addView(a,new LinearLayout.LayoutParams(0,dp(55),1));
        r.addView(b,new LinearLayout.LayoutParams(0,dp(55),1));
    }

    private void showComments() {
        EditText input = new EditText(this);
        input.setHint("Write a comment");
        new AlertDialog.Builder(this)
                .setTitle("💬 COMMENTS")
                .setMessage("Comment • Reply • Like • Delete • Report")
                .setView(input)
                .setPositiveButton("POST",(d,w)->Toast.makeText(this,"Comment posted - TEST",Toast.LENGTH_SHORT).show())
                .setNegativeButton("CANCEL",null).show();
    }

    private void shareVideo() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT,"Watch this video on Viyzo Go");
        startActivity(Intent.createChooser(share,"Share Viyzo Video"));
    }

    private void showMessages() {
        LinearLayout r=page(); r.addView(text("💬 MESSAGES",28));
        r.addView(button("➕ NEW MESSAGE")); r.addView(button("USER 1"));
        r.addView(button("USER 2")); r.addView(button("📩 MESSAGE REQUESTS")); addBack(r);
    }

    private void showNotifications() {
        LinearLayout r=page(); r.addView(text("🔔 NOTIFICATIONS",28));
        r.addView(text("❤️ Someone liked your video",17));
        r.addView(text("👤 Someone followed you",17));
        r.addView(text("💬 New comment received",17));
        r.addView(text("💬 New message received",17)); addBack(r);
    }

    private void showProfile() {
        LinearLayout r=page(); r.addView(text("👤 MY PROFILE",28));
        r.addView(text("Name: Viyzo User",18)); r.addView(text("Followers: 0",18));
        r.addView(text("Following: 0",18)); r.addView(text("Likes Received: "+likeCount,18));
        r.addView(button("✏️ EDIT PROFILE")); r.addView(button("🎬 MY VIDEOS")); addBack(r);
    }

    private void showFriends() {
        LinearLayout r=page(); r.addView(text("👥 FRIENDS / FOLLOWERS",26));
        r.addView(button("👥 FRIENDS")); r.addView(button("👤 FOLLOWERS"));
        r.addView(button("➡️ FOLLOWING")); r.addView(button("📩 FRIEND REQUESTS"));
        r.addView(button("🚫 BLOCKED USERS")); addBack(r);
    }

    private void showSearch() {
        LinearLayout r=page(); r.addView(text("🔎 SEARCH USERS",26));
        EditText s=new EditText(this); s.setHint("Search name or username");
        s.setTextColor(Color.WHITE); s.setHintTextColor(Color.GRAY); r.addView(s);
        Button b=button("🔎 SEARCH"); b.setOnClickListener(v->Toast.makeText(this,"Search test",Toast.LENGTH_SHORT).show());
        r.addView(b); addBack(r);
    }

    private void showMyDashboard() {
        LinearLayout r=page(); r.addView(text("📊 MY DASHBOARD",28));
        r.addView(text("Video Views: 0",18)); r.addView(text("Total Likes: "+likeCount,18));
        r.addView(text("Comments: 0",18)); r.addView(text("Shares: 0",18));
        r.addView(text("Followers: 0",18)); r.addView(text("Following: 0",18));
        r.addView(text("Creator Earnings: ₹0",18)); r.addView(button("📈 ANALYTICS")); addBack(r);
    }

    private void showSettings() {
        LinearLayout r = page();
        r.addView(text("⚙️ SETTINGS & PRIVACY",26));

        Button account = button("🔐 ACCOUNT & PASSWORD");
        account.setOnClickListener(v -> showSettingInfo("ACCOUNT & PASSWORD",
                "Change password\nChange email\nEdit account name\nLogin activity"));
        r.addView(account);

        Button privacy = button("🔒 PRIVACY");
        privacy.setOnClickListener(v -> showSettingInfo("PRIVACY",
                "Profile privacy\nWho can message you\nPrivate account\nActivity visibility"));
        r.addView(privacy);

        Button security = button("🛡️ SECURITY");
        security.setOnClickListener(v -> showSettingInfo("SECURITY",
                "Login alerts\nTwo-step verification\nActive sessions\nSecurity checkup"));
        r.addView(security);

        Button notifications = button("🔔 NOTIFICATION SETTINGS");
        notifications.setOnClickListener(v -> showSettingInfo("NOTIFICATION SETTINGS",
                "Likes\nComments\nFollowers\nMessages\nCreator notifications"));
        r.addView(notifications);

        Button messages = button("💬 MESSAGE SETTINGS");
        messages.setOnClickListener(v -> showSettingInfo("MESSAGE SETTINGS",
                "Message requests\nWho can message you\nRead receipts\nBlocked messages"));
        r.addView(messages);

        Button followers = button("👥 FOLLOWERS & FOLLOWING");
        followers.setOnClickListener(v -> showSettingInfo("FOLLOWERS & FOLLOWING",
                "Manage followers\nFollowing list\nFriend requests\nRemove follower"));
        r.addView(followers);

        Button blocking = button("🚫 BLOCKING");
        blocking.setOnClickListener(v -> showSettingInfo("BLOCKING",
                "Blocked users\nBlocked messages\nBlocked videos"));
        r.addView(blocking);

        Button content = button("🎬 CONTENT PREFERENCES");
        content.setOnClickListener(v -> showSettingInfo("CONTENT PREFERENCES",
                "Recommendations\nSensitive content\nTopics\nNot interested"));
        r.addView(content);

        Button language = button("🌐 LANGUAGE");
        language.setOnClickListener(v -> showLanguageDialog());
        r.addView(language);

        Button data = button("📱 DATA USAGE");
        data.setOnClickListener(v -> showSettingInfo("DATA USAGE",
                "Data saver\nVideo quality\nAutoplay\nWi-Fi only"));
        r.addView(data);

        Button help = button("❓ HELP & SUPPORT");
        help.setOnClickListener(v -> showSettingInfo("HELP & SUPPORT",
                "Help Center\nReport a problem\nContact support\nAccount help"));
        r.addView(help);

        Button terms = button("📄 TERMS & POLICIES");
        terms.setOnClickListener(v -> showSettingInfo("TERMS & POLICIES",
                "Terms\nPrivacy Policy\nCommunity Guidelines\nCreator Policy"));
        r.addView(terms);

        Button delete = button("⚠️ DELETE ACCOUNT");
        delete.setOnClickListener(v -> showDeleteAccountDialog());
        r.addView(delete);

        addBack(r);
    }

    private void showSettingInfo(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showLanguageDialog() {
        String[] languages = {"English", "Hindi", "বাংলা", "Urdu"};
        new AlertDialog.Builder(this)
                .setTitle("🌐 LANGUAGE")
                .setSingleChoiceItems(languages, 0, (dialog, which) -> {
                    Toast.makeText(this, "Language selected: " + languages[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ DELETE ACCOUNT")
                .setMessage("This is a test screen. Real account deletion will be connected later.")
                .setPositiveButton("DELETE TEST", (d, w) ->
                        Toast.makeText(this, "Delete account - TEST", Toast.LENGTH_SHORT).show())
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showReport() {
        String[] options={"Report User","Report Video","Spam","Harassment","Copyright","Block User"};
        new AlertDialog.Builder(this).setTitle("🚨 REPORT / BLOCK").setItems(options,
                (d,w)->Toast.makeText(this,options[w],Toast.LENGTH_SHORT).show())
                .setNegativeButton("CANCEL",null).show();
    }

    private void showAdminDashboard() {
        LinearLayout r=page(); r.addView(text("🛠️ ADMIN DASHBOARD",28));
        r.addView(text("Users: 0",18)); r.addView(text("Videos: 0",18));
        r.addView(text("Reports: 0",18)); r.addView(text("Active Users: 0",18));
        r.addView(button("👥 MANAGE USERS")); r.addView(button("🎬 MANAGE VIDEOS"));
        r.addView(button("🚨 MANAGE REPORTS")); r.addView(button("⚖️ DISPUTES"));
        r.addView(button("💬 MESSAGE MODERATION")); r.addView(button("⭐ CREATOR MANAGEMENT"));
        r.addView(button("💰 CREATOR EARNINGS")); r.addView(button("📢 ADS MANAGEMENT"));
        r.addView(button("📈 APP ANALYTICS")); r.addView(button("⚙️ APP SETTINGS")); addBack(r);
    }

    private void addBack(LinearLayout r) {
        Button b=button("← BACK TO HOME"); b.setOnClickListener(v->showHome()); r.addView(b);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==100 && resultCode==RESULT_OK && data!=null) {
            Uri videoUri=data.getData();
            if(videoUri!=null) {
                try { getContentResolver().takePersistableUriPermission(videoUri,Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch(Exception ignored) {}
                MediaController controller=new MediaController(this);
                controller.setAnchorView(videoView);
                videoView.setMediaController(controller);
                videoView.setVideoURI(videoUri);
                videoView.setOnPreparedListener(mp->{ mp.setLooping(true); videoView.start(); });
                videoView.requestFocus();
            }
        }
    }
}
