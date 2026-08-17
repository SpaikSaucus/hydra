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

`testDebugUnitTest` runs three kinds of test, all on the JVM:

- **Cucumber-JVM** over `src/test/resources/features/*.feature` — the behaviour spec (182 scenarios).
  Prefer adding here; use hand-written fakes (`steps/Fakes.kt`), no MockK.
- **Focused JUnit tests** for things Gherkin can't express: `MidnightRekeyTest` (flow re-keying on a
  virtual-time clock, Turbine), `DayLogDaoCascadeTest` (real in-memory Room under Robolectric) and
  `DayLogMigrationTest` (a real on-disk v1 file driven through the schema upgrade).
- **Roborazzi screenshots** (`screenshots/`) — regenerate with `:app:recordRoborazziDebug`.

Per-suite counts aren't printed on success; read `app/build/test-results/testDebugUnitTest/*.xml`.

## Architecture

- **domain/** — pure Kotlin, no Android deps, fully unit-tested (JUnit, no Robolectric):
  `GoalCalculator`, `Hydration` (SIMPLE/ADVANCED policy facade — always resolve goals through it),
  `UnitConverter`, `SeasonInference`, `ScheduleGenerator`, `Redistributor`, `ReminderEvaluator`
  (piecewise morning/afternoon pacing via `AppSettings.morningSharePct`, 65/35 default, range
  45–70), `PauseManager` (pause-for-N-days policy over `HydraConfig.pauses`, max 30 days),
  `MuteManager` (one-day reminder mute over `HydraConfig.remindersMutedDay`),
  `CaffeineCutoff` (advisory window ending at the configured bedtime; the evidence behind the
  9 h constant is in its KDoc — change the number there, not in a string),
  `StreakCalculator` (paused days are neutral for streaks), `AchievementEvaluator`,
  `HistoryAnalytics` (`DateRange`/`RangeSummary` for the active period, `byWeekday`,
  `rollingAverage` + `rollingAverageFor`, 12-week `heatmap`), `IntakeDistribution` (24 hourly
  buckets, peak window, observed morning/afternoon split), `PaceCurve` (the ONE pacing target —
  `ReminderEvaluator` and the Home chart both call `idealAt`), `GoalReachAnalytics` (per-day
  goal-crossing time), `ChartSelectionPolicy` (the range picker's state machine, period→window
  resolution and the chart's calendar `slots`).
  All inputs (time, zone, locale country, now, consumed) are injected. Canonical units:
  ml (Int), kg (Double). Models + ranges in `domain/model/`.
- **data/** — Room (`day_log` immutable per-day snapshot + `intake_entry` soft-deleted),
  Preferences DataStore storing the whole `HydraConfig` as one JSON blob, repositories,
  JSON backup via SAF. Day boundary = **calendar midnight** via `core/time/DayKeyResolver`
  (wake/sleep bound the reminder window only — never the day). `BackupManager` returns a
  `BackupReport` (rows moved) or throws `BackupException` with a neutral `BackupErrorCode`;
  `BackupOutcomes.of` turns either into a `BackupOutcome` the settings screen must show.
- **reminder/** — WorkManager `PeriodicWork` (15 min) → `ReminderEvaluator` decides whether to
  post (returns `PAUSED` during a tracking pause and `MUTED` while today is muted → worker
  cancels any visible notification);
  `HydraNotifier` with actions; `ReminderActionReceiver` (goAsync + IO). No exact alarms.
  We declare no BOOT receiver ourselves — WorkManager's own receiver self-reschedules after
  reboot. WorkManager also merges in normal permissions (ACCESS_NETWORK_STATE, WAKE_LOCK,
  RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE); none grant network access. No INTERNET ever.
- **ui/** — single-Activity Compose. `MainActivity` hosts splash + theme + NavHost. Screens:
  onboarding, home (pause banner + resume, top-bar mute-today toggle, today's pace chart),
  history (streaks/achievements + a page-wide `Period` card — 7/30/90 days or a drawn range —
  driving five charts: day bars with rolling average and drag- or tap-to-pick ranges, weekday
  pattern, hourly distribution, goal-reach times, plus the deliberately unscoped 12-week
  heat-map), settings (simple/advanced gated by `ConfigMode`; pause card + morning/afternoon
  balance slider; export/import snackbar), about. Chart composables live in `ui/components/BarChart.kt` + `Charts.kt`
  and are pure Canvas — no charting dependency. Manual DI via `di/AppContainer` (created in `HydraApp`); ViewModels built with
  the `hydraViewModel {}` helper (exception: `AppViewModel` via its own factory in MainActivity).
- **theme/** — Material 3, water-teal seed, elegant near-black dark scheme, System/Light/Dark.

## Conventions

- No Hilt (manual Service Locator). No internet permission ever.
- Domain stays pure: never reference Android/clock/locale inside it — inject them.
- Goal is always derived from profile, never stored raw. Past days (`closed`) are immutable.
- Room migrations are ALWAYS explicit — there is no `fallbackToDestructiveMigration`, because the
  only user data that can't be recreated is their history. Every schema bump needs a `Migration`
  in `HydraDatabase.kt`, the matching `@ColumnInfo(defaultValue = ...)` on the entity (Room
  validates the default too), and a case in `DayLogMigrationTest`, which drives a real on-disk v1
  file through the upgrade so a drifted migration fails here and not on someone's phone.
  Adding a nullable/defaulted field to the backup DTOs needs NO `BACKUP_SCHEMA_VERSION` bump: an
  older file lacks the key and takes the default, a newer file stays readable thanks to
  `ignoreUnknownKeys`.

## Documentation is part of the change, never a follow-up

A user-visible change is not finished until these are updated **in the same pass** — together they
are the project's record of why anything is the way it is.

- **`CHANGELOG.md`** — Keep a Changelog format, newest version first, grouped
  Added / Changed / Fixed / Removed. Write it for the person USING the app: lead with the symptom
  and what it means for them, not with the class that changed. A fix says what was wrong before.
  Keep it to the changes themselves — it is a public record of the product, not of the process.
- **`README.md` AND `README.es.md`** — a translated pair that must never drift. If a feature
  bullet, a screenshot, a caption or a count changes in one, it changes in the other in the SAME
  edit. Integrate into the section that already covers the topic instead of appending a new one:
  a new chart joins the charts block (and bumps its "N charts" count), a new toggle joins the
  feature bullet it belongs to.
- **This file** — new intentional behaviours, new invariants, new gotchas.
- **Counts** — the scenario/test totals appear in both READMEs and in Build & Test above. Read
  them from `app/build/test-results/testDebugUnitTest/*.xml`, never estimate.
- **`app/build.gradle.kts`** — `versionCode` and `versionName` move together with the changelog
  entry.

NEVER edit the READMEs with shell replacements. PowerShell 5.1 reads these BOM-less UTF-8 files as
ANSI and mojibakes every emoji, em-dash and accent; a Python `re.sub` template like `\1240` parses
as an octal escape and silently rewrites paths. Use the Edit tool, and verify with a mojibake grep
afterwards.

## Cost of a config write (read before adding a settings control)

A `settings.config` emission is cheap to produce and EXPENSIVE downstream: it restarts the Room
"today" queries, re-runs `ensureDayOpen` (SELECT + possible UPDATE) and re-runs the whole history
analytics for the active period. Three rules keep that under control:

- **Persist on release, not per pixel.** `SettingsScreen.LabeledSlider` keeps the dragged value in
  local state and calls `onCommit` from `onValueChangeFinished`. Wiring a `Slider` straight to a
  setter turns one drag into dozens of DataStore file writes and dozens of full cascades.
- **`SettingsRepositoryImpl.config` is `distinctUntilChanged`.** DataStore re-emits on any write to
  the file, and several setters can produce an identical config; identical configs must not cascade.
- **`snapshot()` reads DataStore directly**, never `config.first()` — a value feeding a
  read-modify-write must not come from a conflated stream.

`AppViewModel` holds the only process-lifetime (`Eagerly`) collector, and just one: `boot` and
`theme` both map off a single cached config.

Known growth axis: `observeHistory()` returns EVERY recorded day and the history analytics are
O(days) per emission (~365 rows/year). Fine for years; if it ever matters, bound the query by the
period window rather than making the analytics lazy.

Implementation details that would only matter to whoever is editing the code — which APIs are
still unwired, open follow-ups — live in `docs/internal-notes.md`, which is untracked. Check it
before assuming a component is live, and keep it current.

## Intentional behaviors (not bugs — don't "fix" without asking)

- The OPEN day's goal recalculates when the profile changes (weight/factor/heat), so
  the completion % can jump mid-day; closed days stay frozen. This is the "goal applies
  today and onward" rule.
- `intakeInLastHour` is a GLOBAL rolling 1-hour window (not day-scoped) — it guards the
  physiological ~1 L/h kidney cap across the midnight boundary.
- `wake == sleep` maps to a 24-hour intake window (sleepFromWake 0 → 1440).
- The hydration day is the CALENDAR day (00:00). Wake/sleep only gate reminders. This was
  changed in v1.4 — before that the wake hour was the cut, which filed early-morning water
  under the previous day. `day_boundary.feature` locks the new contract in.
- The "today" flows re-key themselves at midnight. `HydrationRepositoryImpl.dayKeys()` emits the
  current key, then sleeps exactly until the zone's next `atStartOfDay` (never a naive +24 h — a DST
  night is 23 or 25 hours). Before that, a today-flow only re-keyed when the CONFIG happened to
  change, so leaving the app open across midnight left Home bound to yesterday while new drinks
  were already filed under the new day: a ring that refused to move. `MidnightRekeyTest` drives it
  on a clock wired to virtual time. The ticker is not polling — it is one suspended coroutine, alive
  only while a `WhileSubscribed` ViewModel collects.
- `observeTodayEntries()` has NO config dependency, on purpose: since v1.4 the day key derives from
  the calendar alone, so the clock is the only thing that may re-key it.
- Rolling over also happens inside `observeToday()` (not only in `HomeViewModel.init`), which is
  what freezes yesterday without an app restart. It is one idempotent UPDATE that normally matches
  no rows.
- `RangeSummary.dailyAverageMl` divides by the range's CALENDAR days, not by the days that
  have a record, so gaps honestly drag the average down.
- `IntakeDistribution`'s peak window never wraps past midnight (a 23→01 block reads as
  nonsense on the chart); its morning/afternoon split uses the wake→cutoff midpoint, the same
  one `ReminderEvaluator` paces against, so "actual" and "target" are comparable.
- A one-day mute (`remindersMutedDay`) is NOT a pause: it is invisible to `StreakCalculator`
  and to logging, and it self-expires because the stored key stops matching today.
- The Home pace chart reads `LocalTime.now()` in the ViewModel and therefore refreshes on
  state changes rather than ticking live — deliberate, same edge-reads-the-clock rule as
  `ReminderWorker`.
- Screenshots are generated from `screenshots/SampleData.kt`: 12 weeks of data from a fixed
  seed (a tiny LCG, no `Math.random`, no clock) fed through the REAL domain analytics, so the
  PNGs are byte-stable and actually show what the app would compute.
- The history screen has exactly ONE period control (`ui/history` `PeriodSection`), at the top of
  the page. It scopes the day chart, its aggregates, the day list, "when you drink", "when you
  hit your goal" and the weekday pattern; every one of those cards carries a `PeriodBadge` so it
  is obvious which control moves it. Burying the chips inside the "when you drink" card while
  they silently also scoped a second chart is what made the control read as broken.
- The 12-week heat-map is the ONE card that ignores the period, and it says so on screen
  (`heatmap_fixed`). 12 aligned weeks is the whole point of that chart; a chip that appears to do
  nothing is worse than a chip that declares it doesn't apply. Its GRID/BARS toggle is a SETTING
  (`AppSettings.heatmapStyle`), not screen state, so it survives leaving the screen.
- `HistoryAnalytics.weeklyAverages` counts a day with no record as ZERO inside a week (over the
  days that already happened), because the bars are a trend of ADHERENCE. That deliberately
  differs from `byWeekday`, which answers "when I DO log, which weekday is weakest" and so
  averages only the days present. A week with nothing at all is null and draws no bar — the
  "empty is not 0%" rule, one level up.
- The "Pace of a day" card in History is NOT wired to the period, on purpose: it answers a
  one-day question. It steps over RECORDED days only (`olderDay`/`newerDay` carry the neighbours,
  so stepping never re-scans a list the period may have filtered), and rebuilds each day from its
  own frozen `day_log` snapshot — including `morning_share_pct`, added in DB schema v2 precisely
  so moving the balance slider stops redrawing last Tuesday.
- `DayPace.drinks` exists because `actual` can't be used for the per-drink markers: it also
  carries a synthetic origin and a flat tail out to "now", which would draw two drinks that never
  happened. `PaceChart(showNow = false)` for a finished day — a "now" marker on last Tuesday is
  a lie.
- The day chart renders one slot per CALENDAR day of the period (`ChartSelectionPolicy.slots`);
  days with no record are an empty track, never a 0% bar. Picking a range ZOOMS the chart into
  it, and "Show 30 days" is the way back out.
- `BarChart` reports a drag only on release, and paints the live band from its own state. It used
  to report every pointer delta, which — now that a committed range re-zooms the chart — would
  collapse the canvas under the finger and remap the very indices being read.
- `rollingAverageFor` returns an EMPTY list when the window is no longer than the averaging
  window: 7 slots can hold exactly one 7-day point, and one point renders as a lone dot that
  reads as a glitch. The caption is hidden along with the line.
- The "no history yet" screen keys off `hasHistory` (was any day ever recorded), NOT off the
  period's day list. A 7-day period you drank nothing in is an empty PERIOD, not an empty history.
- Export and import always end in a snackbar. `BackupManager` produces `BackupErrorCode`s, never
  sentences (same rule as `WarningCode`); `SettingsScreen.backupMessage` is the single place they
  become words. Cancelling the file picker hands back a null Uri and stays silent — that is the
  user backing out, not a failure.
- `rollingAverage` returns null wherever the 7-slot window isn't fully populated, so the line
  can never claim to be a 7-day mean of 2 days. A gap in the history breaks the line.
- `ChartSelection` invariant: `period == SELECTION` implies `range != null`. Breaking it made
  the "Selection" chip disappear while the window silently fell back to 30 days.
- The manual goal adjustment is ±20% (`Ranges.ADJ_MIN/ADJ_MAX`, widened from ±15% in v1.7).
  Note the slider only persists on release, so the label moves with the finger while DataStore
  hears a single value — see "Cost of a config write".
  `GoalCalculator` clamps and warns with `ADJUSTMENT_CLAMPED`, so an imported config carrying an
  older out-of-range value still resolves to a sane goal.
- `HeatmapLayout` derives the canvas height FROM the width. Pinning the height first capped the
  squares at ~65% of the card. It is a plain-Kotlin object precisely so it can be unit-tested.
- `ChartTint.awayFromCard` is the answer to "this fill vanished on one theme". Alpha over an
  unknown backdrop keeps collapsing: `surfaceVariant` @25-30% is nearly the card itself on BOTH
  the near-black dark card and the very light one — it swallowed the heat-map's empty square in
  dark and the pace chart's drinking-window band in light. Blending away from the card picks the
  direction from the card's own luminance, so the shape survives either way. `HeatmapPalette`
  builds on it, and `chart_ux.feature` asserts the result on the REAL scheme colours.
- The cancel-on-tap detector sits on the history page's whole scrolling `Column`, so a pick can be
  abandoned from anywhere — including on top of another chart. It only exists WHILE a pick is
  pending (`pointerInput(picking)`), and Compose gives child gestures the event first, so bars,
  chips and buttons keep working.
- `HeatmapPalette` builds the heat-map ramp by blending OUTWARDS from the card colour, never by
  stacking alpha. On the dark card (`#121817`) an alpha ramp composited the empty square
  (`surfaceVariant` @25%) and the lowest logged step (accent @22%) down to almost the same
  near-black, so a week with no water looked like a week that missed the goal. It also made the
  direction of the scale theme-dependent, which is why `heatmap_hint` says the colour gets
  STRONGER and never says "darker" — that was only true on the light theme. The grid and the
  legend read the same `ramp()`, and `chart_ux.feature` asserts on the REAL scheme colours that
  every step stays distinguishable and moves further from the card, on both themes.
- Picking a range is all-or-nothing, and starting one always loses the previous one:
  `ChartSelectionPolicy.setPeriod` to a plain period DISCARDS the range (keeping it lit the
  "Selection" chip and could still paint the band over the new window), and `cancel` — a tap on the
  chart card that isn't the chart, or the "Cancel" button next to the anchor read-out — lands in
  exactly the same place as `clear`. One rule to remember instead of a restore stack.
- Chart bars use the accent at 42% alpha for a missed day, NOT the track colour — in the light
  theme those two greys were indistinguishable and short bars disappeared.
- Season inference is OFFLINE only (country → hemisphere → meteorological season); it never
  reads real weather. Indonesia (ID) and Timor-Leste (TL) are treated as SOUTHERN (most of
  their population is south of the equator). Heat mode is always user-overridable.
- The reminder `ReminderWorker` reads `LocalTime.now()` / resolves the hydration day at the
  edge (domain stays pure and is unit-tested with injected time); a `Clock` is deliberately
  NOT injected into the Worker.
- Custom-amount imperial bounds are DERIVED from the canonical ml range (`Ranges.CUSTOM_*`);
  integer fl oz is intentionally coarse.
