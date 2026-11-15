#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
APK=app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleDebug -q

PHONES=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [ -z "$PHONES" ]; then
    echo "No phones found. Plug them in and accept the USB debugging prompt."
    exit 1
fi

# Install on every phone at once, so a slow phone doesn't hold up the others.
for serial in $PHONES; do
    (
        model=$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')
        if adb -s "$serial" install -r "$APK" > /dev/null; then
            adb -s "$serial" shell monkey -p com.flarehop 1 > /dev/null 2>&1
            echo "OK    $model ($serial)"
        else
            echo "FAIL  $model ($serial)"
        fi
    ) &
done
wait
