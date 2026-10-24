# iOS alarm mission recovery validation

Status (2026-10-10): recovery core implemented and physically observed in the product app. Dynamic foreground opening failed in the user test; automatic app opening with the installed deferred foreground build was subsequently confirmed by the user. No commit or full PRD completion claimed.

## App direct stop and retry contract (2026-10-23)

This change targets app ringing-screen dismissal. It must stop the actual ringing system UUID and await the same native journal/claim/scheduling implementation used by AlarmKit Intents. The app path must not enqueue legacy open/stop events or use legacy import as its return channel. Intent persistence and retry scheduling must still finish before foreground authentication.

- A successful native result carries the latest persisted attempt and reservation UUID. Kotlin adopts that reservation and its original absolute deadline without scheduling a second retry. A retry's system UUID may differ from its source alarm ID; parent token and chain identity must survive dismissal.
- Recovery and direct results share mission projection. Replaying the same occurrence must preserve the mission, deadline and UUID. A terminal or newer retry wins over a delayed direct result; direct revalidation reads a journal-only snapshot without importing legacy events or scheduling retries.
- Native scheduling failure remains a persisted `FAILED` mission. OS stop failure, journal failure and an unsupported `NOT_FOUND` must not fabricate a successful stop. A previously handled matching execution may be adopted only with journal evidence.
- Existing `UNKNOWN`/`IN_FLIGHT` attempts are not automatically resubmitted because an OS query is empty. Legacy IDs and deadlines remain authoritative; the change does not promise to heal previously imported `UNKNOWN` missions.
- Mission and record ACKs remain independent. Record persistence or owner capture failure must not erase retry authority. Existing location acquisition grace, overlapping-mission policy, weekday schedules and late-success cancellation obligations remain required regression coverage.
- OS stop and journal commit are separate operations. Process termination after OS stop but before commit is not covered by a no-loss guarantee. Restart recovery after a durable commit must preserve the recorded execution and deadline.
- The `recordOpen`/`runtimeCustom` producer remains for protocol compatibility, with no callers in the new runtime dismissal path. The unused Kotlin `recordOpenAwait` helper was removed. Existing stored event decoding and legacy import remain available for older data.

### Validation evidence for this change

- CI now compiles and runs `AlarmRecoveryCommandTests.swift` with the actual Foundation command and journal sources immediately after the existing journal runner. The workflow contract suite verifies that the command source, test source and executable invocation remain wired into iOS CI.
- `python3 -B -m unittest discover -s ci -p 'test_workflows.py' -v`: 9 tests PASS locally on 2026-10-23. This verifies workflow contracts, not AlarmKit or the Swift/Kotlin bridge.
- `python3 -B -m unittest discover -s ci -p 'test_*.py'`: all 110 Python helper tests PASS locally on 2026-10-23.
- Final native host run: all 14 command checks and 31 journal checks PASS. The command suite uses the real extracted core and temporary journal with only OS operations faked, including interrupted PENDING replay, tokenless roots, post-stop storage failure, compaction during OS await, and late terminal cancellation.
- Although `xcode-select -p` reports `/Library/Developer/CommandLineTools`, explicit `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` selects Xcode 27.0, build 27A266a. The unsigned Debug app build for a generic iOS simulator destination succeeded, type-checking the new Swift/Kotlin bridge. Final build evidence is in `/tmp/ringout-direct-xcodebuild-final.log` (`BUILD SUCCEEDED`); a simulator app build does not establish real AlarmKit behavior.
- Final `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :shared:iosSimulatorArm64Test --console=plain`: all 1,196 tests PASS, zero failures/errors/skips. This includes direct retry projection with parent arrival evidence: no legacy recovery read, no reservation recovery, no Kotlin scheduling, and cancellation of both parent/child UUIDs. Evidence: `/tmp/ringout-direct-kotlin-tests-final.log` and `shared/build/test-results/iosSimulatorArm64Test/`. An initial fixture failure was corrected to exercise a parent after its deadline/location grace; product policy was preserved.
- Device scenarios still require fresh evidence for original/retry app dismissal, locked Intent stop with canceled unlock, duplicate stop, scheduling denial/limits, restart after commit and terminal races. Record the OS/app build, timestamp, occurrence and reservation UUID for each observed result; avoid full snapshots, coordinates or account data. Historical physical evidence below applies only to the builds explicitly described there.

## Implemented behavior

- Stop Intent commits an immutable source snapshot, stop time, original deadline, occurrence token and next reservation before any foreground authentication.
- Native journal uses interprocess locking, atomic replacement and protection available after the first device unlock. Mission and record ACKs are independent; consumed projections do not delete recovery authority.
- Original weekday recurring schedules remain. Root duplicate detection retains observation / 10-second coalescing limits; arbitrary delayed duplicates cannot be distinguished exactly. Retry tokens are unique and exact.
- Unknown/in-flight daemon calls are queried, never resubmitted based on elapsed time or an absent OS list. Overdue pending/failed attempts remain expired without automatic rearming or a changed deadline.
- Disabled/deleted sources reject new roots. Existing chains restore their immutable snapshot. Unknown stop-time owners remain local and are not attributed to the current login account.
- Terminal tombstones precede local clearing; cancellation and record replay are independent. Late OS success retains a cancellation obligation. Overlapping unrelated missions are skipped by the existing single-mission policy.
- Native retry recovery preserves the preceding mission's location acquisition grace. Terminal replay uses the actual completed occurrence, even if a later retry stop was already accepted.
- Reconciliation protects journal reservation IDs and the existing legacy deadline UUID, and aborts destructive cleanup on authority read failure.
- Legacy active projections import their occurrence, deadline, next token, snapshot and captured owner. An existing legacy deadline without enough stop evidence remains protected and on its existing recovery path; no fabricated stop time or historical parent identity is added.
- Native inbox events and terminal journal chains are cleaned only after durable-consumption and Room projection proofs; candidate fingerprints are rechecked before deletion/compaction. Native writes use interprocess locking and durable atomic replacement, and cleanup failures preserve data for a later pass.

