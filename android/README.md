# Android app

## Run the app

1. Follow the [backend authentication setup](../README.md#running-the-backend-for-authentication) and start the backend on port `8000`.
2. Open the `android/` folder as a project in Android Studio.
3. Start an Android emulator, select the `app` run configuration, and click **Run** to launch the app.

## Run tests

From the `android/` folder, run unit tests:

```bash
bash gradlew :app:testDebugUnitTest
```

With a running emulator or connected Android device, run instrumentation tests:

```bash
bash gradlew :app:connectedDebugAndroidTest
```
