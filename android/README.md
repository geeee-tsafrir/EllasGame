# Android App

This module is the Android launcher for EllasGame.

The desktop camera implementation uses JavaCV and FFmpeg. Android uses CameraX instead, because Android camera access should go through the Android camera stack.

## Build

Install Android Studio first so the Android SDK, platform tools, and SDK licenses are available.

From the project root:

```bash
gradle :android:assembleDebug
```

## Connect A Phone

1. On the phone, enable Developer options.
2. Enable USB debugging.
3. Connect the phone with USB.
4. Accept the trust prompt on the phone.
5. Check that the phone is visible:

```bash
adb devices
```

You should see a device listed as `device`, not `unauthorized`.

## Install And Run

```bash
gradle :android:installDebug
adb shell am start -n com.ellasgame/com.ellasgame.android.AndroidLauncher
```

The app asks for camera permission the first time the camera button is pressed.

## Current Android Surface

- Left full-height side panel.
- Camera button that connects and disconnects CameraX preview.
- Top panel uses 2/3 of the content height for camera preview.
- Bottom panel uses 1/3 of the content height.

Next Android work should move settings storage to Android app-private storage and add real camera selection through CameraX camera metadata.
