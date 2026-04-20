#!/usr/bin/env python3
"""
aod-config.py -- Configure AOD settings via ADB
Companion to AOD Suite app for advanced settings.
Usage: python3 aod-config.py --brightness 100 --show-clock --no-tap-to-wake
"""
import subprocess, argparse

def adb(cmd):
    return subprocess.run(f"adb shell {cmd}", shell=True, capture_output=True, text=True).stdout.strip()

AOD_SETTINGS = {
    'show_clock': ('secure', 'doze_always_on', '1'),
    'show_notifications': ('secure', 'aod_notifications', '1'),
    'show_time': ('secure', 'aod_time_format', '1'),
    'tap_to_wake': ('secure', 'aod_tap_to_show_screen', '1'),
    'always_on': ('global', 'aod_enabled', '1'),
}

def set_setting(name, value):
    if name not in AOD_SETTINGS:
        print(f"Unknown setting: {name}")
        return False
    namespace, key, _ = AOD_SETTINGS[name]
    adb(f"settings put {namespace} {key} {1 if value else 0}")
    print(f"✓ {name} = {value}")
    return True

def main():
    parser = argparse.ArgumentParser(description="Configure AOD settings")
    parser.add_argument("--brightness", type=int, help="Minimum brightness 0-255")
    parser.add_argument("--show-clock", action="store_true")
    parser.add_argument("--show-notifications", action="store_true")
    parser.add_argument("--show-time", action="store_true")
    parser.add_argument("--no-tap-to-wake", action="store_true")
    parser.add_argument("--enable", action="store_true", help="Enable AOD")
    parser.add_argument("--disable", action="store_true", help="Disable AOD")
    args = parser.parse_args()

    print("\n🌙 AOD Suite Configuration\n")

    if args.brightness is not None:
        adb(f"settings put secure aod_brightness {args.brightness}")
        print(f"✓ brightness = {args.brightness}")

    if args.show_clock:
        set_setting('show_clock', True)
    if args.show_notifications:
        set_setting('show_notifications', True)
    if args.show_time:
        set_setting('show_time', True)

    if args.no_tap_to_wake:
        set_setting('tap_to_wake', False)

    if args.enable:
        adb("settings put global aod_enabled 1")
        print("✓ AOD enabled")
    if args.disable:
        adb("settings put global aod_enabled 0")
        print("✓ AOD disabled")

    if not any([args.brightness, args.show_clock, args.show_notifications,
                args.show_time, args.no_tap_to_wake, args.enable, args.disable]):
        parser.print_help()

if __name__ == "__main__":
    main()
