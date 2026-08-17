# Changelog

All notable changes to Hydra are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## 1.10 — 2026-08-16

### Added

- **Caffeine notice on Home.** A one-line advisory above *Add water*, from 9 h before your
  configured bedtime, suggesting you ease off coffee, tea and mate. The threshold is expressed in
  hours before bedtime, so changing your sleep time moves it. On by default, switchable from
  *Settings › Schedule*, and purely informational — it touches neither the goal, nor the streaks,
  nor the reminders.
  The 9 h rounds the 8.8 h that Gardiner et al. (2023, *Sleep Medicine Reviews*, meta-analysis of
  24 studies) report as the point where a standard coffee stops measurably shortening sleep. It is
  stated as a floor because dose drives the spread: Drake et al. (2013) put the sleep-hygiene
  minimum at 6 h, and a 2024 crossover trial found 400 mg still fragmenting sleep 8 h out.
- **Weekly bars for the 12-week card.** A second reading of the same 12 weeks: one bar per week at
  that week's average completion, for trend rather than texture. The choice between *Calendar* and
  *Bars* is remembered until you change it. Days with no record count as zero inside a week, so a
  week you forgot to log doesn't look perfect; a week with nothing at all draws no bar.
- **Pace of a day, in History.** The Home pace curve for any recorded day, with its own `‹ date ›`
  stepper that walks recorded days only. Deliberately independent of the page-wide period. Each
  day is rebuilt from its own frozen snapshot (goal, wake time, night cutoff), so changing your
  profile never rewrites how a past day is drawn.
- **A dot per drink on the pace curve**, on Home and in History, so the curve shows *when* each
  glass landed instead of only that it climbed.

### Changed

- **Each day now stores the morning/afternoon balance it ran on** (database schema v2). Before,
  the pace curve of a finished day was redrawn against your *current* balance, so moving the
  slider rewrote how last Tuesday looked. Days recorded before this release take the app default
  (65%), since the value they actually used was never stored. Existing history is migrated in
  place — nothing is dropped, and the JSON backup format did not need a version bump.
- **Days with no record in the 12-week grid** are now a dashed outline instead of a faint fill.
  Shape separates them from a logged-but-weak day at a glance, which colour alone cannot do on a
  square that small.
- **Cancelling a range pick** now works from anywhere on the History page — including on top of
  another chart — not just from the day-chart card.

### Fixed

- **The drinking-window band on the pace chart was invisible in the light theme.** It was
  `surfaceVariant` at 30% alpha composited over an almost identical card (`#E4ECEB` on `#E8EFEE`).
  The band is now blended away from the card colour, the same fix the heat-map ramp got in 1.9, so
  it survives both themes.

## 1.9 — 2026-08-16

### Fixed

- **The 12-week heat-map was unreadable in the dark theme.** The ramp stacked alpha over a
  near-black card, so an empty square (`#1D2423`) and the lowest logged step both collapsed onto
  the card colour (`#121817`) — a week with no water looked like a week that missed the goal. The
  ramp is now blended outwards from the card, and the legend no longer claims "the darker, the
  closer", which was only true in the light theme.

### Changed

- **Picking a range is all-or-nothing.** Choosing 7/30/90 days now discards a stored range instead
  of keeping the *Selection* chip lit over a window it no longer describes, and a *Cancel* button
  sits next to the pending-anchor read-out.

## 1.8 — 2026-08-16

### Fixed

- **The "today" flows now re-key themselves at midnight.** Leaving the app open across 00:00 kept
  Home bound to yesterday while new drinks were already filed under the new day — a progress ring
  that refused to move. The repository sleeps exactly until the zone's next start-of-day, never a
  naive +24 h, so a DST night of 23 or 25 hours still lands correctly.

### Changed

- **Settings sliders persist on release, not per pixel.** Every persisted value restarts the Room
  "today" queries and re-runs the whole history analytics, so one drag used to cost dozens of full
  cascades and dozens of disk writes.
- Identical configs no longer cascade (`distinctUntilChanged`), and the process-lifetime config
  collector went from two to one.

### Removed

- Internal APIs that no longer had a purpose, trimmed to keep the data layer to what the app
  actually uses.

## 1.7 — 2026-07-29

### Added

- **One period for the whole history page** — 7, 30 or 90 days, or a range you draw — replacing
  chips buried inside one card that silently scoped a second one. Every card it governs carries a
  matching badge; the 12-week heat-map is the one deliberate exception and says so.
- The day chart follows the period, and picking a range zooms into it.
- **Export and import now end in a confirmation** stating how many days and entries moved, or why
  they failed. Both used to be completely silent.

### Changed

- The manual goal adjustment widened from ±15% to ±20%.

## 1.6 — 2026-07-29

### Fixed

- The 7-day average line no longer spans a window it cannot describe, and the heat-map squares now
  fill the card width instead of ~65% of it.

## 1.5 — 2026-07-29

### Added

- Today's pace curve on Home, the weekday pattern, the 12-week heat-map and the goal-completion
  time chart.

## 1.4 — 2026-07-29

### Fixed

- **The hydration day is the calendar day.** Water drunk after midnight used to be filed under the
  previous day, because the wake hour was the cut. Wake and sleep times now only bound the hours
  in which reminders may be sent.

### Added

- Mute reminders for the rest of today, from the Home top bar.
- Pick a date range on the day chart, and the hour-of-day distribution chart.

## 1.3 — 2026-07-18

### Fixed

- **A black strip above the bottom navigation bar**, which ate screen space on every tab. The
  nested `Scaffold`s were each applying the system bottom inset, so it was counted twice. The
  `NavHost` now consumes the inset its parent already handled.

## 1.2 — 2026-07-18

### Fixed

- **Changing your profile mid-day could delete that day's logged intakes.** Refreshing the open
  day's snapshot used `@Insert(REPLACE)`, which deletes the `day_log` row before reinserting it —
  and the `intake_entry` foreign key cascades on delete. It is an `@Update` now, guarded by a test
  that drives a real in-memory database.

### Changed

- Hardened the ignore rules so credentials, the signing keystore and exported health data can
  never be committed.

## 1.1 — 2026-07-17

### Added

- **Pause tracking** for 5, 10 or 30 days — an alternative to uninstalling when something stops
  you from logging for a while. Paused days are neutral for streaks: they don't break one, and
  they still count if you hit the goal anyway.
- **Morning/afternoon balance**: the goal is paced more heavily into the first half of the
  drinking window, to reduce evening accumulation.

## 1.0 — 2026-07-17

### Added

- First release: daily goal derived from weight and factor with heat mode, WorkManager reminders
  with quick actions, history with streaks and achievements, onboarding, JSON export/import,
  Material 3 light and dark themes, Spanish and English, metric and imperial.

