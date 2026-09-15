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
import android.widget.DatePicker;
import android.app.DatePickerDialog;
import android.speech.tts.TextToSpeech;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import java.util.Locale;
import android.widget.MediaController;

public class MainActivity extends Activity {

    private TextToSpeech tts;
    private AccountDb db;
    private String currentName = "Viyzo User";
    private String currentUsername = "";
    private String currentDob = "";
    private VideoView videoView;
    private int likeCount = 0;
    private boolean liked = false;

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
        showLogin();
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

        EditText name = field("Full name"); root.addView(name);
        EditText username = field("Username"); root.addView(username);
        EditText email = field("Email"); email.setInputType(33); root.addView(email);
        EditText password = field("Password"); password.setInputType(129); root.addView(password);
        EditText confirm = field("Confirm password"); confirm.setInputType(129); root.addView(confirm);

        Button dob = button("📅 DATE OF BIRTH");
        dob.setOnClickListener(v -> {
            DatePickerDialog d = new DatePickerDialog(this, (view, y, m, day) -> {
                currentDob = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day);
                dob.setText("📅 DOB: " + currentDob);
            }, 2000, 0, 1);
            d.show();
            speak("Please select your date of birth");
        });
        root.addView(dob);

        Button voice = button("🔊 VOICE HELP");
        voice.setOnClickListener(v -> speak("Enter your name, username, email, password, and date of birth"));
        root.addView(voice);

        Button create = button("CREATE ACCOUNT");
        create.setOnClickListener(v -> {
            String n=name.getText().toString().trim(), u=username.getText().toString().trim();
            String e=email.getText().toString().trim(), pw=password.getText().toString();
            if(n.isEmpty()||u.isEmpty()||e.isEmpty()||pw.isEmpty()||currentDob.isEmpty()) { Toast.makeText(this,"Please fill all fields",Toast.LENGTH_SHORT).show(); return; }
            if(!pw.equals(confirm.getText().toString())) { Toast.makeText(this,"Passwords do not match",Toast.LENGTH_SHORT).show(); return; }
            if(db.usernameExists(u) || db.emailExists(e)) { Toast.makeText(this,"Username or email already exists",Toast.LENGTH_SHORT).show(); return; }
            db.createAccount(n,u,e,pw,currentDob,Locale.getDefault().toLanguageTag());
            currentName=n; currentUsername=u;
            Toast.makeText(this,"Account created",Toast.LENGTH_SHORT).show();
            speak("Your Viyzo Go account has been created");
            showHome();
        });
        root.addView(create);
        Button back = button("BACK TO LOGIN"); back.setOnClickListener(v -> showLogin()); root.addView(back);
    }

    private EditText field(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); return e;
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

        Button language = button("🌐 APP LANGUAGE / ALL LANGUAGES");
        language.setOnClickListener(v -> showAllLanguages());
        root.addView(language);

        Button account = button("👤 ACCOUNT: " + currentName);
        account.setOnClickListener(v -> showAccountDetails());
        root.addView(account);

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

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "viyzo_voice");
    }

    private void showAccountDetails() {
        new AlertDialog.Builder(this).setTitle("👤 ACCOUNT")
            .setMessage("Name: " + currentName + "\nUsername: " + currentUsername + "\nDOB: " + currentDob)
            .setPositiveButton("OK", null).show();
    }

    private void showAllLanguages() {
        Locale[] locales = Locale.getAvailableLocales();
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for(Locale l: locales) { String tag=l.toLanguageTag(); if(!tag.isEmpty() && seen.add(tag)) list.add(tag + " — " + l.getDisplayName(l)); }
        java.util.Collections.sort(list);
        String[] arr=list.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("🌐 LANGUAGES")
            .setItems(arr,(d,w)->{ String tag=arr[w].split(" — ")[0]; Locale chosen=Locale.forLanguageTag(tag); Locale.setDefault(chosen); if(tts!=null) tts.setLanguage(chosen); Toast.makeText(this,"Selected: "+chosen.getDisplayName(),Toast.LENGTH_SHORT).show(); speak("Language selected"); })
            .setNegativeButton("CANCEL",null).show();
    }

    private static class AccountDb extends SQLiteOpenHelper {
        AccountDb(Activity c){ super(c,"viyzo_accounts.db",null,1); }
        public void onCreate(SQLiteDatabase d){ d.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,username TEXT UNIQUE,email TEXT UNIQUE,password TEXT,dob TEXT,language TEXT,created_at INTEGER)"); }
        public void onUpgrade(SQLiteDatabase d,int a,int b){ d.execSQL("DROP TABLE IF EXISTS accounts"); onCreate(d); }
        boolean usernameExists(String u){ return exists("username",u); }
        boolean emailExists(String e){ return exists("email",e); }
        boolean exists(String col,String val){ SQLiteDatabase d=getReadableDatabase(); android.database.Cursor c=d.rawQuery("SELECT 1 FROM accounts WHERE "+col+"=? LIMIT 1",new String[]{val}); boolean x=c.moveToFirst(); c.close(); return x; }
        void createAccount(String n,String u,String e,String p,String dob,String lang){ ContentValues v=new ContentValues(); v.put("name",n);v.put("username",u);v.put("email",e);v.put("password",p);v.put("dob",dob);v.put("language",lang);v.put("created_at",System.currentTimeMillis());getWritableDatabase().insertOrThrow("accounts",null,v); }
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
