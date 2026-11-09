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
- **Fuse boxes** (Tools): both fuse boxes of the car redrawn from the manual's diagrams (engine bay and dash, every fuse and relay with its number and use). Search by what is not working (horn, wipers, radio, 12 V socket, OBD port...) or browse the diagram, zoom, and tap a slot: the fuse pulses on the diagram with its number, type and how to open the box. Fault cards list the fuses and relays worth checking for that code, AI answers name fuses exactly and show tappable links, and the Connect screen links to the OBD port fuse (14) if the adapter has no power. Also: fuse colour chart, safety tips, how to check a fuse, relay test tips, and "I checked it / replaced it" logging into the history. (The manual lists no amp ratings: read them on the fuse and the box lid.)
- **History and links**: one timeline of services, repairs, things you log (refuelled, car wash, battery off, warning light, noise) and faults that appear or go away between scans. Obdy looks for links (for example an oil change followed by a cam timing code, a refuel followed by an EVAP code, a battery change followed by network codes) and for repeating problems or repairs that did not fix a code. The AI can read the whole timeline too.
- **Registration and insurance dates** with reminders.
- **Backup and restore** (one file, AI keys optional).
- **Ask about your car (chat)**: the AI sees your data; attach a photo or ask by voice.
- **Dashboard mode**: big landscape gauges.
- **Trip routes**: optional GPS track saved as GPX per trip.
- **Updates from GitHub**: automatic check and background download (Wi-Fi only by default), kept on the phone until you press Install; a red dot on the gear and a banner on Home show when one is ready. Settings (gear, top-right of every main screen) now holds all settings in one place.

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
`app/src/main/java/com/example/obdy`: `Obd.kt` (reply parser), `Pids.kt`, `Dtc.kt` (code decoding + offline database), `Elm.kt` (adapter client), `Transport.kt`
(Bluetooth/LE/Wi-Fi), `Gm.kt` (modules and extras), `Calc.kt` (fuel, battery, service, recordings), `ObdyVm.kt` (state), `Gemini.kt`, `Report.kt` (PDF), `Updater.kt`, `ui/`.
Unit tests: `app/src/test` (parsing, code decoding, formulas, battery, service intervals).
