Feature: Pace curve and goal-completion time
  The Home chart draws the same pacing target the reminders use, and the history
  screen reports at what time of day the goal was actually met.

  Scenario Outline: The pacing target follows the clock
    Given a pace with goal 2000 ml, wake "07:00", window 780 minutes and morning share 65
    Then the pace target at "<time>" is <ml> ml

    Examples:
      | time  | ml   |
      | 05:00 | 0    |
      | 07:00 | 0    |
      | 10:15 | 650  |
      | 13:30 | 1300 |
      | 20:00 | 2000 |
      | 23:00 | 2000 |

  Scenario: Being behind the plan is reported as a negative delta
    Given a pace with goal 2000 ml, wake "07:00", window 780 minutes and morning share 65
    And a pace intake of 500 ml at "08:00"
    When the pace is computed at "10:15" in zone "America/Argentina/Buenos_Aires"
    Then the pace actual is 500 ml
    And the pace ideal is 650 ml
    And the pace delta is -150 ml

    # The curve must reach "now" even when nothing was logged after the last drink.
  Scenario: The actual curve is extended up to the current time
    Given a pace with goal 2000 ml, wake "07:00", window 780 minutes and morning share 65
    And a pace intake of 900 ml at "08:00"
    When the pace is computed at "16:00" in zone "America/Argentina/Buenos_Aires"
    Then the pace actual is 900 ml
    And the last actual point is at minute 960

  # A dot per drink, so the curve says WHEN each glass happened. The actual curve
  # can't be used for this: it carries a synthetic origin and a flat tail out to
  # "now", which would draw two drinks that never happened.
  Scenario: Every drink is marked on the curve, and nothing else is
    Given a pace with goal 2000 ml, wake "07:00", window 780 minutes and morning share 65
    And a pace intake of 500 ml at "08:00"
    And a pace intake of 300 ml at "11:30"
    When the pace is computed at "16:00" in zone "America/Argentina/Buenos_Aires"
    Then the pace marks 2 drinks
    And drink 0 is marked at minute 480 holding 500 ml
    And drink 1 is marked at minute 690 holding 800 ml

  Scenario: A day with nothing logged marks no drinks
    Given a pace with goal 2000 ml, wake "07:00", window 780 minutes and morning share 65
    When the pace is computed at "16:00" in zone "America/Argentina/Buenos_Aires"
    Then the pace marks 0 drinks

  Scenario: A day that reaches its goal records the crossing time
    Given a goal-time day "2026-06-14" with goal 1000 ml
    And a goal-time intake of 400 ml at "2026-06-14T09:00"
    And a goal-time intake of 400 ml at "2026-06-14T14:00"
    And a goal-time intake of 400 ml at "2026-06-14T18:30"
    When the goal times are computed in zone "America/Argentina/Buenos_Aires"
    Then the goal on "2026-06-14" was reached at "18:30"
    And 1 of 1 days reached the goal

  Scenario: A day that never reaches its goal has no crossing time
    Given a goal-time day "2026-06-14" with goal 1000 ml
    And a goal-time intake of 400 ml at "2026-06-14T09:00"
    When the goal times are computed in zone "America/Argentina/Buenos_Aires"
    Then the goal on "2026-06-14" was not reached
    And 0 of 1 days reached the goal

  Scenario: The typical completion time is the median of the days that made it
    Given a goal-time day "2026-06-12" with goal 500 ml
    And a goal-time intake of 500 ml at "2026-06-12T11:00"
    And a goal-time day "2026-06-13" with goal 500 ml
    And a goal-time intake of 500 ml at "2026-06-13T17:00"
    And a goal-time day "2026-06-14" with goal 500 ml
    And a goal-time intake of 500 ml at "2026-06-14T21:00"
    When the goal times are computed in zone "America/Argentina/Buenos_Aires"
    Then the typical completion time is "17:00"
    And 3 of 3 days reached the goal
