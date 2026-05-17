# AOD-Suite ADB Settings Reference

Settings exposed by AOD-Suite via Shizuku ContentResolver access.

## Brightness Control
```
Namespace: secure
Key: aod_brightness
Values: 0-255 (brightness level)
Example: settings put secure aod_brightness 128
```

## AOD Enable/Disable
```
Namespace: secure  
Key: aod_enabled
Values: 0 (off), 1 (on)
Example: settings put secure aod_enabled 1
```

## Wallpaper Blur
```
Namespace: secure
Key: aod_background_blur
Values: 0-100 (blur radius percentage)
Example: settings put secure aod_background_blur 80
```

## Display Off Delay
```
Namespace: secure
Key: aod_screen_off_delay_ms
Values: milliseconds (how long to show before dimming)
Example: settings put secure aod_screen_off_delay_ms 10000  # 10 seconds
```

## Always Show Time
```
Namespace: secure
Key: aod_show_time_always
Values: 0 (false), 1 (true)
Example: settings put secure aod_show_time_always 1
```

## Accessibility Features
```
Namespace: secure
Key: aod_tap_to_wake
Values: 0 (disabled), 1 (enabled)
Example: settings put secure aod_tap_to_wake 1
```

---

All settings are applied via Shizuku shell UID with WRITE_SECURE_SETTINGS permission.
Changes take effect immediately or after screen lock/unlock.
