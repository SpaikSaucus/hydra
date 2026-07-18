Feature: Hydration repository (in-memory integration)
  Logging updates the cached daily total; changing the profile updates today's
  open-day goal; deleting an entry reverts the total.

  Background:
    Given a fresh repository with profile weight 77 kg, factor 33, advanced mode

  Scenario: Logging accumulates the daily total
    When I ensure today is open
    And I log 250 ml
    And I log 500 ml
    Then today's total is 750 ml

  Scenario: Changing weight refreshes today's open-day goal
    When I ensure today is open
    Then today's goal is 2541 ml
    When the weight changes to 100 kg
    And I ensure today is open
    Then today's goal is 3300 ml

  Scenario: Deleting an entry reverts the total
    When I ensure today is open
    And I log 300 ml
    And I delete the last entry
    Then today's total is 0 ml
