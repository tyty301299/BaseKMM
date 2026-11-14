#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .toolchains/NativeLiveChecks
xcrun swiftc -parse-as-library -module-cache-path .toolchains/SwiftModuleCache \
  iosApp/iosApp/Platform/Speech/NativeLiveSession.swift \
  iosApp/iosApp/Platform/Speech/NativeLessonSession.swift tools/tests/NativeLessonChecks.swift \
  -o .toolchains/NativeLiveChecks/lessons
.toolchains/NativeLiveChecks/lessons
