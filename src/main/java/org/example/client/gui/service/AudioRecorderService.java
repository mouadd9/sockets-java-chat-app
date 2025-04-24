package org.example.client.gui.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * Service for recording audio messages.
 */
public class AudioRecorderService {

    private TargetDataLine targetLine;
    private boolean isRecording = false;
    private Thread recordingThread;
    private ByteArrayOutputStream byteOutputStream;

    // Audio format settings
    private static final float SAMPLE_RATE = 44100;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    private static final AudioFormat.Encoding ENCODING = AudioFormat.Encoding.PCM_SIGNED;

    /**
     * Starts recording audio.
     *
     * @throws LineUnavailableException If the audio line is unavailable
     */
    public void startRecording() throws LineUnavailableException {
        if (isRecording) {
            return;
        }

        // Create audio format
        AudioFormat format = new AudioFormat(
                ENCODING,
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS,
                CHANNELS,
                (SAMPLE_SIZE_BITS / 8) * CHANNELS,
                SAMPLE_RATE,
                BIG_ENDIAN);

        // Get the target data line
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("The system does not support the specified audio format");
        }

        targetLine = (TargetDataLine) AudioSystem.getLine(info);
        targetLine.open(format);
        targetLine.start();

        byteOutputStream = new ByteArrayOutputStream();
        isRecording = true;

        // Create a thread to read audio data
        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            int bytesRead;

            while (isRecording) {
                bytesRead = targetLine.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    byteOutputStream.write(buffer, 0, bytesRead);
                }
            }
        });

        recordingThread.start();
    }

    /**
     * Stops recording and returns the recorded audio file.
     *
     * @return The recorded audio file
     * @throws IOException If an I/O error occurs
     */
    public File stopRecording() throws IOException {
        if (!isRecording) {
            return null;
        }

        isRecording = false;

        try {
            // Wait for the recording thread to finish
            recordingThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        targetLine.stop();
        targetLine.close();

        // Create a WAV file from the recorded bytes
        byte[] audioBytes = byteOutputStream.toByteArray();
        AudioFormat format = new AudioFormat(
                ENCODING,
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS,
                CHANNELS,
                (SAMPLE_SIZE_BITS / 8) * CHANNELS,
                SAMPLE_RATE,
                BIG_ENDIAN);

        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(audioBytes);
        AudioInputStream audioInputStream = new AudioInputStream(byteInputStream, format, audioBytes.length / format.getFrameSize());

        // Create a temporary file
        File tempFile = File.createTempFile("audio_" + UUID.randomUUID().toString(), ".wav");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, tempFile);
        } catch (IOException e) {
            throw new IOException("Failed to save audio file: " + e.getMessage(), e);
        }

        return tempFile;
    }

    /**
     * Checks if the system supports audio recording.
     *
     * @return true if audio recording is supported, false otherwise
     */
    public boolean isAudioRecordingSupported() {
        try {
            AudioFormat format = new AudioFormat(
                    ENCODING,
                    SAMPLE_RATE,
                    SAMPLE_SIZE_BITS,
                    CHANNELS,
                    (SAMPLE_SIZE_BITS / 8) * CHANNELS,
                    SAMPLE_RATE,
                    BIG_ENDIAN);

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            return AudioSystem.isLineSupported(info);
        } catch (Exception e) {
            return false;
        }
    }
}