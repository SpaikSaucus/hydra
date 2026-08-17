Feature: Mute reminders for the rest of today
  The Home top bar can silence reminders for the current day with one tap. It is
  NOT a tracking pause: logging keeps working and streaks are untouched. The
  mute expires by itself when the calendar day changes.

  Scenario: Muting today silences reminders
    Given reminders muted on "2026-01-15"
    When the mute evaluation runs at "10:00" on "2026-01-15"
    Then the mute decision reason is "MUTED"
    And the mute decision posts no reminder

  Scenario: A mute set yesterday does not silence today
    Given reminders muted on "2026-01-14"
    When the mute evaluation runs at "10:00" on "2026-01-15"
    Then the mute decision posts a reminder

  Scenario: With no mute stored, reminders behave normally
    Given no reminder mute
    When the mute evaluation runs at "10:00" on "2026-01-15"
    Then the mute decision posts a reminder

  Scenario: A tracking pause wins over a one-day mute
    Given reminders muted on "2026-01-15"
    And a tracking pause covering "2026-01-10" to "2026-01-20"
    When the mute evaluation runs at "10:00" on "2026-01-15"
    Then the mute decision reason is "PAUSED"

  Scenario: A corrupt muted day never silences reminders
    Given reminders muted on "not-a-date"
    When the mute evaluation runs at "10:00" on "2026-01-15"
    Then the mute decision posts a reminder

  Scenario: Muting is not a pause — an unmet muted day still breaks the streak
    Given a muted-day streak completed on "2026-01-13"
    And a muted-day streak completed on "2026-01-14"
    And a muted-day streak incomplete on "2026-01-15"
    When the muted-day streak is computed for "2026-01-16"
    Then the muted-day current streak is 0

  Scenario: The muted day survives a backup round-trip
    Given reminders muted on "2026-01-15"
    When the muted config is encoded and decoded as backup JSON
    Then the decoded muted day is "2026-01-15"

  Scenario: An older config decodes with no mute
    When an old config JSON without the mute field is decoded
    Then the decoded config has no muted day
