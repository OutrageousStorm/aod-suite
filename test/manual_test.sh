#!/bin/bash
# manual_test.sh -- Test AOD-Suite functionality via ADB shell
# Usage: ./manual_test.sh

set -e
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

echo -e "\n${YELLOW}🌙 AOD-Suite Manual Test${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

adb shell pm grant com.outrageousstorm.aodsuite android.permission.WRITE_SECURE_SETTINGS 2>/dev/null && \
    echo -e "${GREEN}✓${NC} Permissions granted via pm" || \
    echo -e "${RED}✗${NC} Permission grant failed"

# Test 1: Check if AOD is enabled
echo -e "\n${YELLOW}Test 1: AOD Status${NC}"
status=$(adb shell settings get secure aod_mode)
echo "  aod_mode = $status"

# Test 2: Set AOD brightness
echo -e "\n${YELLOW}Test 2: Setting brightness to 50${NC}"
adb shell settings put secure aod_brightness_level 50
result=$(adb shell settings get secure aod_brightness_level)
[[ "$result" == "50" ]] && echo -e "  ${GREEN}✓${NC} Set to $result" || echo -e "  ${RED}✗${NC} Got $result"

# Test 3: Toggle AOD
echo -e "\n${YELLOW}Test 3: Toggling AOD${NC}"
adb shell settings put secure aod_mode 1
sleep 2
adb shell settings put secure aod_mode 0
echo -e "  ${GREEN}✓${NC} Toggle tested"

# Test 4: Check Shizuku service
echo -e "\n${YELLOW}Test 4: Shizuku service status${NC}"
service=$(adb shell service list 2>/dev/null | grep -i shizuku || echo "not found")
[[ "$service" != "not found" ]] && echo -e "  ${GREEN}✓${NC} Shizuku active" || echo -e "  ${RED}⚠${NC} Shizuku not detected"

echo -e "\n━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}Tests complete${NC}"
