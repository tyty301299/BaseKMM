# SHADOW simulator run - 2026-11-10

Result: blocked by environment. Neither native app was installed or launched; no UI/runtime validation passed.

Baseline: BaseKMM commit ea5ff95 with the current uncommitted SHADOW changes. Xcode reports 27.2 (27B52). Tests/builds target the native demo flows, with no production endpoint or AI request.

- Android: Gradle tests and assembleDebug stop because Java/JDK is missing. Android SDK, adb, emulator and AVDs were not found in PATH, configured environment variables or conventional SDK directories.
- iOS: xcodebuild reaches the Compile Kotlin Framework phase and fails because Java/JDK is missing. CoreSimulator discovery returns connection invalid/refused and permission errors. The Simulator.app launcher at Xcode's conventional path cannot be opened from this session.
- Toolchain setup: the Google Android repository is unreachable because DNS lookup fails. Android SDK/emulator packages could not be downloaded.

Required to resume: JDK 17, Android SDK 35 plus emulator/system image/AVD, and an environment with access to Xcode's Simulator application and CoreSimulator services. The native code remains unverified by a complete build and runtime session.

Command exit codes and raw build/launch/download logs are stored alongside this file in results.json and *.log.
