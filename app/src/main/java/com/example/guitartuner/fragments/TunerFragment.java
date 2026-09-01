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
//claude
                int read = audioRecord.read(
                        buffer,
                        0,
                        bufferSize
                );

                // окно Ханна перед FFT — убирает спектральное растекание
                for (int i = 0; i < read; i++) {
                    double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (read - 1));
                    fftData[i] = buffer[i] * window;
                }
                for (int i = read; i < fftData.length; i++) {
                    fftData[i] = 0;
                }

                DoubleFFT_1D fft =
                        new DoubleFFT_1D(bufferSize);

                fft.realForward(fftData);

                // корректная распаковка комплексных магнитуд
                int n = bufferSize;
                double maxMag = -1;
                int peakBin = -1;

                // пропускаем бины ниже ~60 Гц (гул/наводки)
                int minBin = (int) (60.0 * n / sampleRate);

                for (int k = Math.max(2, minBin); k < n / 2 - 1; k++) {
                    double re = fftData[2 * k];
                    double im = fftData[2 * k + 1];
                    double mag = Math.sqrt(re * re + im * im);
                    if (mag > maxMag) {
                        maxMag = mag;
                        peakBin = k;
                    }
                }

                if (peakBin <= 1) continue;

                // параболическая интерполяция вокруг пика для суб-бинной точности
                double reM = fftData[2 * (peakBin - 1)], imM = fftData[2 * (peakBin - 1) + 1];
                double reP = fftData[2 * (peakBin + 1)], imP = fftData[2 * (peakBin + 1) + 1];
                double magM = Math.sqrt(reM * reM + imM * imM);
                double magP = Math.sqrt(reP * reP + imP * imP);

                double denom = (magM - 2 * maxMag + magP);
                double delta = denom == 0 ? 0 : 0.5 * (magM - magP) / denom;

                double freq = (peakBin + delta) * sampleRate / n;
//claude
                double semitoneOffset = 12 *
                        (Math.log(freq / 440.0)
                                / Math.log(2));

                int semitone =
                        (int) Math.round(semitoneOffset);

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