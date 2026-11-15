# FlareHop

FlareHop is a rescue app for Android that works with no cell towers, no internet and no pairing. A trapped person taps SOS once, and their phone keeps calling for help over Bluetooth, even after its battery dies.

- **The person who needs help** taps SOS. Their phone broadcasts a small Bluetooth LE packet with their GPS position, how many people are with them, whether anyone is injured or trapped, their battery level, and the last sign of life the phone heard (speech, shouting, knocking or crying).
- **Every Flarehop phone nearby** joins by itself, keeps a copy of every SOS it hears and reports its own battery. When a phone is about to die, the phone with the most charge takes over broadcasting its SOS, so the call for help outlives the phone that sent it.
- **A rescuer** opens the same app, sees every SOS in range ranked by urgency, sends a receipt back ("A rescuer received your SOS"), and follows the signal to the phone with Find mode.

Phones never connect to each other. Every phone only broadcasts and listens, the way avalanche beacons work.

> Hackathon project, built in a 24-hour event by a team of three.

## Status

| Part | What it does | State |
|---|---|---|
| Packet codec | Encodes and decodes the four 24-byte packet types and drops bad ones | Done |
| Flare engine | SOS records, receipts, signal readings, event log, cancel | Done |
| Radio | Bluetooth LE broadcast and scan in a foreground service | Done |
| Battery watcher | Reports the phone's battery to the engine every minute | Done |
| Phones find each other | Phones near an SOS join by themselves and report their battery; every phone lists the phones in range | Done |
| Handoff | Below 10% battery, hand every SOS a phone broadcasts to the phone in range with the most battery | Done |
| Takeover and conflicts | Take over an SOS after 15 seconds of silence; two phones broadcasting the same SOS | Next |
| Screens | Survivor, Rescuer, Find mode, Log, Demo settings | Done |
| Priority score | Ranks every SOS by urgency | Done |
| Signs of life | On-device sound detection with YAMNet while this phone sends its own SOS, also with the screen off | Done |
| Auto-join with the app closed | The app wakes when it hears an SOS | Planned |

## Using the app

The app has four tabs:

| Tab | What it shows |
|---|---|
| **Survivor** | People count and conditions (injured, trapped, medical, water rising, fire), **Send SOS** and **Cancel SOS**, a radar of the phones around you, and "A rescuer received your SOS" once a rescuer answers |
| **Rescuer** | **Set yourself as rescuer**, then every SOS this phone hears, ranked by urgency, with distance, battery, signal and last sound. **Find** opens Find mode for one SOS. **Stop being a rescuer** switches back. |
| **Log** | Every radio event on this phone: SOS heard, phone joined, receipt sent, cancelled |
| **Demo** | Override battery, Fake a knock, Listen continuously, Keep screen on, Reset this phone |

Find mode follows the selected SOS's Bluetooth signal: a meter, a warmer or colder arrow, and beeps that speed up as you get closer. It averages the last 10 readings and shows "Signal lost" after 5 seconds without one.

A red banner on the Survivor and Rescuer tabs explains anything that stops the radio: Nearby devices not allowed, Location set to Approximate, Bluetooth off or Location off, each with a button to the right Settings page.

## How it works

Every message is exactly 24 bytes, sent as manufacturer data under company ID `0xFFFF` (reserved for testing) in a non-connectable Bluetooth LE advert. There are four packet types:

| Type | Sent by | Carries |
|---|---|---|
| SOS | The phone broadcasting that SOS | Message ID, origin and carrier phone IDs, position, accuracy, fix age, people count, injured / trapped / water / medical / fire flags, the broadcasting phone's battery, the victim's last sound |
| RECEIPT | A rescuer phone | The message ID it heard |
| STANDBY | Every phone except a rescuer, every 5 seconds, once there is an SOS around | Its phone ID and battery, and its own SOS's IDs if it has one |
| HANDOFF | A phone handing an SOS on | The phone taking over, or 0 to cancel |

A missing GPS fix is sent as the smallest 32-bit number, never as 0,0. Packets with the wrong length, version or type, an impossible position, or a zero ID where one is required are dropped.

A phone counts another phone as in range when it heard it at −70 dBm or stronger in the last 15 seconds. The engine lists those phones with their battery and how many SOS each is broadcasting, which the handoff uses to pick the phone with the most charge.

The flare engine is the only part of the app that talks to the radio. Screens call its actions (`sendSos`, `cancelSos`, `setRole`, `setBattery`, `setSound`, `reset`) and watch its streams (`mySos`, `knownSos`, `carried`, `group`, `directSignal`, `log`).

## Code layout

