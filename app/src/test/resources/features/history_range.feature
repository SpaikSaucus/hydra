Feature: History date range
  Tapping two bars of the 30-day chart picks an inclusive date range; the day
  list and the aggregates below it are then scoped to that range.

  Background:
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 2000  |
      | 2026-06-09 | 2000 | 1000  |
      | 2026-06-10 | 2000 | 2000  |
      | 2026-06-11 | 2000 | 2400  |
      | 2026-06-13 | 2000 | 500   |

  Scenario: Summarising a picked range
    When the range "2026-06-08" to "2026-06-11" is summarised
    Then the range covers 4 days
    And the range has 4 logged days
    And the range has 3 completed days
    And the range total is 7400 ml
    And the range daily average is 1850 ml
    And the range average completion is 88 percent

  Scenario: A range picked backwards is normalised
    When the range "2026-06-11" to "2026-06-08" is summarised
    Then the range covers 4 days
    And the range total is 7400 ml

  Scenario: Days with no record still count against the daily average
    When the range "2026-06-12" to "2026-06-13" is summarised
    Then the range covers 2 days
    And the range has 1 logged days
    And the range total is 500 ml
    And the range daily average is 250 ml

  Scenario: A single-day range
    When the range "2026-06-09" to "2026-06-09" is summarised
    Then the range covers 1 days
    And the range total is 1000 ml
    And the range average completion is 50 percent

  Scenario: The day list is filtered to the range
    When the days in "2026-06-09" to "2026-06-11" are listed
    Then the listed days are "2026-06-09,2026-06-10,2026-06-11"

  Scenario: A range with no records at all
    When the range "2026-06-01" to "2026-06-05" is summarised
    Then the range has 0 logged days
    And the range total is 0 ml
    And the range average completion is 0 percent
