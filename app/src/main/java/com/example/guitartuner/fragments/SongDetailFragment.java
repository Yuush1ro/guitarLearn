package com.example.guitartuner.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.webkit.WebViewAssetLoader;

import com.example.guitartuner.R;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

public class SongDetailFragment extends Fragment {

    private static final String ARG_PATH = "arg_path";
    private static final String ARG_TITLE = "arg_title";

    // assets раздаются по https, а не file:// — иначе WebView блокирует загрузку
    // шрифтов AlphaTab (Bravura) как cross-origin запрос
    private static final String ALPHATAB_URL =
            "https://" + WebViewAssetLoader.DEFAULT_DOMAIN + "/assets/alphatab/alphatab.html";

    private WebView webView;
    private TextView titleView;

    private String filePath;
    private String title;

    public static SongDetailFragment newInstance(String filePath, String title) {
        SongDetailFragment fragment = new SongDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, filePath);
        args.putString(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            filePath = args.getString(ARG_PATH);
            title = args.getString(ARG_TITLE, "Песня");
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_song_detail, container, false);

        titleView = view.findViewById(R.id.textSongTitle);
        webView = view.findViewById(R.id.webViewTab);

        titleView.setText(title);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(requireContext()))
                .build();

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(new AlphaTabBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadSongIntoAlphaTab();
            }
        });

        webView.loadUrl(ALPHATAB_URL);

        return view;
    }

    private void loadSongIntoAlphaTab() {
        if (filePath == null || webView == null) return;

        try (InputStream inputStream = new FileInputStream(filePath)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            // Base64 содержит только [A-Za-z0-9+/=], так что его безопасно вставлять в JS-строку
            String base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
            webView.evaluateJavascript("window.loadSongFromBase64('" + base64 + "');", null);

        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Ошибка чтения файла: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Методы вызываются из JS в фоновом потоке WebView. */
    private class AlphaTabBridge {
        @JavascriptInterface
        public void onScoreLoaded(String scoreTitle) {
            runOnUi(() -> titleView.setText(scoreTitle));
        }

        @JavascriptInterface
        public void onError(String message) {
            runOnUi(() -> Toast.makeText(requireContext(),
                    "AlphaTab: " + message, Toast.LENGTH_LONG).show());
        }

        private void runOnUi(Runnable action) {
            View root = getView();
            if (root == null) return; // экран уже закрыт
            root.post(() -> {
                if (getView() != null) action.run();
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
    }
}
