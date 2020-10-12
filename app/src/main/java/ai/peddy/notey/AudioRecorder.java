package ai.peddy.notey;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.spotify.android.appremote.api.Connector;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AudioRecorder {

    private static final String TAG = "AudioRecorder";

    public static final int SAMPLING_RATE = 48000;
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int ENCODING = AudioFormat.ENCODING_PCM_8BIT;
    public static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_CONFIG, ENCODING);

    private static AudioRecorder recorder;

    private MediaProjectionManager mediaProjectionManager;
    private AudioPlaybackCaptureConfiguration captureConfig;
    private AudioRecord record;
    private Context context;


    private AudioRecorder(Context context) {
        this.context = context;
    }

    public static AudioRecorder getInstance(Context context) {
        if (recorder == null) recorder = new AudioRecorder(context);
        return recorder;
    }


    /*
     * Takes in a MediaProjection Token and initializes the Audio Recorder
     */
    public boolean initialize(int resultCode, Intent intentData) {
        if (record != null && record.getRecordingState() == AudioRecord.STATE_INITIALIZED)
            return true;

        if (mediaProjectionManager == null)
            mediaProjectionManager = (MediaProjectionManager) context.getSystemService
                    (Context.MEDIA_PROJECTION_SERVICE);


        MediaProjection projection = mediaProjectionManager.
                getMediaProjection(resultCode, intentData);

        captureConfig =
                new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build();
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setSampleRate(SAMPLING_RATE)
                    .build();

            record = new AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(BUFFER_SIZE)
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .build();

            if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                return true;
            }

        Log.e(TAG, "Initialization of AudioRecorder failed.");
        return false;
    }

    public boolean startRecording() {
        if (!isInitialized()) {
            Log.w(TAG, "Should not call startRecording before Recorder initialization.");
            return false;
        }
        if (!isRecording())
            record.startRecording();

        Log.i(TAG, "AudioRecorder started recording successfully? " + isRecording());
        return isRecording();
    }

    public boolean stopRecording() {
        if (isRecording())
            record.stop();

        Log.i(TAG, "AudioRecorder stopped recording successfully? " + !isRecording());
        return !isRecording();
    }

    public boolean isRecording() {
        return isInitialized() && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
    }

    public boolean captureWindow() {
        if (!isRecording()) {
            Log.w(TAG, "Tried to capture window before starting recording.");
            return false;
        }


        byte[] audioData = new byte[BUFFER_SIZE];
        File exportDir = new File(context.getExternalFilesDir(null), "captured_recordings");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        String filePath = exportDir.getAbsolutePath() + "/" + "Record-" + new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss").format(new Date()) + ".wav";
        BufferedOutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(filePath));
            writeWavHeader(os, CHANNEL_CONFIG, SAMPLING_RATE, ENCODING);
        } catch (IOException e) {
            Log.e(TAG, "File not found for recording:  ", e);
        }
        int totalBytesRead = 0;
        int maxBytesToRead = SAMPLING_RATE * 1 * 12; // sample rate * bytes per sample, recording length
        while (totalBytesRead < maxBytesToRead) {
            int readResult = record.read(audioData, 0, audioData.length);
            Log.d(TAG, "Read result: " + readResult);
            if (readResult < 0) {
                Log.d(TAG, "Reading from AudioRecord object caused error code: " + readResult);
                break;
            }
            if (readResult == 0) {
                break;
            }
            totalBytesRead += readResult;
            try {
                os.write(audioData, 0, readResult);
            } catch (IOException e) {
                Log.e(TAG, "Could not write out audio data to file: " + filePath);
                e.printStackTrace();
            }
        }
        try {
            os.close();
            updateWavHeader(new File(filePath));
            Log.d(TAG, "file: " + filePath);
        } catch (IOException e) {
            Log.e(TAG, "Error when releasing audio recording file");
            e.printStackTrace();
        }


        return true;
    }

    private boolean isInitialized() {
        return record != null && record.getState() == AudioRecord.STATE_INITIALIZED;
    }

    private static void writeWavHeader(OutputStream out, int channelMask, int sampleRate, int encoding) throws IOException {
        short channels;
        switch (channelMask) {
            case AudioFormat.CHANNEL_IN_MONO:
                channels = 1;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
                channels = 2;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable channel mask");
        }

        short bitDepth;
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT:
                bitDepth = 8;
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
                bitDepth = 16;
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                bitDepth = 32;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable encoding");
        }

        writeWavHeader(out, channels, sampleRate, bitDepth);
    }


    private static void writeWavHeader(OutputStream out, short channels, int sampleRate, short bitDepth) throws IOException {
        // Convert the multi-byte integers to raw bytes in little endian format as required by the spec
        byte[] littleBytes = ByteBuffer
                .allocate(14)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(channels)
                .putInt(sampleRate)
                .putInt(sampleRate * channels * (bitDepth / 8))
                .putShort((short) (channels * (bitDepth / 8)))
                .putShort(bitDepth)
                .array();

        // Not necessarily the best, but it's very easy to visualize this way
        out.write(new byte[]{
                // RIFF header
                'R', 'I', 'F', 'F', // ChunkID
                0, 0, 0, 0, // ChunkSize (must be updated later)
                'W', 'A', 'V', 'E', // Format
                // fmt subchunk
                'f', 'm', 't', ' ', // Subchunk1ID
                16, 0, 0, 0, // Subchunk1Size
                1, 0, // AudioFormat
                littleBytes[0], littleBytes[1], // NumChannels
                littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
                littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // ByteRate
                littleBytes[10], littleBytes[11], // BlockAlign
                littleBytes[12], littleBytes[13], // BitsPerSample
                // data subchunk
                'd', 'a', 't', 'a', // Subchunk2ID
                0, 0, 0, 0, // Subchunk2Size (must be updated later)
        });
    }
    private static void updateWavHeader(File wav) throws IOException {
        byte[] sizes = ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                // There are probably a bunch of different/better ways to calculate
                // these two given your circumstances. Cast should be safe since if the WAV is
                // > 4 GB we've already made a terrible mistake.
                .putInt((int) (wav.length() - 8)) // ChunkSize
                .putInt((int) (wav.length() - 44)) // Subchunk2Size
                .array();

        RandomAccessFile accessWave = null;
        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            accessWave = new RandomAccessFile(wav, "rw");
            // ChunkSize
            accessWave.seek(4);
            accessWave.write(sizes, 0, 4);

            // Subchunk2Size
            accessWave.seek(40);
            accessWave.write(sizes, 4, 4);
        } catch (IOException ex) {
            // Rethrow but we still close accessWave in our finally
            throw ex;
        } finally {
            if (accessWave != null) {
                try {
                    accessWave.close();
                } catch (IOException ex) {
                    //
                }
            }
        }
    }

}
