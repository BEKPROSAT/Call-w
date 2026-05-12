package com.example.directvoice.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import com.example.directvoice.core.CallState;
import com.example.directvoice.core.VoiceCallClient;
import com.example.directvoice.core.VoiceCallEvents;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UdpVoiceCallClient implements VoiceCallClient {
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket socket;
    private InetAddress remoteAddress;
    private int remotePort = AudioConfig.CALL_PORT;

    public UdpVoiceCallClient(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public synchronized void startListening(VoiceCallEvents events) throws Exception {
        ensureAudioPermission();
        start(null, AudioConfig.CALL_PORT, events, CallState.LISTENING);
    }

    @Override
    public synchronized void call(InetAddress address, int port, VoiceCallEvents events) throws Exception {
        ensureAudioPermission();
        start(address, port, events, CallState.IN_CALL);
    }

    @Override
    public synchronized void hangUp() {
        running.set(false);
        if (socket != null) {
            socket.close();
            socket = null;
        }
        remoteAddress = null;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void start(InetAddress address, int port, VoiceCallEvents events, CallState initialState) throws SocketException {
        hangUp();
        remoteAddress = address;
        remotePort = port;
        socket = new DatagramSocket(AudioConfig.CALL_PORT);
        running.set(true);
        events.onStateChanged(initialState, initialState == CallState.LISTENING ? "Ready for calls" : "Calling " + address.getHostAddress());
        executor.execute(() -> receiveLoop(events));
        executor.execute(() -> captureLoop(events));
    }

    private void receiveLoop(VoiceCallEvents events) {
        int minBuffer = AudioTrack.getMinBufferSize(
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_OUT,
                AudioConfig.ENCODING
        );
        int bufferSize = Math.max(minBuffer, AudioConfig.BYTES_PER_PACKET * 4);
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(AudioConfig.SAMPLE_RATE)
                        .setEncoding(AudioConfig.ENCODING)
                        .setChannelMask(AudioConfig.CHANNEL_OUT)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        byte[] buffer = new byte[AudioConfig.BYTES_PER_PACKET];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        track.play();
        try {
            while (running.get()) {
                socket.receive(packet);
                if (remoteAddress == null) {
                    remoteAddress = packet.getAddress();
                    remotePort = packet.getPort();
                    events.onStateChanged(CallState.IN_CALL, "Connected to " + remoteAddress.getHostAddress());
                }
                if (packet.getLength() > 0) {
                    track.write(packet.getData(), 0, packet.getLength());
                }
                packet.setLength(buffer.length);
            }
        } catch (Exception error) {
            if (running.get()) {
                events.onError("Audio receive failed", error);
            }
        } finally {
            track.stop();
            track.release();
        }
    }

    private void captureLoop(VoiceCallEvents events) {
        int minBuffer = AudioRecord.getMinBufferSize(
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_IN,
                AudioConfig.ENCODING
        );
        int bufferSize = Math.max(minBuffer, AudioConfig.BYTES_PER_PACKET * 4);
        AudioRecord record = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_IN,
                AudioConfig.ENCODING,
                bufferSize
        );
        byte[] buffer = new byte[AudioConfig.BYTES_PER_PACKET];
        try {
            record.startRecording();
            while (running.get()) {
                int read = record.read(buffer, 0, buffer.length);
                InetAddress destination = remoteAddress;
                if (read > 0 && destination != null) {
                    DatagramPacket packet = new DatagramPacket(buffer, read, destination, remotePort);
                    socket.send(packet);
                }
            }
        } catch (Exception error) {
            if (running.get()) {
                events.onError("Microphone send failed", error);
            }
        } finally {
            record.stop();
            record.release();
        }
    }

    private void ensureAudioPermission() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("Microphone permission is required");
        }
    }
}
