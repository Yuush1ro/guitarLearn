package com.example.guitartuner.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.guitartuner.R;

import java.io.File;
import java.nio.file.Files;

public class SongDetailFragment extends Fragment {

    private static final String ARG_PATH = "arg_path";
    private static final String ARG_TITLE = "arg_title";

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

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.addJavascriptInterface(new AlphaTabBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadSongIntoAlphaTab();
            }
        });

        webView.loadUrl("file:///android_asset/alphatab/alphatab.html");

        return view;
    }

    private void loadSongIntoAlphaTab() {
        if (filePath == null) return;

        try {
            byte[] bytes = Files.readAllBytes(new File(filePath).toPath());
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            webView.evaluateJavascript("window.loadSongFromBase64('" + base64 + "');", null);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Ошибка чтения файла: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class AlphaTabBridge {
        @JavascriptInterface
        public void onScoreLoaded(String scoreTitle) {
            requireActivity().runOnUiThread(() -> titleView.setText(scoreTitle));
        }

        @JavascriptInterface
        public void onError(String message) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "AlphaTab: " + message, Toast.LENGTH_LONG).show());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
    }
}