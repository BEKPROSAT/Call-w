package com.example.directvoice;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.directvoice.audio.AudioConfig;
import com.example.directvoice.audio.UdpVoiceCallClient;
import com.example.directvoice.core.CallState;
import com.example.directvoice.core.Peer;
import com.example.directvoice.core.VoiceCallEvents;
import com.example.directvoice.network.LanDiscoveryService;
import com.example.directvoice.network.LocalNetworkInfo;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity implements VoiceCallEvents, LanDiscoveryService.Listener {
    private static final int REQUEST_RECORD_AUDIO = 31;

    private UdpVoiceCallClient callClient;
    private LanDiscoveryService discoveryService;
    private TextView statusText;
    private TextView localAddressText;
    private EditText addressInput;
    private Button listenButton;
    private Button callButton;
    private Button hangUpButton;
    private Button discoverButton;
    private ArrayAdapter<String> peersAdapter;
    private final List<Peer> peers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        callClient = new UdpVoiceCallClient(this);
        discoveryService = new LanDiscoveryService(this);
        setContentView(buildUi());
        requestAudioPermissionIfNeeded();
        discoveryService.start(android.os.Build.MODEL, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        callClient.hangUp();
        discoveryService.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            showToast("Microphone permission is required for calls");
        }
    }

    @Override
    public void onStateChanged(CallState state, String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            boolean running = state == CallState.LISTENING || state == CallState.IN_CALL;
            listenButton.setEnabled(!running);
            callButton.setEnabled(!running);
            hangUpButton.setEnabled(running);
        });
    }

    @Override
    public void onError(String message, Throwable throwable) {
        runOnUiThread(() -> {
            statusText.setText(message + ": " + throwable.getMessage());
            listenButton.setEnabled(true);
            callButton.setEnabled(true);
            hangUpButton.setEnabled(false);
        });
        callClient.hangUp();
    }

    @Override
    public void onPeerFound(Peer peer) {
        runOnUiThread(() -> {
            if (!peers.contains(peer)) {
                peers.add(peer);
                peersAdapter.add(peer.displayText());
            }
        });
    }

    @Override
    public void onDiscoveryError(String message, Throwable throwable) {
        runOnUiThread(() -> statusText.setText(message + ": " + throwable.getMessage()));
    }

    private View buildUi() {
        int padding = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(246, 248, 250));

        TextView title = new TextView(this);
        title.setText("DirectVoice");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(17, 24, 39));
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        statusText = new TextView(this);
        statusText.setText("Idle");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.rgb(55, 65, 81));
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, matchWrap());

        localAddressText = new TextView(this);
        localAddressText.setText("Your IP: " + LocalNetworkInfo.localIpv4Address() + "  Port: " + AudioConfig.CALL_PORT);
        localAddressText.setTextSize(14);
        localAddressText.setTextColor(Color.rgb(75, 85, 99));
        localAddressText.setGravity(Gravity.CENTER);
        root.addView(localAddressText, matchWrap());

        addressInput = new EditText(this);
        addressInput.setHint("Peer IP address");
        addressInput.setSingleLine(true);
        addressInput.setTextColor(Color.rgb(17, 24, 39));
        addressInput.setHintTextColor(Color.rgb(107, 114, 128));
        root.addView(addressInput, matchWrap());

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);

        listenButton = button("Listen", view -> startListening());
        callButton = button("Call", view -> startCall());
        hangUpButton = button("Hang Up", view -> hangUp());
        hangUpButton.setEnabled(false);

        buttonRow.addView(listenButton, weightWrap());
        buttonRow.addView(callButton, weightWrap());
        buttonRow.addView(hangUpButton, weightWrap());
        root.addView(buttonRow, matchWrap());

        discoverButton = button("Find Devices", view -> {
            peers.clear();
            peersAdapter.clear();
            discoveryService.sendDiscovery();
            statusText.setText("Searching local network");
        });
        root.addView(discoverButton, matchWrap());

        TextView peersTitle = new TextView(this);
        peersTitle.setText("Nearby devices");
        peersTitle.setTextSize(18);
        peersTitle.setTextColor(Color.rgb(17, 24, 39));
        root.addView(peersTitle, matchWrap());

        ListView peerList = new ListView(this);
        peersAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        peerList.setAdapter(peersAdapter);
        peerList.setOnItemClickListener((parent, view, position, id) -> {
            Peer peer = peers.get(position);
            addressInput.setText(peer.getAddress().getHostAddress());
            startCall(peer);
        });
        root.addView(peerList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        return root;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams weightWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(4), dp(8), dp(4), dp(8));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void requestAudioPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    private void startListening() {
        try {
            callClient.startListening(this);
        } catch (Exception error) {
            onError("Could not start listening", error);
        }
    }

    private void startCall() {
        String address = addressInput.getText().toString().trim();
        if (address.isEmpty()) {
            showToast("Enter a peer IP address");
            return;
        }
        try {
            callClient.call(InetAddress.getByName(address), AudioConfig.CALL_PORT, this);
        } catch (Exception error) {
            onError("Could not start call", error);
        }
    }

    private void startCall(Peer peer) {
        try {
            callClient.call(peer.getAddress(), peer.getCallPort(), this);
        } catch (Exception error) {
            onError("Could not call peer", error);
        }
    }

    private void hangUp() {
        callClient.hangUp();
        onStateChanged(CallState.IDLE, "Idle");
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
