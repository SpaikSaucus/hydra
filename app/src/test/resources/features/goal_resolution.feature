Feature: Simple vs advanced goal resolution
  In SIMPLE mode the goal is the pure formula (factor 33, no manual adjustment)
  and heat mode is automatic from the inferred season. In ADVANCED mode the
  user's raw factor, heat toggle and ±% adjustment are used.

  Scenario: Simple mode ignores stored factor and adjustment
    Given a config with weight 77.0 kg, factor 38, adjustment 12 percent in simple mode
    And the country is "AR" with date "2026-06-14"
    When the effective goal is resolved
    Then the resolved goal is 2541 ml

  Scenario: Simple mode applies heat automatically in summer
    Given a config with weight 77.0 kg, factor 33, adjustment 0 percent in simple mode
    And the country is "AR" with date "2026-01-15"
    When the effective goal is resolved
    Then the resolved goal is 3080 ml

  Scenario: Advanced mode honours stored factor and adjustment
    Given a config with weight 77.0 kg, factor 38, adjustment 12 percent in advanced mode
    And the country is "AR" with date "2026-06-14"
    When the effective goal is resolved
    Then the resolved goal is 3277 ml
