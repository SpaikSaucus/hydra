Feature: Achievements
  Milestones unlock from total completed days and the best streak.

  Background:
    Given today is "2026-06-14"

  Scenario: The first completed day unlocks the first achievement
    Given a completed day on "2026-06-14"
    When the stats are computed
    Then achievement "FIRST_DAY" is unlocked
    And achievement "PERFECT_WEEK" is locked

  Scenario: A seven-day streak unlocks the first-week and perfect-week badges
    Given completed days from "2026-06-08" to "2026-06-14"
    When the stats are computed
    Then achievement "DAYS_7" is unlocked
    And achievement "PERFECT_WEEK" is unlocked
    And achievement "STREAK_3" is unlocked
    And achievement "DAYS_30" is locked
    And achievement "STREAK_14" is locked

  Scenario: A thirty-day streak unlocks the monthly badges but not the gold tier
    Given completed days from "2026-05-16" to "2026-06-14"
    When the stats are computed
    Then achievement "DAYS_30" is unlocked
    And achievement "STREAK_30" is unlocked
    And achievement "STREAK_14" is unlocked
    And achievement "DAYS_100" is locked
    And achievement "STREAK_100" is locked

  Scenario: A hundred-day streak reaches the gold tier
    Given completed days from "2026-03-07" to "2026-06-14"
    When the stats are computed
    Then achievement "DAYS_100" is unlocked
    And achievement "STREAK_100" is unlocked
    And achievement "DAYS_365" is locked
    And achievement "STREAK_365" is locked
