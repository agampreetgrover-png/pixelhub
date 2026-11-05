# PixelHub

A personal all-in-one Android app for a Google Pixel 9a. Everything works by hand first, and an assistant can do the same things from a sentence.

- **Reminders**: one-off or repeating (daily, chosen weekdays, every N days or weeks), with Done and Snooze buttons on the notification. Swipe to delete, with Undo.
- **Alarms**: exact alarms that ring full-screen over the lock screen, even in silent mode. Snooze, repeat days, ringtone and vibration are configurable. Deleting one offers Undo.
- **Money**: log expenses and income, set a monthly budget and per-category budgets, filter by category, and see where the money went each month. Hold **Add** (or **Income**) to repeat the last entry.
- **Health**: reads steps, distance, active calories and exercise from Health Connect, shows today and the past week against a daily step goal, and can send nudges, e.g. a reminder to walk when you're behind.
- **Calendar**: shows your upcoming Google Calendar events, read from the calendars synced to the phone, so no sign-in is needed. It can also write your reminders and alarms into a calendar you pick.
- **Assistant**: type or speak "remind me to call mom at 6pm" or "got paid 5000 salary". The request becomes a draft card that you confirm before anything is created. It also opens installed apps ("open spotify"). It works with Gemini, Groq or xAI Grok, using your own API key.

**Home** gathers it all:
- a swipeable alarm and reminder card deck;
- quick actions;
- today's items, upcoming events and the month's money and health;
- the **Ask PixelHub anything** bar, which sits just above the tab bar.

There are 10 themes (System, Light, Dark, Black, Midnight, Forest, Plum, Ocean, Rose, Sand). Switching between them crossfades.

It is built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, OkHttp and Health Connect. There are no Google Play services and no account sign-in.

## Build and install

1. Install **Android Studio**. It includes the Android SDK, `adb` and a JDK 21.
   The Android Gradle plugin needs JDK 17–21. Java 25 is too new, so use Android Studio's bundled JDK.
2. **File → Open** the `pixel9a` folder and let Gradle sync.
3. Prepare the phone:
   - Go to **Settings → About phone** and tap **Build number** 7 times.
   - Go to **Settings → System → Developer options** and turn on **USB debugging**.
   - Plug the phone in and accept the prompt.
4. Pick the Pixel in the device dropdown and press **Run ▶**.

From the command line, point `JAVA_HOME` at a JDK 17–21 first. On this machine that is `%LOCALAPPDATA%\Android\jdk-21`. Then:

```
gradlew test           # unit tests: recurrence, assistant parsing, expenses, calendar, nudges, themes
gradlew installDebug   # build and install on the connected phone
```

## First launch on the phone

1. Allow notifications when asked.
2. Open **Settings** (top right on Home) and work through **Alarms & reminders**. Every item should show a green check:
   - Alarms & reminders (exact alarms)
   - Full-screen alarms, which let an alarm show over the lock screen
   - Unrestricted battery, so Doze can't delay anything
   - The Alarms notification category is on
3. **Calendar**: allow calendar access to see your events on Home and the Calendar page. Under **Calendar sync** you can pick a calendar for PixelHub to write to.
4. **Health**: tap **Connect** and allow Health Connect access. Turn on **Background reading** if you want nudges while the app is closed.
5. **Assistant** (optional): add a key under **Settings → Assistant**. Keys are stored only on the phone.
   - Gemini: <https://aistudio.google.com/apikey>. The default model is `gemini-flash-latest`.
   - Groq: <https://console.groq.com/keys>. Tap a model chip to pick one.
6. Pick a theme under **Appearance**.

## Checking that it works

- **Alarm**: set an alarm 1–2 minutes ahead, lock the phone and switch to silent. It should light the screen and ring. Snooze should ring again after the chosen number of minutes.
- **Doze**: with USB connected, run `adb shell dumpsys deviceidle force-idle`. A reminder should still fire on time.
- **Reboot**: restart the phone. `adb shell dumpsys alarm | findstr pixelhub` should still list the scheduled items.
- **Assistant**: try "wake me up at 6:30 on weekdays", "spent 250 on lunch" and "open spotify". Each should show a confirmation card. Long requests can be stopped, and failed ones retried.
- **Open a tab directly**:

  ```
  adb shell am start -f 0x14000000 -n com.agam.pixelhub/.MainActivity --es com.agam.pixelhub.extra.TAB EXPENSES
  ```

  The tabs are `HOME`, `REMINDERS`, `ALARMS`, `EXPENSES`, `HEALTH`, `CALENDAR`, `ASSISTANT` and `SETTINGS`.

## Accessibility

- Text scales with the system font size. Long values shrink to fit rather than being cut off.
- Touch targets are at least 48dp.
- Turning on **Remove animations** in Android's accessibility settings drops all movement and keeps only short fades.

## Code map

| Path | What it does |
|---|---|
| `app/src/main/java/com/agam/pixelhub/scheduling/` | `Recurrence.kt` computes the next occurrence (pure, unit-tested). `Scheduler.kt` makes all the AlarmManager calls. The receivers handle triggers and reboots. |
| `.../alarm/` | The ringing foreground service and the full-screen alarm activity. |
| `.../notify/` | Notification channels, and reminder notifications with Done/Snooze. |
| `.../data/` | Room entities, DAOs and repositories (with migrations), plus DataStore settings. |
| `.../expenses/` | Monthly summaries, budgets, and the expense/income split. |
| `.../health/` | Health Connect reads, nudge rules, and the privacy-policy activity Health Connect requires. |
| `.../calendar/` | Reading upcoming events (`CalendarEvents.kt`) and writing reminders and alarms to a calendar (`CalendarSync.kt`). |
| `.../launcher/` | The installed-app list and launching apps for the assistant. |
| `.../ai/` | The Gemini client, the Groq/xAI client, the prompt, and the command parser. |
| `.../ui/` | Compose screens: Home, Reminders, Alarms, Money, Health, Calendar, Assistant and Settings. |
| `.../ui/theme/` | Colour tokens (`HubColors`), the 10 themes, and Poppins type. |
| `.../ui/motion/` | One shared motion vocabulary: press feedback, springs, entrance stagger, rolling numbers, shape morphs and the small helpers in `Micro.kt`. |
