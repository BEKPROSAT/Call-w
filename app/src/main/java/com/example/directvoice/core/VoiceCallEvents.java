package com.example.directvoice.core;

public interface VoiceCallEvents {
    void onStateChanged(CallState state, String message);

    void onError(String message, Throwable throwable);
}
