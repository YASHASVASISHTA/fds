# Call Alarm

An alarm app where every alarm rings like an **incoming phone call**.

- Set as many alarms as you want (name, time, repeat days, spoken message).
- When an alarm fires, your phone's **own ringtone** plays and a full-screen **call screen** appears:
  caller name on top, a red button and a green button below.
- The **red button does nothing** (it only shakes). The only way to stop the alarm is to **answer with the green button**.
- After answering you are "on a call": a call timer runs and a voice talks to you
  ("Hello! This is your wake up call..."). If you go quiet it says "Hello? Are you still there?".
- Works over the lock screen, survives reboots, no ads, no internet, no accounts.

---

## Get the APK (no computer setup needed, GitHub builds it)

1. Create a new repository on github.com (e.g. `call-alarm`). Public or private both work.
2. Upload **everything inside this folder** to the repository's `main` branch, **including the hidden `.github` folder**.
   - Easiest with git:
     ```
     cd CallAlarm
     git init
     git add .
     git commit -m "Call Alarm"
     git branch -M main
     git remote add origin https://github.com/YOUR_NAME/call-alarm.git
     git push -u origin main
     ```
   - Or on github.com: *Add file -> Upload files*, then drag the **contents** of the folder in
     (make sure `.github/workflows/build-apk.yml` shows up in the file list).
3. Open the **Actions** tab. The "Build APK" workflow starts by itself (first build takes ~5-8 minutes).
4. When it is green, get the APK from either place:
   - **Releases** (right side of the repo page) -> `latest` -> download **CallAlarm.apk**
     (you can open this page directly on your phone), or
   - the finished workflow run -> **Artifacts** -> `CallAlarm-apk` (zip containing the APK).
5. On your phone open the APK and install it (Android will ask you to allow "install unknown apps" for your browser/Files app once).

Every time you push a change, GitHub rebuilds and refreshes the `latest` release.

If the build fails with a permission error at the "Publish APK" step:
*Repo -> Settings -> Actions -> General -> Workflow permissions -> "Read and write permissions" -> Save*, then re-run.
The APK is still available under Artifacts either way.

## Build locally instead (optional)

Needs JDK 17 and the Android SDK (platform 34).

- Linux / macOS: `./build.sh`
- Windows: `build.bat`

The APK lands in `dist/CallAlarm.apk`.

## First run on the phone

1. Open **Call Alarm**, allow **notifications** when asked.
2. Tap **Test call** (top right) to see exactly what an alarm looks and sounds like.
3. Tap **Add alarm**, pick a time, set a caller name (e.g. "Mom") and optionally what they say, then **Save**.
4. Android 14+: if a yellow banner asks, allow **full-screen calls** (needed for the call screen to appear over the lock screen).

### Make it bulletproof on some phones
Xiaomi / Oppo / Vivo / OnePlus / Samsung can be aggressive about killing apps. If an alarm ever doesn't ring:
- Settings -> Apps -> Call Alarm -> Battery -> **Unrestricted** (or "Don't optimize").
- On Xiaomi/Oppo/Vivo also enable **Autostart** for Call Alarm.
- Keep the **alarm volume** up. (If alarm volume is at 0 the app raises it to 70% when an alarm rings.)

## Customising

- Change what the red button does: `IncomingCallActivity.kt`, `declineBtn.setOnClickListener`.
- Change the ring timeout (default 10 min): `MAX_RING_MS` in `AlarmService.kt`.
- App name: `app/src/main/res/values/strings.xml`. App id: `app/build.gradle.kts`.

## Project layout

```
.github/workflows/build-apk.yml   GitHub builds the APK + publishes the "latest" release
app/src/main/java/com/callalarm/app/
  MainActivity.kt          alarm list + add/edit screen
  IncomingCallActivity.kt  the fake incoming-call / in-call screen
  AlarmService.kt          ringtone + vibration while it rings
  AlarmScheduler.kt        schedules alarms with AlarmManager.setAlarmClock
  AlarmReceiver.kt         fires at alarm time
  BootReceiver.kt          re-schedules alarms after reboot
  AlarmStore.kt / Alarm.kt storage + model
build.sh / build.bat       optional local build scripts
```
