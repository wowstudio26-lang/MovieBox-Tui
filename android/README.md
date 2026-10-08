# MovieBox Leo Android

Native Android frontend for MovieBox Leo.

## Architecture

- Kotlin + Jetpack Compose for the Android UI.
- Existing Rust crate remains the core engine.
- Android/Rust JNI integration will be added after the UI shell is validated.
- The terminal/TUI frontend remains intact for desktop and Termux.

## Build

Open the `android/` directory in Android Studio and sync Gradle.

Or, with a local Android SDK/Gradle environment:

```bash
./gradlew :app:assembleDebug
```

The first milestone intentionally uses local placeholder catalog cards. Provider, playback, subtitle and download calls are wired to the Rust core in the next milestone.


## Rust bridge

The Android Search screen now calls the existing Rust MovieBoxService through JNI. The bridge currently exposes MovieBox provider search and returns JSON to Kotlin.

### Local Android build

Install the Android SDK/NDK, Rust 1.90+, and cargo-ndk, then from the repository root run:

```bash
cargo install cargo-ndk --locked
cd android
gradle assembleDebug
```

The Gradle build invokes cargo ndk for the arm64-v8a Android ABI and places the Rust shared library under the app JNI library directory.

### CI build

Pushes to android-app run `.github/workflows/android.yml`. The workflow builds a debug APK and publishes it as the `moviebox-leo-debug-apk` artifact.

The current milestone is deliberately limited to Search. Playback, details, posters, downloads, subtitles, history and favorites will be connected incrementally through the same Rust core.
