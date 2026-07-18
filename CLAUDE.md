# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

Hydra — offline Android water-intake tracker + reminders. Kotlin + Jetpack Compose + Material 3.
Package `com.personal.hydra`. minSdk 26, target/compile 34, JDK 17, AGP 8.2.2, Gradle 8.5.

## Build & Test

Docker Desktop must be running. There is NO local JDK 17 / Android SDK.

```bash
# Reusable toolchain image (JDK 17 + SDK 34 + Gradle 8.5)
docker build -f docker/toolchain.Dockerfile -t hydra-toolchain docker/

# Compile + unit tests (mount project + a persistent gradle cache volume)
docker run --rm -v "$PWD:/project" -v hydra-gradle:/root/.gradle hydra-toolchain \
    gradle :app:assembleDebug :app:testDebugUnitTest --no-daemon

# Signed release APK -> app-output/Hydra.apk
cp .env.example .env && ./build-apk.sh
```

NOTE: when running gradle in the background, do NOT pipe to `tail` — the pipeline reports
tail's exit code, masking gradle failures. Redirect to a file or read the full output.

## Architecture

- **domain/** — pure Kotlin, no Android deps, fully unit-tested (JUnit, no Robolectric):
  `GoalCalculator`, `Hydration` (SIMPLE/ADVANCED policy facade — always resolve goals through it),
  `UnitConverter`, `SeasonInference`, `ScheduleGenerator`, `Redistributor`, `ReminderEvaluator`
  (piecewise morning/afternoon pacing via `AppSettings.morningSharePct`, 65/35 default, range
  45–70), `PauseManager` (pause-for-N-days policy over `HydraConfig.pauses`, max 30 days),
  `StreakCalculator` (paused days are neutral for streaks), `AchievementEvaluator`. All inputs
  (time, locale country, now, consumed) are injected. Canonical units: ml (Int), kg (Double).
  Models + ranges in `domain/model/`.
- **data/** — Room (`day_log` immutable per-day snapshot + `intake_entry` soft-deleted),
  Preferences DataStore storing the whole `HydraConfig` as one JSON blob, repositories,
  JSON backup via SAF. Day boundary = wake hour (not midnight) via `core/time/DayKeyResolver`.
- **reminder/** — WorkManager `PeriodicWork` (15 min) → `ReminderEvaluator` decides whether to
  post (returns `PAUSED` during a tracking pause → worker cancels any visible notification);
  `HydraNotifier` with actions; `ReminderActionReceiver` (goAsync + IO). No exact alarms.
  We declare no BOOT receiver ourselves — WorkManager's own receiver self-reschedules after
  reboot. WorkManager also merges in normal permissions (ACCESS_NETWORK_STATE, WAKE_LOCK,
  RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE); none grant network access. No INTERNET ever.
- **ui/** — single-Activity Compose. `MainActivity` hosts splash + theme + NavHost. Screens:
  onboarding, home (pause banner + resume), history (streaks/achievements/30-day chart),
  settings (simple/advanced gated by `ConfigMode`; pause card + morning/afternoon balance
  slider), about. Manual DI via `di/AppContainer` (created in `HydraApp`); ViewModels built with
  the `hydraViewModel {}` helper (exception: `AppViewModel` via its own factory in MainActivity).
- **theme/** — Material 3, water-teal seed, elegant near-black dark scheme, System/Light/Dark.

## Conventions

- No Hilt (manual Service Locator). No internet permission ever.
- Domain stays pure: never reference Android/clock/locale inside it — inject them.
- Goal is always derived from profile, never stored raw. Past days (`closed`) are immutable.

## Intentional behaviors (not bugs — don't "fix" without asking)

- The OPEN day's goal recalculates when the profile changes (weight/factor/heat), so
  the completion % can jump mid-day; closed days stay frozen. This is the "goal applies
  today and onward" rule.
- `intakeInLastHour` is a GLOBAL rolling 1-hour window (not day-scoped) — it guards the
  physiological ~1 L/h kidney cap across the wake boundary.
- `wake == sleep` maps to a 24-hour intake window (sleepFromWake 0 → 1440).
- Season inference is OFFLINE only (country → hemisphere → meteorological season); it never
  reads real weather. Indonesia (ID) and Timor-Leste (TL) are treated as SOUTHERN (most of
  their population is south of the equator). Heat mode is always user-overridable.
- The reminder `ReminderWorker` reads `LocalTime.now()` / resolves the hydration day at the
  edge (domain stays pure and is unit-tested with injected time); a `Clock` is deliberately
  NOT injected into the Worker.
- Custom-amount imperial bounds are DERIVED from the canonical ml range (`Ranges.CUSTOM_*`);
  integer fl oz is intentionally coarse.
