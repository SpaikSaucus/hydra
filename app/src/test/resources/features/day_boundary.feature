Feature: Hydration day boundary
  The hydration day is the CALENDAR day: it starts at 00:00 and ends at 23:59.
  Wake and sleep times only bound the notification window — they never decide
  which day an intake belongs to.

  Background:
    Given a day-boundary repository in zone "America/Argentina/Buenos_Aires" waking at "07:00" and sleeping at "23:00"

  Scenario Outline: Every intake is filed under its own calendar day
    Given the boundary clock reads "<now>"
    When 250 ml is logged at "<now>"
    Then the logged intake day key is "<key>"

    Examples:
      | now              | key        |
      | 2026-06-14T00:05 | 2026-06-14 |
      | 2026-06-14T03:20 | 2026-06-14 |
      | 2026-06-14T06:59 | 2026-06-14 |
      | 2026-06-14T07:00 | 2026-06-14 |
      | 2026-06-14T13:00 | 2026-06-14 |
      | 2026-06-14T23:50 | 2026-06-14 |

  Scenario: The open day rolls over at midnight, not at wake time
    Given the boundary clock reads "2026-06-14T00:00"
    Then the open hydration day is "2026-06-14"

  Scenario: The open day is still today just before waking up
    Given the boundary clock reads "2026-06-14T06:30"
    Then the open hydration day is "2026-06-14"

  Scenario: Last night's water does not count toward this morning
    Given the boundary clock reads "2026-06-13T23:30"
    When 500 ml is logged at "2026-06-13T23:30"
    Then the hydration day total is 500 ml
    Given the boundary clock reads "2026-06-14T06:00"
    Then the hydration day total is 0 ml

  Scenario: Water drunk before waking up counts for the new day
    Given the boundary clock reads "2026-06-14T05:45"
    When 300 ml is logged at "2026-06-14T05:45"
    Then the logged intake day key is "2026-06-14"
    And the hydration day total is 300 ml

  Scenario: Yesterday is closed as soon as the calendar day changes
    Given the boundary clock reads "2026-06-13T22:00"
    When 400 ml is logged at "2026-06-13T22:00"
    Given the boundary clock reads "2026-06-14T02:00"
    Then the hydration day "2026-06-13" is closed
    And the hydration day "2026-06-14" is open

  Scenario: The wake time still silences reminders before waking up
    Given the boundary clock reads "2026-06-14T05:45"
    Then the boundary reminder decision at "05:45" is "NIGHT_CUTOFF"

  Scenario: The wake time still opens the reminder window after waking up
    Given the boundary clock reads "2026-06-14T07:10"
    Then the boundary reminder decision at "07:10" is "DUE"

  Scenario: The night cutoff still silences reminders before sleeping
    Given the boundary clock reads "2026-06-14T21:00"
    Then the boundary reminder decision at "21:00" is "NIGHT_CUTOFF"

  # The day snapshot has to freeze the morning/afternoon balance too. Without it,
  # the pace curve of a finished day was redrawn against the CURRENT balance, so
  # moving the slider rewrote how last Tuesday looked.
  Scenario: A day freezes the morning balance in force that day
    Given the morning balance is 50 percent
    And the boundary clock reads "2026-06-14T09:00"
    Then the hydration day "2026-06-14" was paced at 50 percent

  Scenario: Changing the balance does not rewrite a finished day
    Given the morning balance is 50 percent
    And the boundary clock reads "2026-06-13T09:00"
    Given the morning balance is 70 percent
    And the boundary clock reads "2026-06-14T09:00"
    Then the hydration day "2026-06-13" was paced at 50 percent
    And the hydration day "2026-06-14" was paced at 70 percent

  # A screen that stays subscribed has to know WHEN to re-key itself. Sleeping
  # until the zone's own next start-of-day means no polling, and no naive +24 h
  # that would drift on a DST night.
  Scenario Outline: How long a live "today" subscription may sleep
    Given a resolver clock reading "<now>" in "<zone>"
    Then the time until the next hydration day is <minutes> minutes

    Examples:
      | now              | zone                           | minutes |
      | 2026-06-13T23:50 | America/Argentina/Buenos_Aires | 10      |
      | 2026-06-13T00:00 | America/Argentina/Buenos_Aires | 1440    |
      | 2026-06-13T12:00 | America/Argentina/Buenos_Aires | 720     |
      | 2026-06-13T23:59 | Asia/Kolkata                   | 1       |
