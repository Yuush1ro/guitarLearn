package com.example.guitartuner.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;

import com.example.guitartuner.ui.Anim;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.guitartuner.R;
import com.example.guitartuner.tuner.NoteUtils;
import com.example.guitartuner.tuner.PitchDetector;
import com.example.guitartuner.views.TunerView;

import java.util.Locale;

public class TunerFragment extends Fragment {

    // сглаживание стрелки: доля нового значения в экспоненциальном среднем
    private static final double SMOOTHING = 0.15;

    private TunerView tunerView;
    private TextView textView;
    private Button btnToggle;

    private final PitchDetector pitchDetector = new PitchDetector(new PitchDetector.PitchListener() {
        @Override
        public void onPitchDetected(double frequencyHz) {
            showPitch(frequencyHz);
        }

        @Override
        public void onSilence() {
            // после паузы новая нота не должна "подтягиваться" от старой
            lastMidi = Integer.MIN_VALUE;
        }
    });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startTuner();
                } else {
                    Toast.makeText(requireContext(),
                            "Без доступа к микрофону тюнер не работает",
                            Toast.LENGTH_SHORT).show();
                }
            });

    private double smoothedCents = 0;
    private int lastMidi = Integer.MIN_VALUE;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_tuner, container, false);

        textView = view.findViewById(R.id.textView);
        tunerView = view.findViewById(R.id.tunerView);
        btnToggle = view.findViewById(R.id.btnToggle);

        btnToggle.setOnClickListener(v -> {
            if (pitchDetector.isRunning()) {
                stopTuner();
            } else {
                startTuner();
            }
        });

        Anim.cascadeIn(view.findViewById(R.id.tunerContent));
        return view;
    }

    private void startTuner() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        lastMidi = Integer.MIN_VALUE;
        pitchDetector.start();
        btnToggle.setText("СТОП");
    }

    private void stopTuner() {
        pitchDetector.stop();
        if (btnToggle != null) btnToggle.setText("СТАРТ");
    }

    // вызывается в главном потоке (так гарантирует PitchDetector)
    private void showPitch(double freq) {
        NoteUtils.NoteInfo note = NoteUtils.frequencyToNote(freq);
        if (note == null) return;

        if (note.midi != lastMidi) {
            smoothedCents = note.cents;
            lastMidi = note.midi;
        } else {
            smoothedCents = (1 - SMOOTHING) * smoothedCents + SMOOTHING * note.cents;
        }

        tunerView.setCents((float) smoothedCents);
        // нота крупно, частота — мелкой строкой под ней
        String hz = String.format(Locale.US, "%.1f Гц", freq);
        SpannableString text = new SpannableString(note.fullName + "\n" + hz);
        text.setSpan(new RelativeSizeSpan(0.32f), note.fullName.length() + 1, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(text);
    }

    @Override
    public void onPause() {
        super.onPause();
        // не держим микрофон, когда экран не виден
        stopTuner();
    }
}
