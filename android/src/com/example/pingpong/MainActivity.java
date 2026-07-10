package com.example.pingpong;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private volatile boolean listening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new SpeechBridge(), "AndroidSpeech");
        webView.addJavascriptInterface(new LogBridge(), "AndroidLog");
        webView.loadUrl("file:///android_asset/pingpong.html");
        setContentView(webView);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
        setupRecognizer();
    }

    private void setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        if (recognizer != null) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { notifyState("listening"); }
            @Override public void onBeginningOfSpeech() { notifyState("hearing"); }
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int e) {
                notifyState("idle");
                if (listening) restart(400);
            }
            @Override public void onResults(Bundle r) {
                ArrayList<String> texts = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (texts != null && !texts.isEmpty()) {
                    org.json.JSONArray arr = new org.json.JSONArray();
                    for (String t : texts) arr.put(t);
                    dispatchArray(arr.toString());
                }
                if (listening) restart(50);
            }
            @Override public void onPartialResults(Bundle p) {}
            @Override public void onEvent(int e, Bundle b) {}
        });
    }

    private void dispatch(String utterance) {
        final String js = "window.onSpeechResult && onSpeechResult(" + JSONObject.quote(utterance) + ")";
        webView.post(new Runnable() { public void run() { webView.evaluateJavascript(js, null); } });
    }

    private void dispatchArray(String jsonArrayLiteral) {
        final String js = "window.onSpeechResults && onSpeechResults(" + jsonArrayLiteral + ")";
        webView.post(new Runnable() { public void run() { webView.evaluateJavascript(js, null); } });
    }

    private void notifyState(String state) {
        final String js = "window.onSpeechState && onSpeechState(" + JSONObject.quote(state) + ")";
        webView.post(new Runnable() { public void run() { webView.evaluateJavascript(js, null); } });
    }

    private void restart(long delayMs) {
        webView.postDelayed(new Runnable() {
            public void run() {
                if (recognizer != null && listening) {
                    try { recognizer.startListening(recognizerIntent); } catch (Exception ignored) {}
                }
            }
        }, delayMs);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        listening = false;
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        String state = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) ? "granted" : "denied";
        final String js = "window.onMicPermission && onMicPermission(" + JSONObject.quote(state) + ")";
        webView.post(new Runnable() { public void run() { webView.evaluateJavascript(js, null); } });
    }

    class LogBridge {
        private static final String LOG_NAME = "pingpong-voice.log";
        private static final String LOG_DIR = "Documents/PingPong";
        private volatile Uri cachedUri;
        private volatile File legacyFile;
        private final SimpleDateFormat ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);

        @JavascriptInterface
        public String write(String line) {
            try {
                String stamped = ts.format(new Date()) + " " + line + "\n";
                byte[] bytes = stamped.getBytes("UTF-8");
                if (Build.VERSION.SDK_INT >= 29) {
                    Uri uri = getOrCreateUri();
                    if (uri == null) return "err:nouri";
                    OutputStream out = null;
                    try {
                        out = getContentResolver().openOutputStream(uri, "wa");
                        if (out == null) return "err:noopen";
                        out.write(bytes);
                    } finally { if (out != null) try { out.close(); } catch (Exception ignored) {} }
                    return "ok:" + describePath();
                } else {
                    File f = getOrCreateLegacyFile();
                    if (f == null) return "err:nofile";
                    FileOutputStream fos = new FileOutputStream(f, true);
                    try { fos.write(bytes); } finally { fos.close(); }
                    return "ok:" + f.getAbsolutePath();
                }
            } catch (Exception e) {
                return "err:" + e.getClass().getSimpleName() + ":" + e.getMessage();
            }
        }

        @JavascriptInterface
        public String path() { return describePath(); }

        private String describePath() {
            if (Build.VERSION.SDK_INT >= 29) return "Documents/PingPong/" + LOG_NAME;
            if (legacyFile != null) return legacyFile.getAbsolutePath();
            return "/sdcard/" + LOG_DIR + "/" + LOG_NAME;
        }

        private Uri getOrCreateUri() {
            if (cachedUri != null) return cachedUri;
            ContentResolver cr = getContentResolver();
            Uri collection = MediaStore.Files.getContentUri("external");
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
            String[] args = new String[]{ LOG_NAME, LOG_DIR + "%" };
            Cursor c = null;
            try {
                c = cr.query(collection, new String[]{ MediaStore.MediaColumns._ID }, selection, args, null);
                if (c != null && c.moveToFirst()) {
                    long id = c.getLong(0);
                    cachedUri = ContentUris.withAppendedId(collection, id);
                    return cachedUri;
                }
            } catch (Exception ignored) {
            } finally { if (c != null) c.close(); }

            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, LOG_NAME);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, LOG_DIR);
            try {
                cachedUri = cr.insert(collection, cv);
            } catch (Exception ignored) {}
            return cachedUri;
        }

        private File getOrCreateLegacyFile() {
            if (legacyFile != null) return legacyFile;
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "PingPong");
            if (!dir.exists()) dir.mkdirs();
            legacyFile = new File(dir, LOG_NAME);
            return legacyFile;
        }
    }

    class SpeechBridge {
        @JavascriptInterface
        public void start() {
            runOnUiThread(new Runnable() { public void run() {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                    return;
                }
                setupRecognizer();
                listening = true;
                if (recognizer != null) {
                    try { recognizer.startListening(recognizerIntent); } catch (Exception ignored) {}
                }
            }});
        }
        @JavascriptInterface
        public void stop() {
            runOnUiThread(new Runnable() { public void run() {
                listening = false;
                if (recognizer != null) {
                    try { recognizer.stopListening(); } catch (Exception ignored) {}
                    try { recognizer.cancel(); } catch (Exception ignored) {}
                }
                notifyState("idle");
            }});
        }
        @JavascriptInterface
        public boolean isAvailable() {
            return SpeechRecognizer.isRecognitionAvailable(MainActivity.this);
        }
        @JavascriptInterface
        public boolean hasPermission() {
            return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
    }
}
