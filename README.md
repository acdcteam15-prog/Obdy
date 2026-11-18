# Obdy

Car diagnostics on your Android phone for the **Chevrolet Malibu 2013 LT (GCC)**, built with the same toolchain and flow as Remoty:
Kotlin + Compose, built on GitHub Actions, updated from GitHub Releases.

```
phone (Obdy)  <== Bluetooth / Bluetooth LE / Wi-Fi ==>  OBD-II adapter (ELM327 type)  <==>  car
```

## What it does
- **Connect**: Bluetooth (paired), Bluetooth LE, **USB cable (OTG)**, or Wi-Fi adapter. Reads the VIN and which live values the car supports.
- **Live data**: round gauges (rpm, speed, coolant, load, fuel trims, battery voltage, air flow, throttle, and more; you choose).
  GM extras (transmission fluid temperature, oil life) are experimental and shown only if the module answers.
- **Faults**: quick scan (engine + transmission: stored, pending, permanent codes) or **deep scan** (also looks for ABS, airbag, body and
  other modules). Each code has a plain description, urgency and likely causes (built in, works offline). Freeze frame, warning-light state and
  readiness monitors. **Clear codes** with a clear warning and the scan saved first.
- **AI services** (Settings): add several (Google Gemini, Anthropic Claude, OpenAI, OpenRouter, Groq, your own Ollama/LM Studio, any OpenAI-compatible service),
  pick the active one, and press **Test** on each: it asks one tiny question and tells you if the key, model name and connection work.
- **AI analysis**: reasons over codes + freeze frame + live values + your notes. Or copy the data as text and paste it to Claude.
- **Find the cause (questions)**: pick a code, answer simple questions (what you already replaced or cleaned, when it happens, how the car feels). Built-in
  suggestions update at once; the AI then asks a few short follow-up questions of its own and gives the solution, including cleaning jobs and other causes
  when the obvious part did not fix it.
