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
- **Updates from GitHub**: Settings > Updates.

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
4. **Updates**: double-click **`Publish-Update.bat`** (optional: `Publish-Update.bat what is new`). It builds the release APK and publishes it as a
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
