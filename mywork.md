# Kushal (App lead): my work

Updated 2026-11-14. Branch `kushal`, synced with `main` at a77173b (skeleton and packet codec merged).

I own every screen a person touches: the SOS screen, the rescuer dashboard, Find mode, the Event log and Demo settings, all in the `ui` package. After hour 18 I also own every fix, because Aayush stops coding.

## Rules for my part

- Build against `FakeEngine` until hour 9, so I never wait for the radio.
- Big type, plain words, one action per screen: judges will try it with their own hands.
- The dashboard is the rescuer's whole view: rank by the priority score, and show the carrier, battery and last sound on every card.
- Never touch the `flare` package (Aayush) or `sense` (Aman). No Bluetooth code.
- Start every AI prompt with the Contracts header, the whole Contracts tab, then one Build list row.
- Set my Build list row to In progress when I start it and Done once it's merged.
- Blocked for 20 minutes: post in the team chat, tag the owner, switch to my next task.

## Done

- [x] Repo cloned to `d:\00-hackathon\FlareHop`, and it builds and runs on my phone
- [x] Branch `kushal` created and synced with `main`
- [x] Skeleton from Aayush is in: `SosRecord`, `FlareEngine` interface, `FlareConstants`, `PacketCodec`

## My batches

| Batch | Hours | Do | Needs | Done when | Status |
|---|---|---|---|---|---|
| 0 | Before | Install Android Studio and Compose; sketch the SOS screen, rescuer dashboard and Find mode on paper; pack cables, power bank, extension cord | Nothing | Sketches photographed into the team chat; kit packed | Android Studio done; sketches and kit not done |
| 1 | 0-1 | Kickoff; turn sketches into AI prompts; from minute 45, build `FakeEngine` | Skeleton (Aayush): in | `FakeEngine` runs on my phone by hour 2 | Not started |
| 2 | 1-5 | Permissions and radio checks, role picker, SOS screen | `FakeEngine` | SOS screen shows Sending, Received and Handed off on sample data | Not started |
| 3 | 5-9 | Rescuer dashboard with ranked cards; Event log | Priority score (Aman) | Dashboard sorts the sample SOS records correctly | Not started |
| 4 | 9-13 | Find mode; Demo settings; swap `FakeEngine` for the real engine at hour 9 | Real engine (Aayush) | Hour 13 gate: Find leads to the carrier phone | Not started |
| 5 | 13-18 | Hours 13-16: nap. Hours 16-18: fix Test log bugs; check type size and contrast | Test log (Aman) | Every open app bug has an owner | Not started |
| 6 | 18-20 | Take every fix, including radio fixes Aayush hands over; record the backup video in one clean take with scrcpy, save two copies | A stable build | Video saved twice | Not started |
| 7 | 20-24 | Play the victim in rehearsals and judge drills; stand by for fixes | Everyone | Submitted | Not started |

## My Build list rows

| # | Piece | Scope | Batch | Est. | Status |
|---|---|---|---|---|---|
| 1 | FakeEngine | Implements the engine interface with sample SOS records, a simulated signal and a fake handoff every 2 minutes | 1 | 0.5 h | Written in `ui/FakeEngine.kt`, compiles; not run on a phone or committed yet |
| 2 | Permissions and radio checks | Runtime requests for BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, ACCESS_FINE_LOCATION, RECORD_AUDIO; clear screens for denied permission, Bluetooth off and location off | 2 | 1 h | Not started |
| 3 | Role picker | Bottom nav: Victim (default), Nearby, Rescuer; choice saved in SharedPreferences and restored on next launch (`ui/MainScreen.kt`, `ui/RoleStore.kt`, `ui/NearbyScreen.kt`) | 2 | 0.5 h | Written; not run yet |
| 4 | SOS screen | Big button; people, injured, trapped, water, medical, fire; status Sending, Received or Handed off; "Listening for sounds" | 2 | 2 h | Not started |
| 5 | Bare SOS list (hour 5 gate) | Plain list of `knownSos`, so A's SOS shows on C at hour 5; grows into the dashboard | 2 | 0.3 h | Written in `ui/SosListScreen.kt` and `ui/FlareViewModel.kt`, shown from `MainActivity`; not run yet. Confirm the gate with Aayush |
| 6 | Rescuer dashboard | Every SOS heard, sorted by priority score; each card shows flags, people, fix age, battery, carrier, last sound and receipt | 3 | 3 h | Not started |
| 7 | Event log | Timestamped heard, handoff, takeover and receipt events in large type | 3 | 1 h | Not started |
| 8 | Find mode | Selected SOS's signal from whichever phone carries it; mean of last ~10 readings; meter, arrow, beeps, vibration; "Signal lost" after 5 s | 4 | 2.5 h | Not started |
| 9 | Demo settings | Battery override slider, fake knock, listen continuously, demo coordinates, keep screen on, reset | 4 | 1 h | Not started |
| 10 | Swap to the real engine | Replace `FakeEngine` with Aayush's engine | 4 (hour 9) | - | Waiting on Aayush |

## Gates that check my work

- [ ] Hour 2: `FakeEngine` in, every screen runs on sample records
- [ ] Hour 5: phone A's SOS shows on C's dashboard (first merge)
- [ ] Hour 9: C's dashboard follows the new carrier after the handoff; real engine replaces `FakeEngine`
- [ ] Hour 13: full loop runs once (SOS, receipt, sound, handoff, takeover, Find); feature freeze
- [ ] Hour 20: backup video recorded

## On stage (demo script)

I play the victim: send the SOS on phone A ("2 people, trapped, injured"), knock beside A, drop A's battery to 8% in Demo settings, switch A off, then hide phones A and B in the bag for the Find act.

## Open questions

- [ ] Hour 5 gate: bare SOS list in batch 2, or does the gate check the Event log? Ask Aayush.
- [ ] Is the branch name `kushal` fine, or should it be `app-lead/<feature>`? Ask Aayush.
- [ ] Priority score: until Aman's lands, use a local copy of the Contract 5 formula on the dashboard?