#!/bin/bash
# sideload.sh -- Easy sideload APK from releases
# Usage: ./sideload.sh [version]  (default: latest)
set -e

REPO="OutrageousStorm/aod-suite"
VERSION="${1:-latest}"

echo "🚀 AOD Suite Sideloader"
echo "━━━━━━━━━━━━━━━━━━━━━"

if [[ "$VERSION" == "latest" ]]; then
  URL=$(curl -s "https://api.github.com/repos/$REPO/releases/latest" | grep -o '"browser_download_url"[^"]*"[^"]*"' | tail -1 | cut -d'"' -f4)
  [[ -z "$URL" ]] && echo "❌ No releases found" && exit 1
  echo "Latest release: $(echo $URL | rev | cut -d'/' -f1 | rev)"
else
  URL="https://github.com/$REPO/releases/download/$VERSION/AOD-Suite-${VERSION}.apk"
fi

APK=$(mktemp).apk
echo "Downloading..."
curl -sL "$URL" -o "$APK"

echo "Installing..."
adb install "$APK"

rm "$APK"
echo "✅ Done!"
