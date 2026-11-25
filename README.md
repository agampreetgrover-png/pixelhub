# PixelHub

A personal all-in-one Android app for Google Pixel phones. Everything works by hand first, and an assistant can do the same things from a sentence.

It is meant for any Pixel on Android 15 or later (Pixel 6 and newer), but it has only been tested on a **Pixel 9a**.

- **Reminders**: one-off or repeating (daily, chosen weekdays, every N days or weeks), with Done and Snooze buttons on the notification. Swipe to delete, with Undo.
- **Alarms**: exact alarms that ring full-screen over the lock screen, even in silent mode. Snooze, repeat days, ringtone and vibration are configurable. Swipe an alarm left to delete it (or use Delete in its editor), with Undo.
- **Money**: log expenses and income, set a monthly budget and per-category budgets, filter by category, and see where the money went each month. Hold **Add** (or **Income**) to repeat the last entry.
- **People**: who owes whom, under **Money → People**. Record "I paid" or "they paid" for each person and PixelHub keeps the running balance, with history, swipe-to-delete with Undo, and a **Send a reminder** button that opens WhatsApp or SMS with what they owe. Or tell the assistant "paid Neha 30", "rahul ko 50 diye", "Neha paid me 30" or "neha ka hisaab". It is kept apart from Expenses, so lending or being paid back never changes your spending or budgets.
- **Health**: reads steps, distance, active calories and exercise from Health Connect, shows today and the past week against a daily step goal, and can send nudges, e.g. a reminder to walk when you're behind.
- **Calendar**: shows your upcoming Google Calendar events, read from the calendars synced to the phone, so no sign-in is needed. It can also write your reminders and alarms into a calendar you pick.
- **Notes**: quick notes with colours, pinning and a formatting bar for headings, bold, italic, strikethrough, bullets and checklists.
- **Assistant**: type or speak "remind me to call mom at 6pm", "spent 250 on lunch", "add 30 to food" or Hinglish like "kal subah 7 baje dawai yaad dilana". It is not an AI model: regular expressions on the phone match the request, and only for a fixed set of operations. These are reminders, alarms, expenses and income, the People ledger, opening apps ("open spotify", "whatsapp kholo"), calls, texts, navigation and web search. A match becomes a draft card that you confirm before anything is created. This takes well under a millisecond and needs no internet. Anything outside those operations gets an immediate "I didn't understand that". Every request is logged under **Settings → Assistant log**.
- **Widgets**: home-screen widgets for Health, Reminders, Alarms, Money and Calendar, each with a layout for every size from 1×1 to 4×4. They use your PixelHub theme. You can tick off a reminder and switch an alarm on or off straight from the home screen; tapping anywhere else opens that section. They update the moment their data changes while PixelHub is running, and every 30 minutes otherwise. A **Now playing** widget shows what Spotify or any other player is playing, with live times and controls, and follows song changes as they happen.

**Home** greets you by name and gathers it all:
- a swipeable alarm and reminder card deck;
- quick actions;
- today's items, upcoming events and the month's money and health;
- an assistant button with the PixelHub logo, above the right end of the tab bar, which opens the Assistant page.

There are 10 themes (System, Light, Dark, Black, Midnight, Forest, Plum, Ocean, Rose, Sand). Switching between them crossfades.

The app icon is the tab bar's black pill holding a violet (alarms) and a yellow (reminders) button, on the app's light grey. With Pixel's **Themed icons** turned on, it shows a one-colour version with the two buttons cut out.

It is built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, Health Connect and Jetpack Glance. There are no Google Play services, no account sign-in and no internet permission: nothing leaves the phone.

## Build and install

1. Clone the repo (`git clone https://github.com/agampreetgrover-png/pixelhub.git`) and install **Android Studio**. It includes the Android SDK, `adb` and a JDK 21.
   The Android Gradle plugin needs JDK 17–21. Java 25 is too new, so use Android Studio's bundled JDK.
2. **File → Open** the cloned folder and let Gradle sync.
3. Prepare the phone:
   - Go to **Settings → About phone** and tap **Build number** 7 times.
   - Go to **Settings → System → Developer options** and turn on **USB debugging**.
   - Plug the phone in and accept the prompt.
4. Pick the Pixel in the device dropdown and press **Run ▶**.

From the command line, point `JAVA_HOME` at a JDK 17–21 first (Android Studio's bundled one is the `jbr` folder in its install directory). Then:

```
gradlew test           # unit tests: recurrence, assistant parsing (English and Hinglish), expenses, People ledger, calendar, nudges, themes
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
5. Pick a theme under **Appearance**.

## Checking that it works

- **Alarm**: set an alarm 1–2 minutes ahead, lock the phone and switch to silent. It should light the screen and ring. Snooze should ring again after the chosen number of minutes.
- **Doze**: with USB connected, run `adb shell dumpsys deviceidle force-idle`. A reminder should still fire on time.
- **Reboot**: restart the phone. `adb shell dumpsys alarm | findstr pixelhub` should still list the scheduled items.
- **Assistant**: try "wake me up at 6:30 on weekdays", "spent 250 on lunch", "kal subah 7 baje dawai yaad dilana" and "open spotify". Each should show a confirmation card at once. "what is the weather" should get an immediate "I didn't understand that". Both show up under **Settings → Assistant log**, with the rule that matched and how long it took. Type "help" for everything it understands.
- **People**: in **Money → People**, add someone, tap **I paid** for ₹30 and then **they paid** for ₹30. The balance should go to ₹30 and then "All square". In the assistant, "paid Neha 30" should show a card, and "neha ka hisaab" should answer at once. Your Expenses totals shouldn't change.
- **Widgets**: long-press the home screen, open **Widgets → PixelHub** and add each one. Resize them: the layout should change at each size. Tick a reminder and switch an alarm from the widget, then open the app: both should match. With music playing, skip a song in the player: **Now playing** should change within a second or two.
- **Open a tab directly**:

  ```
  adb shell am start -f 0x14000000 -n com.agam.pixelhub/.MainActivity --es com.agam.pixelhub.extra.TAB EXPENSES
  ```

  The tabs are `HOME`, `REMINDERS`, `ALARMS`, `EXPENSES`, `PEOPLE`, `HEALTH`, `CALENDAR`, `NOTES`, `ASSISTANT` and `SETTINGS`.

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
| `.../ledger/` | The People ledger: `LedgerRepository` and `LedgerMath` (balances, history and the share message). |
| `.../ai/` | The rule-based assistant. `Lexicon.kt` holds every English and Hinglish keyword (add new words there). `DateTimeRules.kt` reads times, dates and repeats. `CommandParser.kt` picks the action. `AssistantLog.kt` keeps the request log. |
| `.../widget/` | Home-screen widgets (Jetpack Glance). `kit/WidgetKit.kt` holds the shared theme, card, text and ring; one file per widget; `WidgetSync.kt` redraws them when their data changes. |
| `.../ui/` | Compose screens: Home, Reminders, Alarms, Money, People, Health, Calendar, Assistant and Settings. |
| `.../ui/theme/` | Colour tokens (`HubColors`), the 10 themes, and Poppins type. |
| `.../ui/motion/` | One shared motion vocabulary: press feedback, springs, entrance stagger, rolling numbers, shape morphs and the small helpers in `Micro.kt`. |

## License

MIT, see [LICENSE](LICENSE). The Poppins font is under the SIL Open Font License, see [licenses/fonts/Poppins-OFL.txt](licenses/fonts/Poppins-OFL.txt).
