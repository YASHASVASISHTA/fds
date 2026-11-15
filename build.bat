@echo off
REM Local build (Windows). Needs: JDK 17 and the Android SDK (ANDROID_HOME set).
REM You do NOT need this if you build on GitHub - the workflow does it for you.
cd /d "%~dp0"

if "%ANDROID_HOME%"=="" if "%ANDROID_SDK_ROOT%"=="" if not exist local.properties (
  echo ERROR: Android SDK not found.
  echo Set ANDROID_HOME to your SDK folder, or create local.properties containing:
  echo   sdk.dir=C:\\Users\\YOU\\AppData\\Local\\Android\\Sdk
  exit /b 1
)

call gradlew.bat assembleDebug --stacktrace
if errorlevel 1 exit /b 1

if not exist dist mkdir dist
copy /Y app\build\outputs\apk\debug\app-debug.apk dist\CallAlarm.apk
echo.
echo Done! APK is at: dist\CallAlarm.apk
