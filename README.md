# RideApp Flutter Hello World

This repository contains a minimal Flutter "Hello, world" application that builds for both Android and iOS. The project is ready to open in Android Studio, VS Code, or Xcode once Flutter is installed on your machine.

## Getting started

1. [Install Flutter](https://docs.flutter.dev/get-started/install) and ensure the `flutter` tool is on your PATH.
2. Fetch dependencies:
   ```sh
   flutter pub get
   ```
3. Run the application on a simulator or connected device:
   ```sh
   flutter run
   ```

## Building release binaries

### Android APK

The repository includes a self-contained Gradle wrapper implementation so you can invoke the Android build without any additional setup. The wrapper will automatically download Gradle the first time you run it.

```sh
flutter build apk --release
```

The signed release APK will be produced under `build/app/outputs/flutter-apk/`.

### iOS

```sh
flutter build ios --release
```

Open the generated Xcode project in `ios/Runner.xcworkspace` to archive and distribute through TestFlight or the App Store. Make sure you configure your Apple developer team and provisioning profile in Xcode before archiving.

## Project structure

- `lib/main.dart` — Flutter entry point with the hello world UI.
- `android/` — Native Android project with Gradle configuration.
- `ios/` — Native iOS project configured for Flutter.
- `pubspec.yaml` — Flutter dependencies and metadata.

## Testing

Run the default widget test suite to confirm the counter screen renders and increments correctly:

```sh
flutter test
```

The test harness in `test/widget_test.dart` exercises the app's UI using Flutter's widget testing framework, which provides a
fast feedback loop without needing a device or emulator.

Happy coding!
