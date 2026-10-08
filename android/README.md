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
