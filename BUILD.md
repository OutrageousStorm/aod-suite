# Building AOD Suite from Source

Build the AOD Suite Android app locally using Android Studio or Gradle CLI.

## Prerequisites

- Android SDK 34 (Android 14)
- Gradle 8.4+
- Java 17+
- 2GB free disk space

## Build

```bash
# Clone
git clone https://github.com/OutrageousStorm/aod-suite.git
cd aod-suite

# Debug APK (for testing)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (for distribution)
# Requires signing — see signing section below
./gradlew assembleRelease
```

## Signing for release

Create `~/.gradle/gradle.properties`:
```properties
RELEASE_KEYSTORE_FILE=path/to/keystore.jks
RELEASE_KEYSTORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=release_key
RELEASE_KEY_PASSWORD=your_key_password
```

Then:
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

## Install locally

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Development

- **IDE**: Android Studio (recommended) or VS Code + Android plugin
- **Language**: Kotlin
- **Architecture**: MVVM with ViewModel
- **Permissions**: None (all via Shizuku)

## Contributing

Fork, create a feature branch, and submit a PR with your improvements.
