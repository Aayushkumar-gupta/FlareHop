#!/usr/bin/env bash
set -euo pipefail

SERIAL=${1:?"Give the serial of the phone to record (see: adb devices)"}
OUT_DIR="$HOME/Movies/flarehop"
COPY_DIR="$HOME/Desktop"
FILE="backup-$(date +%Y%m%d-%H%M).mp4"
mkdir -p "$OUT_DIR"

AUDIO=(--no-audio)
SDK=$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')
# Recording audio while the phone keeps beeping needs Android 13 (API 33) or newer.
if [ "${SDK:-0}" -ge 33 ]; then
    AUDIO=(--audio-source=playback --audio-dup --no-audio-playback)
fi

echo "Recording to $OUT_DIR/$FILE. Close the window or press Ctrl+C to stop."
# Ctrl+C must stop scrcpy only, so the copy below still runs.
trap 'true' INT
scrcpy -s "$SERIAL" --stay-awake --record="$OUT_DIR/$FILE" "${AUDIO[@]}" || true
trap - INT

cp "$OUT_DIR/$FILE" "$COPY_DIR/$FILE"
echo "Saved twice: $OUT_DIR/$FILE and $COPY_DIR/$FILE"
