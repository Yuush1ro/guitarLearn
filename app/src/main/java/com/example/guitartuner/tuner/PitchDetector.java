package com.example.guitartuner.tuner;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import org.jtransforms.fft.DoubleFFT_1D;

public class PitchDetector {

    public interface PitchListener {
        void onPitchDetected(double frequencyHz);
    }

    private static final int SAMPLE_RATE = 44100;

    private Thread thread;
    private volatile boolean running = false;
    private AudioRecord audioRecord;
    private final PitchListener listener;

    public PitchDetector(PitchListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;

        thread = new Thread(() -> {

            int bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            if (bufferSize <= 0) bufferSize = 4096;

            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                );
            } catch (SecurityException e) {
                running = false;
                return;
            }

            short[] buffer = new short[bufferSize];
            double[] fftData = new double[bufferSize];

            audioRecord.startRecording();

            while (running) {

                int read = audioRecord.read(buffer, 0, bufferSize);
                if (read <= 0) continue;

                for (int i = 0; i < read; i++) {
                    double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (read - 1));
                    fftData[i] = buffer[i] * window;
                }
                for (int i = read; i < fftData.length; i++) {
                    fftData[i] = 0;
                }

                DoubleFFT_1D fft = new DoubleFFT_1D(bufferSize);
                fft.realForward(fftData);

                int n = bufferSize;
                double maxMag = -1;
                int peakBin = -1;

                int minBin = (int) (60.0 * n / SAMPLE_RATE);

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

                double reM = fftData[2 * (peakBin - 1)], imM = fftData[2 * (peakBin - 1) + 1];
                double reP = fftData[2 * (peakBin + 1)], imP = fftData[2 * (peakBin + 1) + 1];
                double magM = Math.sqrt(reM * reM + imM * imM);
                double magP = Math.sqrt(reP * reP + imP * imP);

                double denom = (magM - 2 * maxMag + magP);
                double delta = denom == 0 ? 0 : 0.5 * (magM - magP) / denom;

                double freq = (peakBin + delta) * SAMPLE_RATE / n;

                if (listener != null) {
                    listener.onPitchDetected(freq);
                }
            }
        });

        thread.start();
    }

    public void stop() {
        running = false;
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        } catch (Exception ignored) {
        }
    }
}