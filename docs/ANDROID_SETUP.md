# Android Studio setup

This workspace is now scaffolded as a standard Kotlin Android app module named `Proto13`.

## 1. Open the folder in Android Studio

- Open the repository root in Android Studio.
- Android Studio should detect the Gradle project automatically.
- If prompted, choose "Open as Project" and let Gradle sync.

## 2. Install the required SDKs

If Android Studio shows missing SDK / JDK messages:

- Install Android Studio if it is not already present.
- Install an Android SDK (API 35 or newer is recommended).
- Ensure a JDK 17 is available.

## 3. Point Gradle at your local SDK

Copy the sample file:

```bash
copy local.properties.example local.properties
```

Then edit `local.properties` and set the SDK path, for example:

```properties
sdk.dir=C\:\Users\<your-user>\AppData\Local\Android\Sdk
```

## 4. Sync and run

From Android Studio:

- Click "Sync Project with Gradle Files"
- Select the app configuration and run the app on an emulator or device

## 5. What this project includes

- Root Gradle settings for the Android app
- `app` module for the Kotlin app
- Jetpack Compose starter screen in `MainActivity.kt`
- Material 3 theme scaffold for future OCR/camera screens

## 6. Next implementation steps

The next tasks are to add:

- image selection from gallery or camera capture
- ML Kit Text Recognition integration
- card matching and OCR benchmarking
- camera and capture workflow for the Pixel 7a
