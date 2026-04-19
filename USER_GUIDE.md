# AOD Suite — User Guide

Complete walkthrough for Android Always On Display customization via Shizuku.

## Requirements

1. **Device** — Any Android 11+ with Always On Display support (Pixel 6+, Samsung S21+, etc.)
2. **Shizuku** — Download from https://shizuku.rikka.app
3. **AOD Suite** — Install from [releases](https://github.com/OutrageousStorm/aod-suite/releases)

## First Run Setup

### 1. Enable Shizuku
- Download Shizuku APK from official site
- Install: `adb install Shizuku-x.x.x.apk`
- Enable Shizuku:
  ```bash
  adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/start.sh
  ```
- Grant permission when prompt appears on device

### 2. Grant AOD Suite Permission
- Open AOD Suite app
- Tap "Request Shizuku Access"
- Confirm in Shizuku app notification

### 3. Tap the Setup Button
- AOD Suite shows a "Setup" button on first launch
- This runs: `pm grant com.outrageousstorm.aodsuite android.permission.WRITE_SECURE_SETTINGS`
- One-time operation — after this, AOD controls work

## Using AOD Suite

### Brightness Slider
- Adjust from 0 (off) to 255 (full brightness)
- Changes take effect immediately when screen is off
- Persistent across reboots

### Toggle AOD
- **On/Off button** — enables or disables Always On Display entirely
- Persists when device reboots

### Background Options (Future)
- Blurred wallpaper (current implementation)
- Custom image
- Solid color
- Gradient

## Settings Modified

All changes are stored in Android's secure settings namespace:

```
aod_tap_to_show_screen = 1    # minimum brightness level
aod_enabled = true/false      # AOD on/off
```

View current settings:
```bash
adb shell settings get secure aod_tap_to_show_screen
adb shell settings get secure aod_enabled
```

## Troubleshooting

| Issue | Fix |
|-------|-----|
| "Shizuku not found" | Re-run Shizuku setup, restart AOD Suite |
| Brightness not changing | Restart device, re-grant permission |
| Blurred background doesn't show | Not supported on your device; use solid color instead |
| AOD disappears after reboot | Disable → re-enable to reset |

## What Shizuku Does

Shizuku grants the app WRITE_SECURE_SETTINGS privilege without requiring root. This allows:
- Reading/writing secure device settings
- Modifying Always On Display behavior
- Accessing hardware display APIs

Without Shizuku, these would require:
- Full root access (Magisk/KernelSU)
- SELinux policy modification
- Bootloader unlock

Shizuku bridges the gap with ADB-level access from within the app.

## Source & Issues
- GitHub: https://github.com/OutrageousStorm/aod-suite
- Report bugs: https://github.com/OutrageousStorm/aod-suite/issues
