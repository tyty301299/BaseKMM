#!/bin/bash
# Archive or upload an internal-only TestFlight build. Never submits App Store review.
set -euo pipefail
cd "$(dirname "$0")/.."

action="${1:-check}"
case "$action" in check|archive|upload) ;; *) echo "Usage: $0 [check|archive|upload]" >&2; exit 2 ;; esac
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
xcode_major=$(xcrun xcodebuild -version | awk '/^Xcode / {split($2,v,"."); print v[1]}')
if [ -z "$xcode_major" ] || [ "$xcode_major" -lt 26 ]; then
    echo "TestFlight requires Xcode 26+ and iOS SDK 26+. This Mac has Xcode ${xcode_major:-unknown}." >&2
    exit 1
fi
sdk_version=$(xcrun --sdk iphoneos --show-sdk-version)
if [ "${sdk_version%%.*}" -lt 26 ]; then
    echo "iOS SDK 26+ is required; found $sdk_version." >&2; exit 1
fi
. ./tools/mobile-env.sh

team_id="${APPLE_TEAM_ID:-GJD9MS8W54}"
bundle_id="${IOS_BUNDLE_ID:-com.trioxstudios.shadow}"
build_number="${BUILD_NUMBER:-1}"
release_version="${RELEASE_VERSION:-1.0.0}"
auth_args=(-allowProvisioningUpdates)
if [ -n "${ASC_KEY_PATH:-}${ASC_KEY_ID:-}${ASC_ISSUER_ID:-}" ]; then
    : "${ASC_KEY_PATH:?Set ASC_KEY_PATH to the App Store Connect .p8 key outside the repository}"
    : "${ASC_KEY_ID:?Set ASC_KEY_ID}"
    : "${ASC_ISSUER_ID:?Set ASC_ISSUER_ID}"
    test -r "$ASC_KEY_PATH"
    auth_args+=(-authenticationKeyPath "$ASC_KEY_PATH" -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID")
fi
curl --fail --silent --show-error --max-time 20 https://hackathon.trioxstudios.com/api/health >/dev/null
echo "Xcode/SDK, Java and backend checks passed. Team: $team_id. Bundle: $bundle_id."
if [ "$action" = check ]; then
    echo "Signing/account access and the App Store Connect app record must also be available before upload."
    exit 0
fi

output_dir="$PWD/.toolchains/TestFlight/$(date -u +%Y%m%d-%H%M%S)"
mkdir -p "$output_dir"
xcrun xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -configuration Release -destination 'generic/platform=iOS' \
    -archivePath "$output_dir/SHADOW.xcarchive" \
    -derivedDataPath "$PWD/.toolchains/DerivedData-TestFlight" \
    "${auth_args[@]}" \
    DEVELOPMENT_TEAM="$team_id" PRODUCT_BUNDLE_IDENTIFIER="$bundle_id" \
    CURRENT_PROJECT_VERSION="$build_number" MARKETING_VERSION="$release_version" archive

if [ "$action" = archive ]; then
    echo "Archive ready: $output_dir/SHADOW.xcarchive"
    exit 0
fi
python3 - "$output_dir/ExportOptions.plist" "$team_id" <<'PY'
import plistlib, sys
with open(sys.argv[1], 'wb') as output:
    plistlib.dump({
        'method': 'app-store-connect',
        'destination': 'upload',
        'teamID': sys.argv[2],
        'signingStyle': 'automatic',
        'manageAppVersionAndBuildNumber': True,
        'uploadSymbols': True,
        'testFlightInternalTestingOnly': True,
    }, output)
PY
xcrun xcodebuild -exportArchive -archivePath "$output_dir/SHADOW.xcarchive" \
    -exportPath "$output_dir/export" -exportOptionsPlist "$output_dir/ExportOptions.plist" \
    "${auth_args[@]}"
echo "Upload completed. Wait for Apple processing, then add the build to an internal TestFlight group."
