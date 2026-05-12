package com.example.directvoice.core;

import java.net.InetAddress;

public interface VoiceCallClient {
    void startListening(VoiceCallEvents events) throws Exception;

    void call(InetAddress address, int port, VoiceCallEvents events) throws Exception;

    void hangUp();

    boolean isRunning();
}
