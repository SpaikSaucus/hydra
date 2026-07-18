Feature: Pause tracking
  The user can pause tracking for N days (presets 5/10/30, hard max 30) instead
  of uninstalling. While paused no reminder is ever posted, and paused days are
  NEUTRAL for streaks: they neither break nor extend a run — unless the goal was
  still met that day, in which case it counts normally. Resuming early keeps the
  already-elapsed paused days neutral.

  Scenario: No reminder while paused
    Given a pause config from "2026-01-10" to "2026-01-14"
    When the paused evaluation runs with 0 ml at "10:00" on "2026-01-12"
    Then the paused decision reason is "PAUSED"
    And the paused decision posts no reminder

  Scenario: Reminders resume automatically after the pause expires
    Given a pause config from "2026-01-10" to "2026-01-14"
    When the paused evaluation runs with 0 ml at "10:00" on "2026-01-15"
    Then the paused decision posts a reminder

  Scenario: A 5-day pause covers exactly 5 days counting the first one
    Given no pause history
    When a pause of 5 days starts on "2026-01-10"
    Then the last pause period ends on "2026-01-14"
    And the remaining pause days on "2026-01-10" are 5
    And the remaining pause days on "2026-01-14" are 1
    And the remaining pause days on "2026-01-15" are 0

  Scenario: Pause duration is clamped to the 30-day maximum
    Given no pause history
    When a pause of 45 days starts on "2026-01-10"
    Then the last pause period ends on "2026-02-08"

  Scenario: Resuming early keeps the already-elapsed days recorded
    Given no pause history
    When a pause of 10 days starts on "2026-01-10"
    And the pause is resumed early on "2026-01-13"
    Then the last pause period ends on "2026-01-12"
    And the remaining pause days on "2026-01-13" are 0

  Scenario: Resuming the same day the pause started removes it entirely
    Given no pause history
    When a pause of 10 days starts on "2026-01-10"
    And the pause is resumed early on "2026-01-10"
    Then there is no pause history

  Scenario: Starting a new pause while one is active truncates the old one
    Given no pause history
    When a pause of 10 days starts on "2026-01-10"
    And a pause of 5 days starts on "2026-01-12"
    Then the last pause period ends on "2026-01-16"
    And the remaining pause days on "2026-01-11" are 1
    And the remaining pause days on "2026-01-12" are 5
    And the remaining pause days on "2026-01-17" are 0

  Scenario: A paused gap does not break the streak
    Given a streak day completed on "2026-06-10"
    And a streak day completed on "2026-06-11"
    And a pause covering "2026-06-12" to "2026-06-13"
    And a streak day completed on "2026-06-14"
    When the paused stats are computed for "2026-06-14"
    Then the paused current streak is 3
    And the paused best streak is 3

  Scenario: A day completed during a pause still counts toward the streak
    Given a streak day completed on "2026-06-11"
    And a pause covering "2026-06-12" to "2026-06-13"
    And a streak day completed on "2026-06-12"
    And a streak day completed on "2026-06-13"
    And a streak day completed on "2026-06-14"
    When the paused stats are computed for "2026-06-14"
    Then the paused current streak is 4

  Scenario: An unpaused gap still breaks the streak
    Given a streak day completed on "2026-06-10"
    And a pause covering "2026-06-12" to "2026-06-12"
    And a streak day completed on "2026-06-14"
    When the paused stats are computed for "2026-06-14"
    Then the paused current streak is 1
    And the paused best streak is 1

  Scenario: An incomplete paused day does not drag the average down
    Given a streak day completed on "2026-06-10"
    And a pause covering "2026-06-11" to "2026-06-11"
    And a streak day incomplete on "2026-06-11"
    When the paused stats are computed for "2026-06-11"
    Then the paused average percent is 100

  Scenario: Pause history survives a config JSON round-trip
    Given a pause config from "2026-01-10" to "2026-01-14"
    When the config is encoded and decoded as backup JSON
    Then the decoded config still has a pause on "2026-01-12"

  Scenario: A config JSON from an older version still decodes with safe defaults
    When an old config JSON without the new fields is decoded
    Then the decoded morning share is 65
    And the decoded config has no pauses
