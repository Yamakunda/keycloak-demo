#!/usr/bin/env bash
# Giả lập chạm cảm biến vân tay trên Android Emulator liên tục cho tới khi bấm Ctrl+C.
# Dùng lúc emulator đang hiện màn hình chờ vân tay (đăng ký hoặc BiometricPrompt lúc unlock).
#
# Cách dùng:
#   ./touch-fingerprint.sh            # tự tìm emulator đang chạy
#   ./touch-fingerprint.sh emulator-5556   # chỉ định rõ serial nếu có nhiều emulator

set -euo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [ ! -x "$ADB" ]; then
  ADB="adb" # fallback nếu adb đã có trong PATH
fi

SERIAL="${1:-}"
if [ -z "$SERIAL" ]; then
  SERIAL=$("$ADB" devices | awk '/^emulator-/{print $1; exit}')
fi

if [ -z "$SERIAL" ]; then
  echo "Không tìm thấy emulator nào đang chạy (adb devices trống)." >&2
  exit 1
fi

echo "Đang chạm cảm biến vân tay mỗi giây trên $SERIAL — bấm Ctrl+C để dừng."
while true; do
  "$ADB" -s "$SERIAL" emu finger touch 1
  sleep 1
done