- **Where is the part?**: a top-view car diagram that lights up the area of each suspect part (red = usual suspects, amber = AI estimate), with a description of where to look.
- **Online checks**: ready web and YouTube searches for your code and car, AI research (Gemini can really search Google), and official recalls/complaints (NHTSA).
- **Hide a code in Obdy**: non-urgent codes can be hidden from the lists. Obdy never changes the car's software; misfire, overheating, brake and airbag codes cannot be hidden.
- **Battery and charging test**: resting voltage, dip while cranking, charging at idle and at 2000 rpm, with plain verdicts.
- **Fuel and trips**: litres/100 km, cost, trip history (estimated from the air flow sensor).
- **Recordings and graphs**: record a drive, graph it, compare two drives on one scale, share as CSV.
- **My car**: profile (VIN, mileage, tank, notes) and **service reminders** (oil, filters, brakes, fluids, plugs, battery, A/C...) with a notification when overdue.
- **PDF report** to show a mechanic.
- **Console**: send raw commands and share the full log (this is how to send me what your car answers).
- **Adapter doctor**: tests the adapter (identity, protocol, voltage, speed, multi-value support). Run it the day the adapter arrives.
- **Fast mode and auto-reconnect**: reads up to 4 values per request (switches itself off if the adapter cannot), and reconnects up to 4 times if the link drops.
- **Engine self-tests (Mode 06)**: misfire counters per cylinder, catalyst, oxygen sensor and EVAP results the car measures itself.
- **Inspection check**: warning light, codes and readiness monitors, with what to drive to finish missing monitors.
- **Health check and trends**: 25 seconds at idle judged by simple rules; averages are kept and graphed, with drift warnings (for example falling charging voltage).
- **Repairs and costs**: log with category, cost, mileage, workshop and a receipt photo; totals by category; included in the PDF and given to the AI.
- **Service reminders with last-done dates**: enter when each job was last done (date and mileage), press Done after a service (it is added to the repair log), add your own items, set many items to one date at once. Next due date and mileage are counted from the last-done record, with a forecast of the date the km limit will arrive based on your usual driving.
- **Car guide (from your owner's manual)**: engine and VIN check, fluids, fuel and key numbers, part numbers (filters, plugs, wipers), the manual's service schedule (normal and severe), every warning light and dashboard message explained, and how-tos (oil check and oil-life reset, coolant, overheating, check engine light, after a battery change, transmission fluid, air filter, tyres). The service reminders can be filled with the manual's intervals in one tap (hot-weather severe use: transmission fluid every 72,000 km, cabin filter 36,000, air filter 72,000, plugs 156,000, coolant 240,000 km / 5 years). Fault cards add what the manual says about that code.
- **Flight recorder**: while connected, keeps the last minute of live data and saves it by itself when a code appears, the engine overheats, the voltage drops or the idle turns rough. **Mark symptom now** saves the last minute on demand (Live, dashboard). **Cold-start recording** captures the first 3 minutes of a cold engine.
- **Quote checker**: photograph a workshop quote; the AI lists each line and judges it against your service schedule and repair history, with questions to ask.
- **Fuel log**: fill-ups, real L/100 km and cost per km, monthly cost chart, yearly fuel plus repair totals, and calibration of the trip fuel estimate against the pump.
- **Parts register**: parts you fitted with date, mileage and warranty (reminder 30 days before it ends).
- **Battery health score** (0 to 100) from your tests, the trend and the battery's age, with a replace-by estimate.
- **Where I parked** (optional): saves the phone's last known location when the adapter disconnects; open it in Maps.
- **Checklists**: before a trip, and a summer check.
- **Driving score** per trip (hard acceleration, hard braking, high revs, idling) with a tip.
- **Home-screen widget** (next service, registration and insurance, parking) and **launcher shortcuts** (long-press the icon: Live data, Service list, Fuse boxes, Ask the AI).
- **Automatic backup** weekly to a folder you choose (phone storage or Google Drive).
- **Bigger offline fault code list** (about 550 standard codes).
- **Fuse boxes** (Tools): both fuse boxes of the car redrawn from the manual's diagrams (engine bay and dash, every fuse and relay with its number and use). Search by what is not working (horn, wipers, radio, 12 V socket, OBD port...) or browse the diagram, zoom, and tap a slot: the fuse pulses on the diagram with its number, type and how to open the box. Fault cards list the fuses and relays worth checking for that code, AI answers name fuses exactly and show tappable links, and the Connect screen links to the OBD port fuse (14) if the adapter has no power. Also: fuse colour chart, safety tips, how to check a fuse, relay test tips, and "I checked it / replaced it" logging into the history. (The manual lists no amp ratings: read them on the fuse and the box lid.)
- **History and links**: one timeline of services, repairs, things you log (refuelled, car wash, battery off, warning light, noise) and faults that appear or go away between scans. Obdy looks for links (for example an oil change followed by a cam timing code, a refuel followed by an EVAP code, a battery change followed by network codes) and for repeating problems or repairs that did not fix a code. The AI can read the whole timeline too.
- **Registration and insurance dates** with reminders.
- **Backup and restore** (one file, AI keys optional).
- **Ask about your car (chat)**: the AI sees your data; attach a photo or ask by voice.
- **Dashboard mode**: big landscape gauges.
- **Trip routes**: optional GPS track saved as GPX per trip.
- **Updates from GitHub**: automatic check and background download (Wi-Fi only by default), kept on the phone until you press Install; a red dot on the gear and a banner on Home show when one is ready. Settings (gear, top-right of every main screen) now holds all settings in one place.

## 1.14.6

- The deep scan no longer reports every supported code as a fault (a healthy car showed 257). GM modules list all codes they support; status 01 means supported and fine. Only entries with other status bits (for example 25 = P0128) are shown, with the status explained in words.
- New background keep-alive: while connected, Obdy runs as a foreground service with a quiet notification and a wake lock, so recording, trips and logging continue with the screen off or another app in front. Earlier a locked phone froze the app for up to 15 minutes. Nothing is written outside the app's own storage.

## Pro tools (version 1.14, Tools > Adapter and advanced)
Built for adapters with the STN command set (vLinker MC+, OBDLink). All of them pause the live data while they work and put the adapter back on the fast bus.
- **Fault state filter** (Faults): every code shows **Active now / Pending / History** (from the module's own status byte); filter chips on top.
- **Second bus (pin 1)**: switches the adapter to GM single-wire CAN (`STP 63` = ISO 15765, 11-bit, 33.3 kbit/s; the wake-up uses `STCSWM 2`, then back to
  normal mode 3), listens, looks for modules at 241-25F and reads their codes. If your car has nothing on pin 1 it tells you so (the fast-bus Deep scan
  already reaches the ABS, steering, body and other modules on this car).
- **Module inventory**: part number, software and calibration numbers and the **VIN stored inside every module** (GM `1A` items and UDS `22 F1xx`), with a red
  "VIN differs" flag for a module that probably came from another car. Share as text.
- **Bus monitor**: raw CAN frames per id with rate, filters (`STFAP`, up to 4 ids) so the Bluetooth link keeps up, CSV log. **Signal finder**: record quiet,
  record while you do one thing (door, brake, wheel), and the bytes that changed are listed; save one under a name.
- **Live signals**: your named signals as big values with **alerts** (for example "Door open", only while driving). Name a signal "Speed" to use it for the moving check.
- **Data sweep** (read only): asks a module for service-22 identifiers in ranges and lists the ones that answer (raw bytes plus a guess). A safety check allows only
  read services. **Watch list**: pick a found value, say which bytes and how to scale it, and read it again and again.
- **Active tests**: the safe wrapper for service 2F / AE / 31 commands. **No commands are built in** (GM does not publish them and none were verified): you enter the
  ones you trust. The car must stand still, battery above 11.8 V, 30 seconds at most, Stop button, and the "give control back" command is always sent at the end.
  Writes, ECU reset and security access can never be sent.
- **Performance**: 0-60 and 0-100 km/h, 100-0 km/h with braking distance, timed from the car's CAN speed with the crossing interpolated; best times and history; CSV.
  Driving versus idling hours and fuel are counted automatically.
- Oil life reset steps added to **Relearn guides**.
Not included, on purpose: Mode 22 misfire/charging identifiers (the numbers for this engine are not published; use Data sweep + Watch list to find them) and
Firebase cloud logging (share the CSV files, or use the backup folder).

## Look and feel
Simple Home (status card plus six icon tiles), an icon grid for Tools, a custom icon set drawn in code (no icon library), animated tab and screen
transitions, press feedback on buttons and tiles, animated gauges and progress bars, expanding fault cards and a pulsing status dot while connecting.
Five colour themes in Settings.

## Get the adapter
| Adapter | How it connects | Notes |
|---|---|---|
| **OBDLink MX+** | Bluetooth (classic + LE) | Most capable and fast; also reads GM single-wire CAN. Best if you want the deepest module access. |
| **vLinker MC+** | Bluetooth classic + LE | Best value; compact; STN-class command set. |
| **vLinker FS / MS** | Bluetooth | Fast, good build quality. |
| **Veepeak OBDCheck BLE** | Bluetooth LE | Cheap and reliable for engine codes and live data. Find it with "Search for adapters" in Obdy. |
| **Veepeak Mini / BAFX** | Bluetooth classic (Android) | Budget; fine for engine codes. Chassis modules (ABS, airbag) may not work. |
| **OBDLink EX, vLinker FS USB** | USB cable (OTG) | Wired: no pairing, no dropouts. |
| **Generic ELM327 USB cable** | USB cable (OTG) | Cheap; quality depends on the chip. Obdy finds the speed by itself. |
| OBDLink CX | Bluetooth LE | Good quality but tuned for BMW; no GM single-wire CAN. |
| BlueDriver | Bluetooth | Works only with its own app: not usable with Obdy. |

Avoid unbranded "ELM327 v2.1" clones: most are fake and fail on GM modules. Bluetooth classic adapters must be paired once in Android's Bluetooth settings
(PIN 1234 or 0000). LE adapters are found inside Obdy. USB: plug the cable (with a USB-OTG adapter if needed), Connect > USB cable, tap OK on Android's permission question.

## Build and publish (same as Remoty)
1. Unzip so that `settings.gradle.kts` and `app\` sit next to `Rebuild-APK.bat`.
2. Double-click **`Rebuild-APK.bat`**. First time: it installs Git and the GitHub helper, you sign in to GitHub once, and it creates the
   **public** repository `Obdy`. It uploads, builds on GitHub (first build about 8-12 minutes), runs the unit tests, and puts `Obdy-buildN.apk` in `APK\`.
   If it fails, `build-error.txt` opens: send it to Claude.
3. Install that APK on the phone once.
4. **Updates**: every `Rebuild-APK.bat` build is also published as a GitHub Release, so the app updates itself: it finds the new build when it opens, downloads it once into private storage, and **Settings > Updates > Install now** installs it at any time, even offline. (`Publish-Update.bat` still exists for a release-optimised build.) Older text: double-click **`Publish-Update.bat`** (optional: `Publish-Update.bat what is new`). It builds the release APK and publishes it as a
   GitHub Release. On the phone: Obdy > Settings > Updates > Check for updates > Download and install.
   Remoty works too: share the new zip to Remoty, it runs `Rebuild-APK.bat` on the laptop.

All builds use the same signing key (`signing/obdy-debug.keystore`), so every new APK installs over the old one. The repository is public, so that key
is public too: fine for a personal app, but if you want a secret key, add the 4 secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`) to the repository and the **Release** workflow will use them (then uninstall once, because the key changes).

## Be aware
- Engine and transmission codes use the standard OBD-II services and are solid.
- **ABS, airbag, body modules** (deep scan) use GM diagnostic services that differ by module. The scan lists exactly which addresses answered and shows the raw answers,
  but module names are a first guess and some modules may refuse to talk. After your first deep scan, send me the Console log and I will map them properly.
- **Clearing codes** in non-engine modules works only on modules that allow it without a special session. Real adaptation resets (for example transmission adaptive
  pressure) need a GM factory scanner; Obdy gives the manual relearn procedures instead.
- AI answers are a guide, not a verdict. Airbag (B-codes) and brake (C-codes) faults: have a workshop check them.
- Fuel numbers are estimates (air flow based). Calibrate once against the pump.
- AI model names change often: if the Test says the model is unknown, type a current name from the service's website (Settings > AI services > Edit).
- Part locations are general for this kind of car and the AI's are estimates: confirm with the workshop manual before removing anything.

## Files
`app/src/main/java/com/example/obdy`: `Obd.kt` (reply parser), `Bus.kt` + `ProTools.kt` (pro tools: bus switching, monitor, sweep, safety, timers), `Pids.kt`, `Dtc.kt` (code decoding + offline database), `Elm.kt` (adapter client), `Transport.kt`
(Bluetooth/LE/Wi-Fi), `Gm.kt` (modules and extras), `Calc.kt` (fuel, battery, service, recordings), `ObdyVm.kt` (state), `Gemini.kt`, `Report.kt` (PDF), `Updater.kt`, `ui/`.
Unit tests: `app/src/test` (parsing, code decoding, formulas, battery, service intervals, pro tools with a pretend STN adapter).

### 1.14.1 (from your first real recordings)
- Bus monitor now runs as one continuous recording (before, it stopped and restarted every 1.5 s, lost the adapter's backlog and cut frames in half). A frame cut at the end of a read is finished by the next read.
- After a recording the app tells you frames per second and warns when the adapter could not pass every frame to the phone. On the busy fast bus (pins 6+14) filter on one to four ids for a complete recording.
- Signal finder ignores ids with fewer than 6 frames in either recording.
- Data sweep is several times faster (each request now says "one answer expected", so the adapter no longer waits out its 200 ms timeout).

### 1.14.2 (steering code not shown, network drop-outs)
- Fault lists of the brake, steering, body and other modules are now asked with automatic formatting OFF. The module's answer is a list of raw frames without a length byte, which the adapter's automatic formatting threw away (log: "no answer" from every module, even though the BCM answered 7F A9 22 to the third try). An empty list is now shown as "0 codes" instead of "no fault list".
- The "ask everyone" question lets only 5xx frames through, so the answers are no longer drowned by normal bus traffic.
- Network watch (Tools > Drives and records): asks every module you found whether it is still there about every two seconds, reads the battery voltage, and writes down when a module drops out or the voltage falls. Share the report with your mechanic.
- Faults screen: "What GM service information says" for C047A, C0710, C0544/U0415, C056D/C056E/C0569 and the U0074/U0077 network codes.


### 1.14.3
- Gearbox screen: live "Lever position" from the gearbox computer (value 194F, found by your own lever test: P 01, R 02, N 04, D 06, M 08). Move the lever slowly and see whether the computer sees Drive when the dash skips it.

### 1.14.4
- Tools page rebuilt: four quick tiles (battery, health check, power steering, find the cause) and five collapsible groups (Diagnose, Ask and check, Fix and find, Drive and costs, Expert). Entries that duplicated other screens (Car guide, History, Recordings, Repairs and costs, Module data, Checklists) were removed from this page.

### 1.14.5 (the log records everything)
- Every command to the adapter is written to the log BEFORE it is sent, then the answer with its time in ms; failures are logged with "!!". The bus monitor now logs its start, stop and every chunk of frames too.
- Every tap (with the screen it landed on), swipe, named button, tile, chip and typed field (keys and passwords are hidden), app foreground/background/close, connect and disconnect are logged. Log limit raised to 300 MB; your own actions are flushed to disk at once.
