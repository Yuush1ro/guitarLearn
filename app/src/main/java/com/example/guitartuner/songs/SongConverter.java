package com.example.guitartuner.songs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Разбирает файлы Guitar Pro (.gp3/.gp4/.gp5/.gpx/.gp) с помощью AlphaTab
 * в невидимом WebView. Вызывать и получать результат — в главном потоке.
 */
public class SongConverter {

    public interface Callback {
        void onSuccess(ParsedSong song);

        void onError(String message);
    }

    private static final String PARSER_URL =
            "https://" + WebViewAssetLoader.DEFAULT_DOMAIN + "/assets/alphatab/parser.html";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private Callback callback;

    public static boolean isSupportedFile(String fileName) {
        String name = fileName.toLowerCase();
        return name.endsWith(".gp3") || name.endsWith(".gp4") || name.endsWith(".gp5")
                || name.endsWith(".gpx") || name.endsWith(".gp");
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void convert(Context context, File file, Callback callback) {
        cancel();
        this.callback = callback;

        final String base64;
        try {
            base64 = Base64.encodeToString(readBytes(file), Base64.NO_WRAP);
        } catch (IOException e) {
            finishWithError("Не удалось прочитать файл: " + e.getMessage());
            return;
        }

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
                .build();

        webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Base64 содержит только [A-Za-z0-9+/=], его безопасно вставлять в JS-строку
                view.evaluateJavascript("window.parseSong('" + base64 + "');", null);
            }
        });
        webView.loadUrl(PARSER_URL);
    }

    // Files.readAllBytes есть только с API 26, а minSdk = 24
    private static byte[] readBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    /** Прерывает разбор; колбэк после этого не вызывается. */
    public void cancel() {
        callback = null;
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
    }

    private void finishWithSuccess(ParsedSong song) {
        Callback cb = callback;
        cancel();
        if (cb != null) cb.onSuccess(song);
    }

    private void finishWithError(String message) {
        Callback cb = callback;
        cancel();
        if (cb != null) cb.onError(message);
    }

    /** Методы вызываются из JS в фоновом потоке WebView. */
    private class Bridge {
        @JavascriptInterface
        public void onParsed(String json) {
            mainHandler.post(() -> {
                try {
                    finishWithSuccess(ParsedSong.fromJson(json));
                } catch (Exception e) {
                    finishWithError("Не удалось разобрать ответ: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void onParseError(String message) {
            mainHandler.post(() -> finishWithError("Файл не распознан: " + message));
        }
    }
}
