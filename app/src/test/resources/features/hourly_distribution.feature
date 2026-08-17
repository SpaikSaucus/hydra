Feature: Hourly intake distribution
  The "when you drink" chart buckets every logged intake of a period by the hour
  of the local day, highlights the busiest block of hours and reports the
  morning/afternoon split actually achieved.

  Background:
    Given a distribution in zone "America/Argentina/Buenos_Aires" waking at "07:00" with a 780 minute window

  Scenario: Intakes are bucketed by the local hour
    Given an intake of 300 ml at "2026-06-14T08:15"
    And an intake of 200 ml at "2026-06-14T08:45"
    And an intake of 500 ml at "2026-06-14T14:00"
    When the distribution is computed
    Then hour 8 holds 500 ml
    And hour 14 holds 500 ml
    And hour 9 holds 0 ml
    And the distribution total is 1000 ml
    And the distribution has 24 buckets

  Scenario: The peak window is the busiest block of consecutive hours
    Given an intake of 100 ml at "2026-06-14T07:30"
    And an intake of 600 ml at "2026-06-14T10:10"
    And an intake of 500 ml at "2026-06-14T11:20"
    And an intake of 300 ml at "2026-06-14T12:40"
    And an intake of 100 ml at "2026-06-14T18:00"
    When the distribution is computed
    Then the peak window starts at hour 10
    And the peak window share is 88 percent

  Scenario: The observed morning share is measured against the window midpoint
    Given an intake of 650 ml at "2026-06-14T09:00"
    And an intake of 350 ml at "2026-06-14T16:00"
    When the distribution is computed
    Then the observed morning share is 65 percent
    And the observed afternoon share is 35 percent

  Scenario: Water drunk past the window midpoint counts as afternoon
    Given an intake of 400 ml at "2026-06-14T13:29"
    And an intake of 600 ml at "2026-06-14T13:31"
    When the distribution is computed
    Then the observed morning share is 40 percent

  Scenario: Intakes across several days accumulate into the same hours
    Given an intake of 250 ml at "2026-06-12T09:00"
    And an intake of 250 ml at "2026-06-13T09:00"
    And an intake of 250 ml at "2026-06-14T09:00"
    When the distribution is computed
    Then hour 9 holds 750 ml
    And the distribution total is 750 ml

  Scenario: An empty period yields an empty distribution
    When the distribution is computed
    Then the distribution is empty
    And the distribution has 24 buckets
