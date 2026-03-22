# 🌙 AOD Suite

> Always On Display customization for Android via [Shizuku](https://shizuku.rikka.app/) — no root required.

[![Build APK](https://github.com/OutrageousStorm/aod-suite/actions/workflows/build.yml/badge.svg)](https://github.com/OutrageousStorm/aod-suite/actions/workflows/build.yml)
[![GitHub release](https://img.shields.io/github/v/release/OutrageousStorm/aod-suite?include_prereleases)](https://github.com/OutrageousStorm/aod-suite/releases)

## Features

| Feature | Description |
|---|---|
| ☀️ Min brightness | Control AOD minimum brightness (0–100%) |
| 🌙 AOD toggle | Enable / disable Always On Display |
| 🖼 Blurred wallpaper | Set a blurred version of any photo as your AOD background |
| 👆 Gestures | Tap-to-wake, raise-to-wake |
| 🌡️ Night mode | Warm colour temperature on AOD |
| ⏱️ Timeout | Auto-dismiss AOD after N seconds |
| 🔋 Battery | Ignore battery-level AOD restrictions |

## Requirements

- Android 10+ (API 29+)
- [Shizuku](https://shizuku.rikka.app/) installed and running (wireless ADB or root)
- No root required — Shizuku provides ADB-level access wirelessly

## Install

1. Download the latest APK from [Releases](https://github.com/OutrageousStorm/aod-suite/releases)
2. Enable *Install from unknown sources* on your device
3. Install the APK
4. Open Shizuku and grant permission to AOD Suite

## How it works

AOD Suite uses Shizuku to execute `settings put` commands that require `WRITE_SECURE_SETTINGS` — a privileged permission that can only be granted via ADB or Shizuku.
No root needed!

## Building from source

```bash
git clone https://github.com/OutrageousStorm/aod-suite
cd aod-suite
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/
```

## Support

If this saved you from rooting your phone → [☕ Ko-fi](https://ko-fi.com/outrageousstorm)

---
MIT License