```
scripts/                       install-all.sh, mirror.sh, record-backup.sh
app/src/main/
├── assets/yamnet.tflite       sound model
└── java/com/flarehop/
    ├── MainActivity.kt         asks for permissions and starts the beacon
    ├── flare/                  radio and mesh (Project lead)
    │   ├── FlareConstants.kt   packet values, timings and thresholds
    │   ├── SosRecord.kt        SosRecord, SosDetails, Fix, SoundType
    │   ├── FlareEngine.kt      the engine interface the screens use
    │   ├── PacketCodec.kt      24-byte packet encode and decode
    │   ├── RealFlareEngine.kt  the engine: records, receipts, phones in range, handoff, log
    │   ├── Flare.kt            the engine, battery watcher and signs of life the app shares
    │   ├── AdvertPlanner.kt    decides when to start, update, pause or resume the advert
    │   ├── BleAdvertiser.kt    broadcasts the engine's packets every 300 ms
    │   ├── BleScanner.kt       hears Flarehop packets and hands them to the engine
    │   └── FlareService.kt     foreground service: radio, battery watcher and sound listener
    ├── ui/                     screens (App lead)
    │   ├── MainScreen.kt       the four tabs
    │   ├── VictimScreen.kt     Survivor tab
    │   ├── SosListScreen.kt    Rescuer tab
    │   ├── FindScreen.kt       Find mode
    │   ├── EventLogScreen.kt   Log tab
    │   ├── DemoSettingsScreen.kt
    │   ├── RadioCheck.kt       permission, Bluetooth and Location banner
    │   ├── LocationReader.kt   GPS or network location
    │   ├── FlareViewModel.kt, RoleStore.kt, RadarView.kt, GeoDistance.kt
    │   └── theme/
    └── sense/                  sound, battery, priority score (AI lead)
        ├── PriorityScore.kt    urgency score, bands and ranking
        ├── BatteryWatcher.kt   battery level every minute, with a demo override
        ├── YamnetListener.kt   microphone to the YAMNet model
        ├── SoundGate.kt        which sounds count: class, confidence and loudness
        └── SignsOfLife.kt      when to listen, and the fake knock
app/src/test/java/com/flarehop/
├── flare/      AdvertPlannerTest, MeshGroupTest, MeshHandoffTest, PacketCodecTest, RealFlareEngineTest
├── handoff/    FakeRadio (several engines on an in-memory radio), RescueLoopTest, HandoffRulesTest
└── sense/      BatteryWatcherTest, PriorityScoreTest, SignsOfLifeTest, SoundGateTest
```

Each person works only in their own package. `SosRecord`, `FlareEngine` and `FlareConstants` are shared contracts: only the Project lead changes them, after telling the team.

## Requirements

- Android Studio, latest stable
- Android SDK 37 (compile and target), minimum SDK 31 (Android 12)
- Gradle 9.6.0 through the wrapper. Gradle downloads the JDK 25 it runs on by itself; the app compiles to Java 11.
- Physical Android phones with 64-bit ARM processors (every recent phone). The app ships only the `arm64-v8a` build, which keeps it at about 66 MB. Emulators cannot advertise or scan over Bluetooth.

## Pinned versions

| Library | Version |
|---|---|
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| AndroidX core-ktx | 1.19.1 |
| AndroidX activity-compose | 1.13.0 |
| AndroidX lifecycle (runtime, viewmodel-compose) | 2.11.0 |
| kotlinx-coroutines (android, test) | 1.11.0 |
| MediaPipe tasks-audio | 1.2.2 |
| ktlint | 1.8.0 (Gradle plugin 14.2.0) |
| JUnit | 4.13.2 |

All versions live in `gradle/libs.versions.toml`.

## Setup

1. Clone the repo:
   ```
   git clone https://github.com/Aayushkumar-gupta/FlareHop.git
   ```
2. Open the folder in Android Studio and let Gradle sync.
3. Before every change, update `main` and branch from it:
   ```
   git checkout main
   git pull origin main
   git checkout -b <your-role>/<feature>
   ```

## Workflow

- Every change goes through a branch and a pull request into `main`.
- Only the Project lead merges, at the hour 5, 9 and 13 checkpoints and for urgent fixes in between.
- After the hour 13 feature freeze, only bug fixes are merged.

## Permissions

The app asks for these when it opens:

| Permission | Why |
|---|---|
| Nearby devices (Bluetooth advertise and scan) | To broadcast the SOS and hear other phones. Without it the beacon cannot start. |
| Location, **precise** | Android only delivers Bluetooth scan results to apps with precise location, because Bluetooth beacons can reveal where you are. With Approximate or none, the phone broadcasts but hears nothing. |
| Microphone | To listen for signs of life while this phone sends its own SOS, also with the screen off. Without it the SOS still goes out, without sounds. |
| Notifications (Android 13 and newer) | To show the "Flarehop is on" notification of the foreground service. |

The phone's **Location setting must be on** as well: with it off, Android delivers no Bluetooth scan results, and the app shows a banner saying so.

## Trying it on phones

You need two phones with Android 12 or newer, Bluetooth on and Location on.

1. Install it on each phone: `./gradlew installDebug` with the phones connected over USB, or **Run** in Android Studio.
2. Phone A: open Flarehop, allow every permission (Location: **Precise**), open **Survivor**, choose the people and conditions and tap **Send SOS**.
3. Phone C: open Flarehop, allow every permission, open **Rescuer** and tap **Set yourself as rescuer**.
4. Within a few seconds phone C lists the SOS with its urgency, battery and signal, and phone A shows "A rescuer received your SOS".
5. On phone C, tap **Find** on the SOS and walk toward phone A: the beeps speed up.
6. Tap **Cancel SOS** on phone A. Phone C marks the SOS as cancelled.

