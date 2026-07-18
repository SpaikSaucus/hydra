Feature: Streaks and stats
  A day counts only at 100% of its goal. Today, still in progress, does not
  break the current streak.

  Background:
    Given today is "2026-06-14"

  Scenario: Consecutive completed days build the current streak
    Given a completed day on "2026-06-12"
    And a completed day on "2026-06-13"
    And a completed day on "2026-06-14"
    When the stats are computed
    Then the current streak is 3
    And the best streak is 3
    And the days completed is 3

  Scenario: Today in progress keeps yesterday's streak alive
    Given a completed day on "2026-06-12"
    And a completed day on "2026-06-13"
    And an incomplete day on "2026-06-14"
    When the stats are computed
    Then the current streak is 2
    And the days completed is 2

  Scenario: A calendar gap breaks the streak
    Given a completed day on "2026-06-10"
    And a completed day on "2026-06-13"
    And a completed day on "2026-06-14"
    When the stats are computed
    Then the current streak is 2
    And the best streak is 2
