# FlareHop

FlareHop turns a trapped person's phone into a rescue beacon that keeps calling after its battery dies. It needs no cell towers, no internet and no pairing.

- The victim taps SOS once. Their phone broadcasts a small Bluetooth LE packet with the SOS details and GPS position, and listens for signs of life such as knocking or voices.
- A nearby phone stands by and takes the SOS over before the victim's battery dies, or after 15 seconds of silence. The SOS keeps its ID and the victim's position.
- A rescuer's phone lists every SOS it hears, ranked by urgency, sends a receipt back to the victim, and leads the rescuer to the phone with a signal-strength meter (Find mode).

Phones never connect to each other. Every phone only broadcasts and listens, the way avalanche beacons work.

> Hackathon project, built in a 24-hour event by a team of three.

## Status

| Step | What it adds | State |
|---|---|---|
| Dependencies | Coroutines, ViewModel, MediaPipe audio, coroutines-test | Done |
| Skeleton | `SosRecord`, the `FlareEngine` interface, shared constants | Done |
| Packet codec | Encode and decode the four 24-byte packet types | Done |
| Flare engine | SOS records, receipts, signal readings, event log, session ID, cancel | Done |
| Advertiser | Bluetooth LE broadcast, foreground service, radio test screen | Done |
| Scanner | Hear other phones' packets and pass them to the engine | Next |
| Handoff rules | Standby, planned handoff, takeover, cancel | Planned |
| Screens | Role picker, SOS screen, rescuer dashboard, Find mode | Planned |
| Signs of life | On-device sound detection with YAMNet | Planned |

On a phone the app opens a temporary radio test screen: pick a role, send a test SOS, cancel it, and stop the beacon. The real screens replace it later. Phones can broadcast but not yet hear each other.

## How it works

Every message is exactly 24 bytes, sent as manufacturer data under company ID `0xFFFF` (reserved for testing) in a non-connectable Bluetooth LE advert. There are four packet types:

| Type | Sent by | Carries |
|---|---|---|
| SOS | The phone carrying the SOS | Message ID, origin and carrier phone IDs, position, accuracy, fix age, people count, injured / trapped / water / medical / fire flags, battery, last sound heard |
| RECEIPT | A rescuer phone | The message ID it heard |
| STANDBY | A nearby phone backing up an SOS | The SOS IDs and its own battery |
| HANDOFF | The carrier | The phone taking over, or 0 to cancel |

A missing GPS fix is sent as the smallest 32-bit number, never as 0,0. Packets with the wrong length, version, type or an impossible position are dropped.

The flare engine is the only part of the app that talks to the radio. Screens call its actions (`sendSos`, `cancelSos`, `setRole`, `setBattery`, `setSound`, `reset`) and watch its streams (`mySos`, `knownSos`, `group`, `directSignal`, `log`). Each phone picks a new random ID every session, and a rescuer phone sends one receipt for every SOS it hears.

## Code layout

```
app/src/main/java/com/flarehop/
├── MainActivity.kt       temporary radio test screen
├── flare/                 radio and handoff code (Project lead)
│   ├── FlareConstants.kt  packet values, timings and thresholds
│   ├── SosRecord.kt       SosRecord, SosDetails, Fix, SoundType
│   ├── FlareEngine.kt     the engine interface the screens use
│   ├── PacketCodec.kt     24-byte packet encode and decode
│   ├── RealFlareEngine.kt the engine: records, receipts, signals, log
│   ├── Flare.kt           the one engine instance the app shares
│   ├── AdvertPlanner.kt   decides when to start, update, pause or resume the advert
│   ├── BleAdvertiser.kt   broadcasts the engine's packets every 300 ms
│   └── FlareService.kt    foreground service that keeps the beacon on
├── ui/                    screens (App lead)
│   └── theme/
└── sense/                 sound, location, battery, priority score (AI lead), not created yet
app/src/test/java/com/flarehop/
└── flare/
    ├── AdvertPlannerTest.kt
    ├── PacketCodecTest.kt
    └── RealFlareEngineTest.kt
```

Each person works only in their own package. `SosRecord`, `FlareEngine` and `FlareConstants` are shared contracts: only the Project lead changes them, after telling the team.

## Requirements

- Android Studio, latest stable
- Android SDK 37 (compile and target), minimum SDK 31 (Android 12)
- Gradle 9.6.0 through the wrapper. Gradle downloads the JDK 25 it runs on by itself; the app compiles to Java 11.
- Two or more physical Android phones with Bluetooth LE for the radio. Emulators cannot advertise or scan, so screens are built against a fake engine first.

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
| JUnit | 4.13.2 |

All versions live in `gradle/libs.versions.toml`.

## Setup

1. Clone the repo:
   ```
   git clone https://github.com/Aayushkumar-gupta/FlareHop.git
   ```
2. Open the folder in Android Studio and let Gradle sync.
3. Create a branch for your work before changing anything:
   ```
   git checkout -b <your-role>/<feature>
   ```

## Workflow

- Every change goes through a branch and a pull request into `main`.
- Only the Project lead merges, at the hour 5, 9 and 13 checkpoints and for urgent fixes in between.
- After the hour 13 feature freeze, only bug fixes are merged.

## Permissions

The app asks for **Nearby devices** (Bluetooth advertise) and, on Android 13 and newer, **Notifications** when you pick a role. Without Nearby devices the beacon cannot start. The beacon runs as a foreground service with a "Flarehop is on" notification, so it keeps broadcasting with the screen off. Location, scanning and microphone permissions are added with the features that need them.

## Trying it on a phone

1. Install it on a physical phone: `./gradlew installDebug` with the phone connected over USB, or **Run** in Android Studio.
2. Turn Bluetooth on, open Flarehop, tap **VICTIM** and allow the permissions.
3. Tap **Send test SOS**.
4. On a second phone, open nRF Connect, start a scan and find the device with manufacturer data `0xFFFF`. Its data starts with `21`: version 2, type SOS.
5. Tap **Cancel SOS** on the first phone. The data starts with `24` (HANDOFF with successor 0) for 10 seconds, then the advert stops.

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
| `PacketCodecTest` | Round trips for all four packet types, exact byte layout, no-fix value, caps at 254, unknown values as 255, and the drop rules |
| `RealFlareEngineTest` | Sending an SOS, records for heard SOS, signal readings, dropped packets, receipts and the 15-second receipt window, acknowledgements, cancel, packet rotation and reset. Uses a fake clock, so no waiting |

To build the app:
```
./gradlew assembleDebug
```

## License

Released under the MIT License. See [LICENSE](LICENSE).
