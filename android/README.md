# Android app

## Run the app

Follow the [Android Studio setup in the root README](../README.md#run-the-android-app-in-android-studio).

## Run tests

From the `android/` folder, run unit tests:

```bash
bash gradlew :app:testDebugUnitTest
```

With a running emulator or connected Android device, run instrumentation tests:

```bash
bash gradlew :app:connectedDebugAndroidTest
```
