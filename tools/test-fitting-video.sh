#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .toolchains/FittingVideoChecks
xcrun swiftc -parse-as-library -module-cache-path .toolchains/SwiftModuleCache \
  iosApp/iosApp/Platform/Camera/FittingVideoGeometry.swift tools/tests/FittingVideoGeometryChecks.swift \
  -o .toolchains/FittingVideoChecks/checks
.toolchains/FittingVideoChecks/checks
