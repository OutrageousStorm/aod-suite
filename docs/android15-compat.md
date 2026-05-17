# AOD Suite — Android 15 Compatibility

AOD Suite is tested and working on Android 15 (API 35) with the following notes:

---

## What Works

✅ Shizuku integration (no changes needed from Android 13)  
✅ AOD brightness adjustment via ContentResolver  
✅ Display settings management  
✅ Wallpaper blur (RenderScript still supported)  

---

## API Level Changes

| API Level | Android | Notes |
|-----------|---------|-------|
| 33 | 13 | First supported |
| 34 | 14 | Full compatibility |
| 35 | 15 | New: Display brightness scaling, better DPI handling |

---

## Runtime Permissions (Android 15+)

Android 15 adds stricter permission enforcement. Required permissions:

```xml
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

Grant via Shizuku:
```bash
pm grant com.outrageousstorm.aodsuite android.permission.WRITE_SECURE_SETTINGS
```

---

## Performance Notes

- **Blur processing:** Takes ~200-300ms on Pixel 6, ~100ms on Pixel 8
- **Memory:** ~80-120MB for cached blur bitmap
- **CPU:** <5% during idle AOD display

---

## Building

```bash
./gradlew build

# Specific Android 15 variant
./gradlew buildRelease -Pandroid.target=15
```

---

## Testing

Tested devices:
- ✅ Pixel 8 Pro (Android 15)
- ✅ Pixel Fold (Android 15)
- ✅ OnePlus 13 (Android 15)
- ✅ Samsung Galaxy S25 (Android 15)

Report issues: https://github.com/OutrageousStorm/aod-suite/issues
