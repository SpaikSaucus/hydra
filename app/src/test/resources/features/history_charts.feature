Feature: History chart analytics
  Weekday pattern, 7-day rolling average and the 12-week calendar heat-map that
  sit under the 30-day chart.

  Scenario: Average completion per weekday
    Given a history of days
      | date       | goal | total |
      | 2026-06-06 | 2000 | 1000  |
      | 2026-06-08 | 2000 | 2000  |
      | 2026-06-09 | 2000 | 2000  |
      | 2026-06-13 | 2000 | 500   |
    When the weekday pattern is computed
    Then the weekday pattern has 7 entries
    And the weekday average for "SATURDAY" is 38 percent
    And the weekday average for "MONDAY" is 100 percent
    And the weekday "SUNDAY" has 0 days
    And the weakest weekday is "SATURDAY"

  # A "7-day average" built from 2 days is not a 7-day average. The line must
  # stay silent until it can honour its own legend.
  Scenario: The 7-day average says nothing before there are seven days
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 2000  |
      | 2026-06-09 | 2000 | 1000  |
      | 2026-06-10 | 2000 | 2000  |
    When the rolling average is computed
    Then the rolling average has no points

  Scenario: The 7-day average starts on the seventh day
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 2000  |
      | 2026-06-09 | 2000 | 2000  |
      | 2026-06-10 | 2000 | 2000  |
      | 2026-06-11 | 2000 | 0     |
      | 2026-06-12 | 2000 | 0     |
      | 2026-06-13 | 2000 | 0     |
      | 2026-06-14 | 2000 | 0     |
    When the rolling average is computed
    Then the rolling average has 7 points
    And the rolling average at index 0 is empty
    And the rolling average at index 5 is empty
    And the rolling average at index 6 is 43 percent

  # 2026-06-11 is missing, so every window straddling it stays silent; the line
  # only resumes once 7 consecutive recorded days fit behind a slot again.
  Scenario: A day with no record breaks the average line
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 2000  |
      | 2026-06-09 | 2000 | 2000  |
      | 2026-06-10 | 2000 | 2000  |
      | 2026-06-12 | 2000 | 2000  |
      | 2026-06-13 | 2000 | 2000  |
      | 2026-06-14 | 2000 | 2000  |
      | 2026-06-15 | 2000 | 2000  |
      | 2026-06-16 | 2000 | 2000  |
      | 2026-06-17 | 2000 | 2000  |
      | 2026-06-18 | 2000 | 2000  |
    When the rolling average is computed
    Then the rolling average has 11 points
    And the rolling average at index 6 is empty
    And the rolling average at index 9 is empty
    And the rolling average at index 10 is 100 percent

  # With the bar chart following the period, a 7-day period gives the 7-day
  # average exactly one plottable slot. One point cannot draw a line.
  Scenario: A period as short as the average window draws no line at all
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 2000  |
      | 2026-06-09 | 2000 | 2000  |
      | 2026-06-10 | 2000 | 2000  |
      | 2026-06-11 | 2000 | 2000  |
      | 2026-06-12 | 2000 | 2000  |
      | 2026-06-13 | 2000 | 2000  |
      | 2026-06-14 | 2000 | 2000  |
    When the rolling average line is computed
    Then the rolling average has no points

  Scenario: One day more than the average window is enough for a line
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 2000  |
      | 2026-06-09 | 2000 | 2000  |
      | 2026-06-10 | 2000 | 2000  |
      | 2026-06-11 | 2000 | 2000  |
      | 2026-06-12 | 2000 | 2000  |
      | 2026-06-13 | 2000 | 2000  |
      | 2026-06-14 | 2000 | 2000  |
      | 2026-06-15 | 2000 | 1000  |
    When the rolling average line is computed
    Then the rolling average has 8 points
    And the rolling average at index 6 is 100 percent
    And the rolling average at index 7 is 93 percent

  Scenario: Going over the goal never pushes the average past 100%
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 6000  |
      | 2026-06-09 | 2000 | 2000  |
      | 2026-06-10 | 2000 | 2000  |
      | 2026-06-11 | 2000 | 2000  |
      | 2026-06-12 | 2000 | 2000  |
      | 2026-06-13 | 2000 | 2000  |
      | 2026-06-14 | 2000 | 0     |
    When the rolling average is computed
    Then the rolling average at index 6 is 86 percent

  Scenario: The heat-map covers 12 aligned weeks ending on today's week
    Given a history of days
      | date       | goal | total |
      | 2026-06-13 | 2000 | 500   |
      | 2026-06-14 | 2000 | 2000  |
    When the heatmap is computed for "2026-06-14"
    Then the heatmap has 12 columns
    And every heatmap column has 7 cells
    And the heatmap starts on "2026-03-23"
    And the heatmap ends on "2026-06-14"
    And the heatmap cell for "2026-06-13" is 25 percent
    And the heatmap cell for "2026-06-01" is empty

  Scenario: Days after today stay empty in the heat-map
    Given a history of days
      | date       | goal | total |
      | 2026-06-10 | 2000 | 2000  |
    When the heatmap is computed for "2026-06-10"
    Then the heatmap ends on "2026-06-14"
    And the heatmap cell for "2026-06-11" is empty

  # The bar rendering of the same 12 weeks. A day with no record counts as ZERO
  # here, because these bars are a trend of adherence: a week you forgot to log
  # is not a perfect week.
  Scenario: A week is averaged over its days, gaps included
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 2000  |
      | 2026-06-09 | 2000 | 2000  |
      | 2026-06-10 | 2000 | 1000  |
    When the heatmap is computed for "2026-06-14"
    And the weekly averages are computed for "2026-06-14"
    Then there are 12 weekly averages
    And the weekly average for week 11 is 36 percent

  Scenario: A week with no record at all draws no bar
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 2000  |
    When the heatmap is computed for "2026-06-14"
    And the weekly averages are computed for "2026-06-14"
    Then the weekly average for week 0 is empty

  # The last column is the current week, so only the days up to today count —
  # otherwise every Monday would look like a 14% week.
  Scenario: Days still in the future do not drag the current week down
    Given a history of days
      | date       | goal | total |
      | 2026-06-08 | 2000 | 2000  |
    When the heatmap is computed for "2026-06-08"
    And the weekly averages are computed for "2026-06-08"
    Then the weekly average for week 11 is 100 percent
