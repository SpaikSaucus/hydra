Feature: Intake schedule and dynamic redistribution
  The plan spreads the goal from wake time to the night cutoff, never exceeding
  the hourly cap, summing exactly to the goal; if the user falls behind it
  redistributes the remainder over the time left.

  Scenario: Full goal distributed across a 13-hour window
    Given a schedule with wake "08:00" and sleep "00:00" and cutoff 180 minutes
    And a goal of 2541 ml with hourly cap 1000 ml
    When the schedule is generated
    Then the schedule total is 2541 ml
    And no intake exceeds 1000 ml
    And there are 13 intakes
    And there is no "GOAL_DOES_NOT_FIT_WINDOW" warning

  Scenario: Goal does not fit a short window
    Given a schedule with wake "19:00" and sleep "22:00" and cutoff 60 minutes
    And a goal of 2541 ml with hourly cap 1000 ml
    When the schedule is generated
    Then the schedule total is at most 2000 ml
    And there is a "GOAL_DOES_NOT_FIT_WINDOW" warning

  Scenario: Behind at 15:00 redistributes the remainder
    Given a schedule with wake "08:00" and sleep "00:00" and cutoff 180 minutes
    And a goal of 2541 ml with hourly cap 1000 ml
    When 600 ml have been consumed by "15:00" and the plan is redistributed
    Then the redistributed total is 1941 ml
    And no redistributed intake exceeds 1000 ml

  Scenario: Goal already met yields an empty plan
    Given a schedule with wake "08:00" and sleep "00:00" and cutoff 180 minutes
    And a goal of 2541 ml with hourly cap 1000 ml
    When 2541 ml have been consumed by "15:00" and the plan is redistributed
    Then the redistributed plan is empty
