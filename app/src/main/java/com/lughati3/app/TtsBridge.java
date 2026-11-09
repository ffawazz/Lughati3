package com.lughati3.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.util.Locale;

/**
 * جسر النطق: يستدعيه JavaScript عبر window.AndroidTTS
 *  speak(text, rate, id) / stop() / status() / openSettings()
 * وعند انتهاء الكلام يستدعي window.__ttsEvent('end', id).
 */
public class TtsBridge extends UtteranceProgressListener implements TextToSpeech.OnInitListener {
    private final Context ctx;
    private final WebView web;
    private final TextToSpeech tts;
    private volatile boolean ready = false;
    private volatile boolean arabic = false;
    private String pendingText, pendingId;
    private float pendingRate = 0.85f;

    public TtsBridge(Context ctx, WebView web) {
        this.ctx = ctx.getApplicationContext();
        this.web = web;
        this.tts = new TextToSpeech(this.ctx, this);
        this.tts.setOnUtteranceProgressListener(this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) { ready = false; return; }
        Locale[] tries = { new Locale("ar", "SA"), new Locale("ar") };
        for (Locale l : tries) {
            int r = tts.setLanguage(l);
            if (r >= TextToSpeech.LANG_AVAILABLE) { arabic = true; break; }
        }
        ready = true;
        synchronized (this) {
            if (pendingText != null) { doSpeak(pendingText, pendingRate, pendingId); pendingText = null; }
        }
    }

    private void doSpeak(String text, float rate, String id) {
        tts.setSpeechRate(Math.max(0.3f, Math.min(1.6f, rate)));
        Bundle p = new Bundle();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, p, id);
    }

    @JavascriptInterface
    public void speak(String text, float rate, String id) {
        synchronized (this) {
            if (!ready) { pendingText = text; pendingRate = rate; pendingId = id; return; }
        }
        doSpeak(text, rate, id);
    }

    @JavascriptInterface
    public void stop() {
        synchronized (this) { pendingText = null; }
        tts.stop();
    }

    @JavascriptInterface
    public String status() {
        return "{\"engine\":\"android\",\"ready\":" + ready + ",\"arabic\":" + arabic + "}";
    }

    @JavascriptInterface
    public void openSettings() {
        try {
            Intent i = new Intent("com.android.settings.TTS_SETTINGS");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception ignored) { /* لا شيء */ }
        }
    }

    public void shutdown() {
        try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { /* لا شيء */ }
    }

    private void notifyJs(final String type, final String id) {
        if (id == null) return;
        final String safe = id.replaceAll("[^A-Za-z0-9_]", "");
        web.post(new Runnable() {
            @Override public void run() {
                web.evaluateJavascript("window.__ttsEvent&&window.__ttsEvent('" + type + "','" + safe + "')", null);
            }
        });
    }

    @Override public void onStart(String utteranceId) { }
    @Override public void onDone(String utteranceId) { notifyJs("end", utteranceId); }
    @Override @SuppressWarnings("deprecation") public void onError(String utteranceId) { notifyJs("error", utteranceId); }
    @Override public void onError(String utteranceId, int errorCode) { notifyJs("error", utteranceId); }
    @Override public void onStop(String utteranceId, boolean interrupted) { notifyJs("end", utteranceId); }
}
