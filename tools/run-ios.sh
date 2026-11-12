#!/bin/bash
# Run from the user's macOS Terminal, with access to CoreSimulator.
set -euo pipefail
cd "$(dirname "$0")/.."
. ./tools/mobile-env.sh
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

# Select an installed iPhone; prefer one already booted. An explicit UDID is optional.
device_id="${1:-}"
if [ -z "$device_id" ]; then
    device_list=$(xcrun simctl list devices available -j)
    device_id=$(printf '%s' "$device_list" | python3 -c '
import json, sys
data = json.load(sys.stdin)
devices = [d for runtime, entries in data["devices"].items()
           if ".iOS-" in runtime for d in entries
           if d.get("isAvailable") and d["name"].startswith("iPhone")]
devices.sort(key=lambda d: (d["state"] != "Booted", d["name"]))
if not devices:
    sys.exit("No available iPhone simulator. Install an iOS runtime in Xcode Settings > Components and create an iPhone in Devices and Simulators.")
print(devices[0]["udid"])
')
fi

free_kb=$(LC_ALL=C df -Pk . | awk 'END {print $4}')
if [ "$free_kb" -lt 10485760 ]; then
    echo "Insufficient build headroom: free at least 10 GiB before retrying (20 GiB recommended for initial setup)." >&2
    exit 1
fi

# A concrete destination builds only the simulator architecture needed on this Mac.
xcrun xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -configuration Debug -sdk iphonesimulator \
    -destination "platform=iOS Simulator,id=$device_id" \
    -derivedDataPath .toolchains/DerivedData \
    ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build

device_list=$(xcrun simctl list devices -j)
device_state=$(printf '%s' "$device_list" | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
print(next(d["state"] for group in devices.values() for d in group if d["udid"] == sys.argv[1]))
' "$device_id")
if [ "$device_state" != "Booted" ]; then
    xcrun simctl boot "$device_id"
fi
open -a Simulator --args -CurrentDeviceUDID "$device_id"
xcrun simctl bootstatus "$device_id" -b
xcrun simctl install "$device_id" .toolchains/DerivedData/Build/Products/Debug-iphonesimulator/KmmBase.app
xcrun simctl launch "$device_id" com.example.kmmbase.ios
