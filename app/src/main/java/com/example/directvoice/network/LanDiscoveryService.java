package com.example.directvoice.network;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.example.directvoice.audio.AudioConfig;
import com.example.directvoice.core.Peer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LanDiscoveryService {
    public interface Listener {
        void onPeerFound(Peer peer);

        void onDiscoveryError(String message, Throwable throwable);
    }

    private static final String PREFIX = "DIRECTVOICE";
    private static final String DISCOVER = "DISCOVER";
    private static final String HERE = "HERE";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService senderExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DatagramSocket socket;
    private WifiManager.MulticastLock multicastLock;

    public LanDiscoveryService(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start(String deviceName, Listener listener) {
        stop();
        running.set(true);
        executor.execute(() -> loop(deviceName, listener));
    }

    public void stop() {
        running.set(false);
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        multicastLock = null;
    }

    public void sendDiscovery() {
        senderExecutor.execute(() -> sendMessage(message(DISCOVER, android.os.Build.MODEL), "255.255.255.255"));
    }

    private void loop(String deviceName, Listener listener) {
        acquireMulticastLock();
        byte[] buffer = new byte[512];
        try {
            socket = new DatagramSocket(AudioConfig.DISCOVERY_PORT);
            socket.setBroadcast(true);
            sendMessage(message(HERE, deviceName), "255.255.255.255");
            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String body = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                String[] parts = body.split("\\|");
                if (parts.length < 4 || !PREFIX.equals(parts[0])) {
                    continue;
                }
                String type = parts[1];
                String name = parts[2];
                int port = Integer.parseInt(parts[3]);
                if (DISCOVER.equals(type)) {
                    sendMessage(message(HERE, deviceName), packet.getAddress().getHostAddress());
                } else if (HERE.equals(type)) {
                    String localAddress = LocalNetworkInfo.localIpv4Address();
                    if (!packet.getAddress().getHostAddress().equals(localAddress)) {
                        listener.onPeerFound(new Peer(name, packet.getAddress(), port));
                    }
                }
            }
        } catch (Exception error) {
            if (running.get()) {
                listener.onDiscoveryError("Peer discovery failed", error);
            }
        }
    }

    private void sendMessage(String message, String host) {
        try {
            DatagramSocket activeSocket = socket;
            if (activeSocket == null || activeSocket.isClosed()) {
                return;
            }
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                    payload,
                    payload.length,
                    InetAddress.getByName(host),
                    AudioConfig.DISCOVERY_PORT
            );
            activeSocket.send(packet);
        } catch (Exception ignored) {
        }
    }

    private String message(String type, String deviceName) {
        String safeName = deviceName == null ? "Android" : deviceName.replace("|", " ");
        return PREFIX + "|" + type + "|" + safeName + "|" + AudioConfig.CALL_PORT;
    }

    private void acquireMulticastLock() {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("directvoice-discovery");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        }
    }
}
