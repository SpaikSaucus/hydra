Feature: Offline season inference
  Season is inferred from the device country (hemisphere) and the date. Real
  weather is never known offline. Summer suggests heat mode.

  Scenario: Argentina in June is winter (southern hemisphere)
    Given the country is "AR"
    And the date is "2026-06-14"
    When the season is inferred
    Then the inferred hemisphere is "SOUTH"
    And the inferred season is "WINTER"
    And heat mode is not suggested

  Scenario: Argentina in January is summer and warns when heat is off
    Given the country is "AR"
    And the date is "2026-01-15"
    When the season is inferred
    Then the inferred season is "SUMMER"
    And heat mode is suggested
    And turning heat off gives warning "HEAT_MODE_DISABLED_IN_SUMMER"

  Scenario: United States in June is summer (northern hemisphere)
    Given the country is "US"
    And the date is "2026-06-14"
    When the season is inferred
    Then the inferred hemisphere is "NORTH"
    And the inferred season is "SUMMER"

  Scenario: Heat on during winter is an informational warning
    Given the season is "WINTER"
    Then turning heat on gives warning "HEAT_MODE_ENABLED_IN_WINTER"

  Scenario: Unknown country defaults to the northern hemisphere
    Given the country is "XX"
    And the date is "2026-06-14"
    When the season is inferred
    Then the inferred hemisphere is "NORTH"
