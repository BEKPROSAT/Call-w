package com.example.directvoice.audio;

import android.media.AudioFormat;

public final class AudioConfig {
    public static final int SAMPLE_RATE = 16000;
    public static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    public static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    public static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int SAMPLES_PER_PACKET = 320;
    public static final int BYTES_PER_PACKET = SAMPLES_PER_PACKET * 2;
    public static final int CALL_PORT = 50005;
    public static final int DISCOVERY_PORT = 50006;

    private AudioConfig() {
    }
}