With a third phone left on **Survivor**, it joins by itself when phone A sends the SOS, and the radars on both survivor phones show each other.

To watch the radio in Logcat: `adb logcat -s FlareAdvertiser FlareScanner`.

To check the raw packets without a second Flarehop phone, scan with nRF Connect and look for manufacturer data `0xFFFF`: an SOS starts with `21`, a receipt with `22`, a STANDBY with `23`.

## Demo scripts

Three scripts in `scripts/` run the phones for testing and the demo. They need `adb` and `scrcpy` on your PATH (macOS):

```
echo 'export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"' >> ~/.zshrc
source ~/.zshrc
brew install scrcpy
```

| Script | What it does |
|---|---|
| `scripts/install-all.sh` | Builds the app once, installs it on every phone plugged in over USB at the same time, and opens it |
| `scripts/mirror.sh [serial]` | Opens a scrcpy window for each phone (or only the one you name), keeping sound on the phone so Find mode's beeps stay audible |
| `scripts/record-backup.sh <serial>` | Records that phone's screen for the backup video and saves it twice, in `~/Movies/flarehop` and on the Desktop |

Find a phone's serial with `adb devices`. The scripts are bash, so on Windows they need Git Bash or WSL.

## Development tools

| Tool | What it does | Command |
|---|---|---|
| ktlint | Formats Kotlin and checks the code style. Settings are in `.editorconfig` (Android Studio style, 100-character lines, trailing commas). | `./gradlew ktlintFormat` fixes, `./gradlew ktlintCheck` checks |
| Android lint | Finds Android bugs, such as Bluetooth calls without a permission check | `./gradlew lintDebug` |
| Unit tests | JUnit tests that run on your computer | `./gradlew testDebugUnitTest` |
| CI | GitHub Actions runs all of the above and builds the APK on every push to `main` and every pull request | `.github/workflows/ci.yml` |

Before you push, run:
```
./gradlew ktlintFormat ktlintCheck testDebugUnitTest lintDebug
```
If CI shows a red cross on GitHub, open the run to see which step failed.

Android Studio picks up `.editorconfig` by itself, so **Code > Reformat Code** gives the same result as `ktlintFormat`.

## Running tests

Run all unit tests from the project folder:
```
./gradlew testDebugUnitTest
```
They run on your computer, with no phone needed. The HTML report is written to `app/build/reports/tests/testDebugUnitTest/index.html`.

In Android Studio you can also right-click a test file, such as `PacketCodecTest`, and choose **Run**.

| Test file | Checks |
|---|---|
| `AdvertPlannerTest` | When the advertiser starts, updates, skips an unchanged packet, pauses and resumes |
| `PacketCodecTest` | Round trips for all four packet types, exact byte layout, no-fix value, caps at 254, unknown values as 255, and the drop rules, including zero IDs |
| `MeshGroupTest` | Joining when an SOS is heard, STANDBY every 5 seconds, never from a rescuer, the −70 dBm range, carried counts, dropping silent phones after 15 seconds, and three phones finding each other on a fake radio |
| `MeshHandoffTest` | The handoff below 10% battery: the phone with the most battery wins, ties go to the lowest ID, unknown battery, weak signal and rescuers are never chosen, a carrier hands on everything it carries, the next phone is named after 30 seconds without an answer, the rescuer follows the new carrier with the victim's details, and cancel releases the carried copy |
| `RealFlareEngineTest` | Sending an SOS, records for heard SOS, signal readings, dropped packets, receipts and the 15-second receipt window, acknowledgements, cancel, packet rotation and reset. Uses a fake clock, so no waiting |
| `RescueLoopTest` | Survivor and rescuer engines on a fake radio: the rescuer hears the SOS with its signal strength, the victim gets the receipt, out-of-range phones hear nothing, cancel reaches the rescuer |
| `HandoffRulesTest` | Contract 3's handoff rules on a fake radio, one test per rule: the strongest phone in range is named, ties, range, rescuers and unknown battery, the next phone after 30 seconds, a carrier hands on everything, the copy keeps the victim's data and sound with the carrier's battery, the SOS reaches phones the victim cannot reach, cancel, and the last phone standing carrying every SOS. Takeover after silence and the two-carrier rule are written and skipped (`@Ignore`) until they are built |
| `PriorityScoreTest` | The four score checks from Contract 5, the 10-minute sound window, and ties going to the SOS heard first |
| `BatteryWatcherTest` | Battery pushed at start and every minute, the demo override, unreadable levels, stop |
| `SignsOfLifeTest` | The listening schedule, stopping below 15% battery, continuous listening, the fake knock |
| `SoundGateTest` | Which YAMNet classes count, the 0.5 confidence floor, and the loudness floor |

To build the app:
```
./gradlew assembleDebug
```

## License

Released under the MIT License. See [LICENSE](LICENSE).
