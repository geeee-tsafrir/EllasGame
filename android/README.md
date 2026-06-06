# Android Module Skeleton

This folder is reserved for the Android launcher and Android-specific integrations.

Once the Android Gradle plugin and game framework are selected, add this module to `settings.gradle` with:

```gradle
include(":android")
```

Expected structure:

- `src/main/java/com/ellasgame/android/` for Android launcher code.
- `src/main/assets/` for game assets packaged with Android.
- `src/main/res/` for Android resources.
- `src/main/AndroidManifest.xml` for Android application metadata.
