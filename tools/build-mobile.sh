#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
. ./tools/mobile-env.sh
case "${1:-}" in
    android) exec ./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug ;;
    ios)
        export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
        exec /usr/bin/xcrun xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
            -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
            -derivedDataPath .toolchains/DerivedData CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build
        ;;
    *) echo "Usage: ./tools/build-mobile.sh android|ios" >&2; exit 2 ;;
esac
