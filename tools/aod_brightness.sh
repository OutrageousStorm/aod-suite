#!/bin/bash
# aod_brightness.sh -- Control AOD minimum brightness via Shizuku
# Usage: aod_brightness.sh [level 0-100]
#        aod_brightness.sh 50   # set AOD to 50%
#        aod_brightness.sh      # show current level

set -e

SETTING="aod_brightness"
PROP="ro.aod.brightness.min"

get_brightness() {
    adb shell "settings get secure $SETTING 2>/dev/null || echo 'not set'"
}

set_brightness() {
    local level=$1
    [[ $level -lt 0 ]] && level=0
    [[ $level -gt 100 ]] && level=100
    
    # Convert 0-100 to 0-255
    local brightness=$((level * 255 / 100))
    
    echo "Setting AOD brightness to $level% (${brightness}/255)"
    adb shell "settings put secure $SETTING $brightness"
    
    # Try to apply immediately
    adb shell "am broadcast -a com.android.aod.BRIGHTNESS_CHANGED --ei brightness $brightness" 2>/dev/null || true
    
    echo "✓ AOD brightness updated. Reboot or toggle AOD to see changes."
}

case "${1:-}" in
    "")
        echo "Current AOD brightness: $(get_brightness)"
        echo "Usage: $0 <0-100>"
        ;;
    *)
        set_brightness "$1"
        ;;
esac
