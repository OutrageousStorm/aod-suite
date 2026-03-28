# Changelog

## v0.2.0 (Planned)
- [ ] AOD schedule (on/off times)
- [ ] Per-profile brightness presets
- [ ] Clockface selector via ADB settings
- [ ] Battery level threshold auto-disable

## v0.1.0-wip — Initial WIP Release
- Shizuku UserService integration for shell-level ADB access
- AOD minimum brightness control via `settings put secure aod_tap_to_show_screen 1`
- Always On Display toggle (enable/disable)
- Blurred wallpaper AOD background (uses WallpaperManager + RenderScript blur)
- AIDL IPC for privilege escalation through Shizuku binder
- Direct ContentResolver.putString for secure settings (bypasses SecurityException)

## Known issues
- Blurred wallpaper: only works on devices where AOD supports custom bitmaps
- Samsung One UI: some AOD settings are in a different namespace
- Reboot required for some AOD changes to take visual effect