## Native data cleanup (2026-10-13)

- Consumed inbox events stay through the 10-second deduplication window. Record-projecting events also require their record ACK and a transactional Room proof; terminal chains require the expected history, movement parent/children and occurrence outbox rows to exist and be settled before compaction to tombstones.
- `AUTH_PENDING` records remain protected. `LOCAL_ONLY` cleanup requires the local activity/occurrence projection. `SERVER` cleanup checks ownership, expected outbox identity/content and `SENT` state; an empty or incomplete expected-row set cannot prove completion.
- Cleanup is requested during startup/auth replay, runtime recovery and sync flush. A candidate inside its deduplication window gets one retry at the window boundary. Mission, deadline and reservation references protect their chains from compaction.
- Stop recovery reports handled, fallback or preserved explicitly. Unknown retries stay preserved rather than being promoted to a root fallback.
- No alarm snapshots, active ringing observations or retry authority are removed by this cleanup path.

## Cleanup validation evidence

- Native journal host tests: 30 PASS, including v1-to-v2 journal decoding, stale fingerprint protection, terminal-chain compaction, tokenless legacy-root routing after tombstoning, legacy-import tombstone protection and concurrent journal access.
- Swift frontend parse: PASS for the inbox, recovery driver, journal and intent sources. This checks syntax only; it does not type-check the iOS app target or its Kotlin/Native bridge.
- Kotlin/Native iOS shared test sources compile, including cleanup coordinator proof-gate, dedup-window boundary and Room migration tests. The simulator test task reaches Xcode linking but cannot complete in this environment because `xcrun xcodebuild -version` is unavailable; no iOS simulator test run is claimed.
- Android shared host tests: 934 PASS, 0 failures/errors/skips. Android release lint, R8 output verification, unsigned release bundle creation and unsigned-bundle verification: PASS.
- Cross-process inbox integration, on-device cleanup/relaunch behavior and actual Room/native bridge execution remain unverified on an iOS device.

## Evidence

- Native journal: 25 host checks PASS, including parallel reader/writer, corrupt/unsupported-schema fail-closed, unknown scheduling, late success/cancel races, observed original execution identity, snapshot preservation, terminal occurrence persistence and bounded presentation diagnostics.
- Removed feasibility probe: 6 evidence-storage host checks passed during development; the standalone probe and its dedicated tests were removed before repository publication.
- iOS shared tests: 1,080 PASS (fresh final run). Device Shared framework: PASS.
- Signed product build: PASS; initial product build installed and launched. Deferred candidate installed; direct CLI launch was rejected while the device was locked. Extracted StopAlarmIntent metadata contains supportedModes=5 and openAppWhenRun=true for the deferred candidate.
- Android shared host tests: 934 PASS; release lint PASS with the project unsigned CI verification option.
- Development feasibility probe: locked stop durability, scheduling before authentication and a repeated stop were observed; the user confirmed a retry after canceling unlock. The standalone probe and its device logs were removed before repository publication; this paragraph retains the historical result only.

## Product physical result

The user confirmed retry alarms and mission execution. The product journal contained one chain with four accepted stop attempts, each with exactly 60 seconds from stop to deadline. Parent tokens linked the retries; explicit terminal state was persisted and all four reservations were cancellation-confirmed. Owner was uncaptured and record ACKs remained false, as requested. An anonymized projection is in `ios-alarm-mission-recovery-physical/2026-10-10-dynamic/summary.json`; the raw device file remains only under `/tmp`.

The user also reported that unlocking after stopping did not open the app automatically. Optional dynamic continuation previously swallowed transition errors. The new candidate declares background + deferred foreground and returns only after durable stop/scheduling; the system requests foreground on completion. It records the post-commit mode/protected-data state and scene activation timestamps. The user subsequently confirmed automatic app opening works with this installed build. This is user-observed physical evidence; a follow-up journal export failed because CoreDevice connectivity/service assertions were unavailable (error 4016), so post-commit/scene timing for that run was not retrieved.

Official contract: https://developer.apple.com/documentation/appintents/intentmodes/foregroundmode/deferred

## Remaining device validation

Automatic app opening on the deferred build is confirmed by the user. Locked retry stop → cancel unlock → retry remains scheduled still needs separate confirmation on this build. Locked-before-authentication durability was physically proven for the preceding dynamic build and must be rechecked for this mode. Product permission denial, reservation limits, process interruption and reboot before first unlock are not physically verified. Xcode device screen sharing is unavailable for the connected iOS version, so physical lock/unlock actions require the user.
