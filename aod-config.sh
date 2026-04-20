#!/bin/bash
# aod-config.sh -- Configure Always On Display settings via ADB without app
# Requires: Android 10+, Shizuku

set -e

DEVICE="$1"
if [[ -z "$DEVICE" ]]; then
    DEVICE=$(adb devices | grep device$ | awk '{print $1}')
fi

echo "🌙 AOD Configuration Tool"
echo "Device: $DEVICE"
echo ""
echo "What would you like to configure?"
echo "  1) Minimum brightness level"
echo "  2) Enable/disable tap to show"
echo "  3) Set display time"
echo "  4) Show current settings"
read -rp "Choice [1-4]: " CHOICE

adb -s "$DEVICE" shell "
case $CHOICE in
  1)
    read -rp 'Brightness (0-255): ' BRIGHT
    settings put secure aod_tap_brightness \$BRIGHT
    echo 'Set to '\$BRIGHT
    ;;
  2)
    read -rp 'Enable? (1=yes, 0=no): ' ENABLE
    settings put secure aod_tap_to_show \$ENABLE
    ;;
  3)
    read -rp 'Display time (seconds): ' TIME
    settings put secure aod_screen_timeout \$TIME
    ;;
  4)
    echo 'Current AOD settings:'
    settings get secure aod_tap_brightness
    settings get secure aod_tap_to_show
    settings get secure aod_screen_timeout
    ;;
esac
"
