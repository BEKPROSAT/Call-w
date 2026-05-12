# DirectVoice

DirectVoice is a native Android voice calling app for devices on the same WiFi network or an already-connected WiFi Direct group. It does not use Flutter, accounts, or an internet relay server.

The current implementation sends 16 kHz mono PCM audio over UDP. This keeps the first version simple, free, and open-source friendly while leaving clear module boundaries for later WebRTC/video/chat/auth work.

## Features

- Voice calls over local network sockets.
- No login or sign-up.
- Manual call by IP address.
- Local peer discovery by UDP broadcast.
- Clean separation between UI, call state, audio transport, and discovery.

## Build Without Android Studio

Push this project to GitHub and open the **Actions** tab. Run the `Android CI` workflow manually, or let it run on push. The workflow uploads a debug APK artifact named `directvoice-debug-apk`.

Local build, if Android SDK and Gradle are installed:

```powershell
gradle assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## How To Use

1. Install the APK on two Android phones.
2. Connect both phones to the same WiFi network, or connect them through WiFi Direct first.
3. Open DirectVoice on both phones and grant microphone permission.
4. On one phone, tap **Listen**.
5. On the other phone, enter the listener phone's IP address and tap **Call**.
6. You can also tap **Find Devices** and call a discovered phone from the list.

## Future Extension Points

- Add video calling by introducing a new `VideoCallClient` beside `VoiceCallClient`.
- Add text chat by adding a message transport under `network`.
- Add user sign-up/login without changing the local voice transport.
- Replace `UdpVoiceCallClient` with WebRTC later if you want echo cancellation, jitter buffering, codecs, NAT traversal, and media negotiation.
