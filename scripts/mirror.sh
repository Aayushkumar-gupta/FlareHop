#!/usr/bin/env bash
set -euo pipefail

PHONES=${1:-$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')}
if [ -z "$PHONES" ]; then
    echo "No phones found. Plug them in and accept the USB debugging prompt."
    exit 1
fi

for serial in $PHONES; do
    model=$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')
    # Forwarding audio would mute the phone's own speaker, and Find mode's beeps must come from it.
    scrcpy -s "$serial" --window-title "$model ($serial)" --stay-awake --no-audio &
done
wait
