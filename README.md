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
| Flare engine | SOS records, receipts, signal readings, event log, session ID | Next |
| Advertiser and scanner | Bluetooth LE broadcast and scan, foreground service | Planned |
| Handoff rules | Standby, planned handoff, takeover, cancel | Planned |
| Screens | Role picker, SOS screen, rescuer dashboard, Find mode | Planned |
| Signs of life | On-device sound detection with YAMNet | Planned |

The app does not do anything on a phone yet: it still opens the starter screen.

## How it works

Every message is exactly 24 bytes, sent as manufacturer data under company ID `0xFFFF` (reserved for testing) in a non-connectable Bluetooth LE advert. There are four packet types:

| Type | Sent by | Carries |
|---|---|---|
| SOS | The phone carrying the SOS | Message ID, origin and carrier phone IDs, position, accuracy, fix age, people count, injured / trapped / water / medical / fire flags, battery, last sound heard |
| RECEIPT | A rescuer phone | The message ID it heard |
| STANDBY | A nearby phone backing up an SOS | The SOS IDs and its own battery |
| HANDOFF | The carrier | The phone taking over, or 0 to cancel |

A missing GPS fix is sent as the smallest 32-bit number, never as 0,0. Packets with the wrong length, version, type or an impossible position are dropped.

## Code layout

```
app/src/main/java/com/flarehop/
├── MainActivity.kt
├── flare/                 radio and handoff code (Project lead)
│   ├── FlareConstants.kt  packet values, timings and thresholds
│   ├── SosRecord.kt       SosRecord, SosDetails, Fix, SoundType
│   ├── FlareEngine.kt     the engine interface the screens use
│   └── PacketCodec.kt     24-byte packet encode and decode
├── ui/                    screens (App lead)
│   └── theme/
└── sense/                 sound, location, battery, priority score (AI lead), not created yet
app/src/test/java/com/flarehop/
└── flare/
    └── PacketCodecTest.kt
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

## Running tests

Run all unit tests from the project folder:
```
./gradlew testDebugUnitTest
```
They run on your computer, with no phone needed. The HTML report is written to `app/build/reports/tests/testDebugUnitTest/index.html`.

In Android Studio you can also right-click a test file, such as `PacketCodecTest`, and choose **Run**.

| Test file | Checks |
|---|---|
| `PacketCodecTest` | Round trips for all four packet types, exact byte layout, no-fix value, caps at 254, unknown values as 255, and the drop rules |

To build the app:
```
./gradlew assembleDebug
```

## License

Released under the MIT License. See [LICENSE](LICENSE).
