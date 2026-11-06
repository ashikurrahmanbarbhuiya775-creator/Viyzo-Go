package com.viyzo.mediahub;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.webkit.JavascriptInterface;
import java.util.ArrayList;
import android.graphics.Color;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.Gravity;

public class MainActivity extends Activity {
    private WebView webView;
    private ProgressBar progress;
    private PermissionRequest pendingPermissionRequest;
    private SpeechRecognizer speechRecognizer;
    private boolean waitingForVoicePermission = false;
    private String requestedSpeechLanguage = "hi-IN";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(11, 16, 32));
        getWindow().setNavigationBarColor(Color.rgb(11, 16, 32));
        getWindow().getDecorView().setSystemUiVisibility(0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(11, 16, 32));
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        webView.setBackgroundColor(Color.rgb(11, 16, 32));
        webView.addJavascriptInterface(new VoiceBridge(), "ViyzoVoice");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP);
        root.addView(progress, progressParams);

        TextView offline = new TextView(this);
        offline.setText("VIYZO MEDIA HUB\n\nCould not open the demo screen. Close and reopen the app.");
        offline.setTextColor(Color.WHITE);
        offline.setTextSize(16);
        offline.setGravity(Gravity.CENTER);
        offline.setVisibility(View.GONE);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean asksForAudio = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) asksForAudio = true;
                    }
                    if (!asksForAudio) { request.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        pendingPermissionRequest = request;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 4102);
                    }
                });
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                                                   android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) offline.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                offline.setVisibility(View.GONE);
            }
        });
        root.addView(offline, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 4102 && pendingPermissionRequest != null) {
            PermissionRequest request = pendingPermissionRequest;
            pendingPermissionRequest = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                request.deny();
            }
        }
        if (requestCode == 4103 && waitingForVoicePermission) {
            waitingForVoicePermission = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startNativeVoiceRecognition();
            else sendVoiceResult("", "microphone_permission_denied");
        }
    }

    private final class VoiceBridge {
        @JavascriptInterface public void start(String languageTag) {
            requestedSpeechLanguage = languageTag == null || languageTag.isEmpty() ? "hi-IN" : languageTag;
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    waitingForVoicePermission = true;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 4103);
                } else {
                    startNativeVoiceRecognition();
                }
            });
        }
        @JavascriptInterface public void stop() {
            runOnUiThread(() -> {
                if (speechRecognizer != null) speechRecognizer.stopListening();
            });
        }
    }

    private void startNativeVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendVoiceResult("", "speech_recognition_unavailable");
            return;
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { sendVoiceStatus("listening"); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { sendVoiceStatus("processing"); }
            @Override public void onError(int error) { sendVoiceResult("", "recognition_error_" + error); }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                sendVoiceResult(matches == null || matches.isEmpty() ? "" : matches.get(0), "");
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) sendVoicePartial(matches.get(0));
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, requestedSpeechLanguage);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    private void sendVoiceStatus(String status) {
        if (webView != null) webView.post(() -> webView.evaluateJavascript(
            "window.onViyzoVoiceStatus && window.onViyzoVoiceStatus('" + status + "')", null));
    }

    private void sendVoicePartial(String text) {
        try {
            String safe = org.json.JSONObject.quote(text);
            if (webView != null) webView.post(() -> webView.evaluateJavascript(
                "window.onViyzoVoicePartial && window.onViyzoVoicePartial(" + safe + ")", null));
        } catch (Exception ignored) { }
    }

    private void sendVoiceResult(String text, String error) {
        try {
            String safeText = org.json.JSONObject.quote(text);
            String safeError = org.json.JSONObject.quote(error);
            if (webView != null) webView.post(() -> webView.evaluateJavascript(
                "window.onViyzoVoiceResult && window.onViyzoVoiceResult(" + safeText + "," + safeError + ")", null));
        } catch (Exception ignored) { }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
