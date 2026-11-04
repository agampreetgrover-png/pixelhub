# PixelHub

A personal all-in-one Android app for a Google Pixel 9a. It does four things:

- **Reminders**: one-off or repeating (daily, chosen weekdays, every N days or weeks), with Done and Snooze buttons on the notification.
- **Alarms**: exact alarms that ring full-screen over the lock screen and sound even in silent mode. Snooze, repeat days, ringtone and vibration are configurable.
- **Apps**: launch any installed app, save shortcuts, add deep-link shortcuts (WhatsApp chat, Maps route, Spotify search…), and pin them to the home screen.
- **Assistant**: type or speak "remind me to call mom at 6pm". Gemini turns it into an action, and you confirm it before anything is created.

It is built with Kotlin, Jetpack Compose, Material 3 (Material You), Room, DataStore and OkHttp. There are no Google Play services and no sign-in.

## Build and install

1. Install **Android Studio**. It includes the Android SDK, `adb` and a JDK 21.
   The Android Gradle plugin needs JDK 17–21. The system Java 25 is too new, so let Android Studio use its bundled JDK.
2. **File → Open** the `pixel9a` folder. Let Gradle sync; the first sync downloads Gradle 8.13 and all the libraries.
3. Prepare the phone:
   - Go to **Settings → About phone** and tap **Build number** 7 times.
   - Go to **Settings → System → Developer options** and turn on **USB debugging**.
   - Plug the phone in and accept the "Allow USB debugging?" prompt.
4. Pick the Pixel in the device dropdown and press **Run ▶**.

To build from the command line, first generate the wrapper once from Android Studio's terminal with `gradle wrapper`. Then use:

```
gradlew test           # unit tests (recurrence math + Gemini response parsing)
gradlew installDebug   # build and install on the connected phone
```

## First launch on the phone

1. Allow notifications when asked.
2. Open **Settings** (the gear on Home) and go through the **Setup checklist**. Every item should show a green check:
   - Alarms & reminders (exact alarms)
   - Full-screen alarms, which let an alarm show over the lock screen
   - Unrestricted battery, so Doze can't delay anything
   - The Alarms notification category is on
3. Optionally, for the assistant: get a key at <https://aistudio.google.com/apikey>, paste it under **Settings → Gemini assistant** and tap Save.
   The key is stored only on the phone. The default model is `gemini-flash-latest`, and you can change it there.

## Checking that it works

- **Alarm**: set an alarm 1–2 minutes ahead, lock the phone and switch to silent. It should light the screen and ring. Snooze should ring again after the chosen number of minutes.
- **Doze**: with USB connected, run `adb shell dumpsys deviceidle force-idle`. A reminder should still fire on time.
- **Reboot**: restart the phone. `adb shell dumpsys alarm | findstr pixelhub` should still list the scheduled items.
- **Assistant**: try "wake me up at 6:30 on weekdays" and "open spotify". Each should show a confirmation card.

## Code map

| Path | What it does |
|---|---|
| `app/src/main/java/com/agam/pixelhub/scheduling/` | `Recurrence.kt` computes the next occurrence (pure, unit-tested). `Scheduler.kt` makes all AlarmManager calls. The receivers handle triggers and reboots. |
| `.../alarm/` | The ringing foreground service and the full-screen alarm activity. |
| `.../notify/` | Notification channels and reminder notifications with Done/Snooze. |
| `.../data/` | Room entities, DAOs and repositories, plus DataStore settings. |
| `.../launcher/` | The installed-app list, launching shortcuts and deep links, and home-screen pinning. |
| `.../ai/` | The Gemini REST client, the prompt and JSON schema, and the response parser. |
| `.../ui/` | Compose screens: Home, Reminders, Alarms, Apps, Assistant and Settings. |
