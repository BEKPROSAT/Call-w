package com.example.directvoice.core;

import java.net.InetAddress;
import java.util.Objects;

public final class Peer {
    private final String name;
    private final InetAddress address;
    private final int callPort;

    public Peer(String name, InetAddress address, int callPort) {
        this.name = name;
        this.address = address;
        this.callPort = callPort;
    }

    public String getName() {
        return name;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getCallPort() {
        return callPort;
    }

    public String displayText() {
        return name + " - " + address.getHostAddress();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Peer)) {
            return false;
        }
        Peer peer = (Peer) other;
        return callPort == peer.callPort && Objects.equals(address, peer.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, callPort);
    }
}
