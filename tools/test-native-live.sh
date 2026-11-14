#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .toolchains/NativeLiveChecks
xcrun swiftc -parse-as-library -module-cache-path .toolchains/SwiftModuleCache \
  iosApp/iosApp/Platform/Speech/NativeLiveSession.swift tools/tests/NativeLiveChecks.swift \
  -o .toolchains/NativeLiveChecks/checks
.toolchains/NativeLiveChecks/checks
