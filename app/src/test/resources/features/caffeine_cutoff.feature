Feature: Caffeine cut-off advisory
  A compact notice on Home once caffeine would start eating into sleep. The window
  is expressed in hours BEFORE the configured bedtime, never as a fixed wall-clock
  hour, so moving the sleep time moves the notice with it.

  9 h rounds the 8.8 h that Gardiner et al. (2023) report as the point where a
  standard coffee stops measurably shortening sleep. Advisory only: it never
  touches the goal, the streaks or the reminders.

  Scenario Outline: The notice starts nine hours before bedtime
    Given a bedtime of "<sleep>"
    Then the caffeine notice starts at "<start>"

    Examples:
      | sleep | start |
      | 23:00 | 14:00 |
      | 22:00 | 13:00 |
      | 00:00 | 15:00 |
      | 01:00 | 16:00 |

  Scenario Outline: Whether the notice shows right now
    Given a bedtime of "<sleep>"
    Then the caffeine notice at "<now>" is <shown>

    Examples:
      | sleep | now   | shown |
      | 23:00 | 13:59 | no    |
      | 23:00 | 14:00 | yes   |
      | 23:00 | 18:30 | yes   |
      | 23:00 | 22:59 | yes   |
      | 23:00 | 23:00 | no    |
      | 23:00 | 08:00 | no    |

  # Someone who goes to bed at 01:00 has to be warned from 16:00 straight through
  # midnight, so the window has to survive the calendar boundary.
  Scenario Outline: The window survives a bedtime past midnight
    Given a bedtime of "<sleep>"
    Then the caffeine notice at "<now>" is <shown>

    Examples:
      | sleep | now   | shown |
      | 01:00 | 15:59 | no    |
      | 01:00 | 16:00 | yes   |
      | 01:00 | 23:30 | yes   |
      | 01:00 | 00:30 | yes   |
      | 01:00 | 01:00 | no    |
      | 01:00 | 09:00 | no    |
