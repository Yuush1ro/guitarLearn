//package com.example.guitartuner.fragments;
//
//import android.os.Bundle;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//
//import androidx.fragment.app.Fragment;
//
//import com.example.guitartuner.R;
//
//public class TunerFragment extends Fragment {
//
//    public TunerFragment() {
//    }
//
//    @Override
//    public View onCreateView(LayoutInflater inflater,
//                             ViewGroup container,
//                             Bundle savedInstanceState) {
//
//        return inflater.inflate(R.layout.fragment_tuner,
//                container,
//                false);
//    }
//}

package com.example.guitartuner.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.guitartuner.R;
import com.example.guitartuner.views.TunerView;

import org.jtransforms.fft.DoubleFFT_1D;

public class TunerFragment extends Fragment {

    private TunerView tunerView;
    private TextView textView;
    private Button btnToggle;

    private boolean isRunning = false;
    private AudioRecord audioRecord;

    private final String[] NOTES = {
            "C", "C#", "D", "D#", "E", "F",
            "F#", "G", "G#", "A", "A#", "B"
    };

    private double smoothedCents = 0;
    private int lastSemitone = Integer.MIN_VALUE;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_tuner,
                container,
                false
        );

        textView = view.findViewById(R.id.textView);
        tunerView = view.findViewById(R.id.tunerView);
        btnToggle = view.findViewById(R.id.btnToggle);

        btnToggle.setOnClickListener(v -> {

            if (!isRunning) {
                startTuner();
                btnToggle.setText("STOP");
                isRunning = true;
            } else {
                stopTuner();
                btnToggle.setText("START");
                isRunning = false;
            }
        });

        return view;
    }

    private void startTuner() {

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1
            );

            return;
        }

        new Thread(() -> {

            int sampleRate = 44100;

            int bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            short[] buffer = new short[bufferSize];
            double[] fftData = new double[bufferSize];

            audioRecord.startRecording();

            while (isRunning) {

                int read = audioRecord.read(
                        buffer,
                        0,
                        bufferSize
                );

                for (int i = 0; i < read; i++) {
                    fftData[i] = buffer[i];
                }

                DoubleFFT_1D fft =
                        new DoubleFFT_1D(bufferSize);

                fft.realForward(fftData);

                double max = -1;
                int index = -1;

                for (int i = 0; i < fftData.length / 2; i++) {

                    double magnitude =
                            Math.abs(fftData[i]);

                    if (magnitude > max) {
                        max = magnitude;
                        index = i;
                    }
                }

                if (index <= 1) continue;

                double freq =
                        index * sampleRate / bufferSize;

                double n = 12 *
                        (Math.log(freq / 440.0)
                                / Math.log(2));

                int semitone =
                        (int) Math.round(n);

                double targetFreq = 440.0 *
                        Math.pow(2,
                                semitone / 12.0);

                double rawCents = 1200 *
                        (Math.log(freq / targetFreq)
                                / Math.log(2));

                if (semitone != lastSemitone) {
                    smoothedCents = rawCents;
                    lastSemitone = semitone;
                } else {
                    smoothedCents =
                            0.85 * smoothedCents
                                    + 0.15 * rawCents;
                }

                double cents = smoothedCents;

                requireActivity().runOnUiThread(() -> {

                    tunerView.setCents(
                            (float) cents
                    );

                    textView.setText(
                            getTunerInfo(freq)
                    );
                });
            }

        }).start();
    }

    private void stopTuner() {

        try {

            if (audioRecord != null) {

                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }

        } catch (Exception ignored) {
        }
    }

    private String getTunerInfo(double freq) {

        double n = 12 *
                (Math.log(freq / 440.0)
                        / Math.log(2));

        int semitone = (int) Math.round(n);

        int noteIndex = (semitone + 9) % 12;

        if (noteIndex < 0) {
            noteIndex += 12;
        }

        String note = NOTES[noteIndex];

        return note +
                "\n" +
                String.format("%.1f Hz", freq);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTuner();
    }
}