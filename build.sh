#!/usr/bin/env bash
# Local build (Linux / macOS). Needs: JDK 17 and the Android SDK (ANDROID_HOME set).
# You do NOT need this if you build on GitHub - the workflow does it for you.
set -e
cd "$(dirname "$0")"

if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ] && [ ! -f local.properties ]; then
  echo "ERROR: Android SDK not found."
  echo "Set ANDROID_HOME to your SDK folder, or create local.properties containing:"
  echo "  sdk.dir=/path/to/Android/Sdk"
  exit 1
fi

chmod +x ./gradlew
./gradlew assembleDebug --stacktrace

mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/CallAlarm.apk
echo
echo "Done! APK is at: dist/CallAlarm.apk"
