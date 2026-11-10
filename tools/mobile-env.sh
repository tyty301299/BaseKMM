#!/bin/sh
# Source from the BaseKMM repository root. No global shell configuration changes.
SHADOW_PROJECT_ROOT=$(pwd -P)
if [ ! -f "$SHADOW_PROJECT_ROOT/settings.gradle.kts" ]; then
    echo "Run from the BaseKMM repository root." >&2
    return 1
fi
if [ -f "$SHADOW_PROJECT_ROOT/.toolchains/env.sh" ]; then
    . "$SHADOW_PROJECT_ROOT/.toolchains/env.sh"
fi
if [ -z "${JAVA_HOME:-}" ] && [ -x "$SHADOW_PROJECT_ROOT/.toolchains/jdk/Contents/Home/bin/javac" ]; then
    JAVA_HOME="$SHADOW_PROJECT_ROOT/.toolchains/jdk/Contents/Home"
fi
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null) || {
        echo "JDK 17 missing. Run: python3 tools/setup_mobile_env.py --with-emulator" >&2
        return 1
    }
fi
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
    echo "JAVA_HOME does not contain a JDK: $JAVA_HOME" >&2
    return 1
fi
case "$("$JAVA_HOME/bin/javac" -version 2>&1)" in
    'javac 17.'*) ;;
    *) echo "This project requires JDK 17. Check JAVA_HOME or run tools/setup_mobile_env.py." >&2; return 1 ;;
esac
export JAVA_HOME
if [ -z "${ANDROID_HOME:-}" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    ANDROID_HOME=$ANDROID_SDK_ROOT
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"
fi
if [ -n "${ANDROID_HOME:-}" ]; then
    export ANDROID_HOME
    PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
fi
PATH="$JAVA_HOME/bin:$PATH"
export PATH
