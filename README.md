# FlareHop

FlareHop is an Android app that relays SOS messages from phone to phone over Bluetooth Low Energy, with no cellular or Wi-Fi connection. Each phone that hears an SOS re-broadcasts it, so the message can hop through a crowd until it reaches someone who can help.

> Hackathon project. Feature code is written during the event; this repo starts as an empty skeleton.

## Team and ownership

Each person works only inside their own package. The only shared pieces are `SosRecord`, the engine interface and the constants in `flare/`. These are written first so all three people can work in parallel from hour 1.

| Package | Owner | Contents |
|---|---|---|
| `com.flarehop.flare` | Mesh lead | Packet codec, engine and handoff rules, BLE advertiser, BLE scanner, foreground service, constants |
| `com.flarehop.ui` | App lead | Role picker, SOS screen, dashboard, Find mode, event log, demo settings, `FakeEngine` |
| `com.flarehop.sense` | AI lead | Sound listener (YAMNet), location, battery watcher, priority score |

## Project layout

```
FlareHop/
├── app/src/main/java/com/flarehop/
│   ├── flare/   ← packet codec, engine + handoff rules, advertiser, scanner, foreground service, constants
│   ├── ui/      ← role picker, SOS screen, dashboard, Find mode, event log, demo settings, FakeEngine
│   └── sense/   ← sound listener (YAMNet), location, battery watcher, priority score
├── app/src/main/assets/yamnet.tflite
├── app/src/test/  ← fake radio, handoff tests, score tests
├── CONTRACTS.md   ← copy of the plan's Contracts tab
├── LICENSE
└── README.md
```

## Requirements

- Android Studio (latest stable)
- JDK 11 or newer (bundled with Android Studio)
- Android SDK 37 (compile and target), minimum SDK 24
- At least two physical Android phones with Bluetooth LE for the relay demo. Emulators can't do BLE advertising or scanning, so use `FakeEngine` when working on the UI.

## Setup

1. Clone the repo:
   ```
   git clone https://github.com/<owner>/flarehop.git
   ```
2. Open the folder in Android Studio and let Gradle sync.
3. Download `yamnet.tflite` and put it in `app/src/main/assets/`.
4. Create your own branch before you change anything:
   ```
   git checkout -b app-lead/<feature>   # or ai-lead/<feature>
   ```
5. Run the `app` configuration on a device.

## Workflow

- `main` is protected. All changes come in through pull requests.
- Only the mesh lead merges, at the hour 5, 9 and 13 gates.
- Keep changes inside your own package. If you need a change to a shared contract (`SosRecord`, the engine interface or the constants), raise it with the team first and update `CONTRACTS.md`.

## Running tests

```
./gradlew test
```

The unit tests use a fake radio, so they run on the JVM without a device.

## Demo

1. Install the app on three or more phones and turn on Bluetooth and location on each.
2. On each phone, pick a role in the role picker (for example sender, relay and rescuer).
3. Place the phones so the sender and rescuer are out of direct range of each other, with the relay in between.
4. On the sender, start an SOS.
5. Watch the event log on the relay as it picks up and re-broadcasts the message.
6. On the rescuer, open the dashboard to see the SOS and its priority score, then use Find mode to locate the sender.

## License

Released under the MIT License. See [LICENSE](LICENSE).
