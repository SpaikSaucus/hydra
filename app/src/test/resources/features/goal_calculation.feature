Feature: Daily water goal calculation
  The daily goal is factor (ml/kg) x weight (kg), with heat mode forcing factor 40
  and an optional ±20% manual adjustment. Inputs are clamped to valid ranges.

  Scenario: Base goal for 77 kg at factor 33
    Given a weight of 77.0 kg
    And a factor of 33 ml per kg
    When the goal is calculated
    Then the goal is 2541 ml
    And the effective factor is 33

  Scenario: Heat mode forces factor 40
    Given a weight of 77.0 kg
    And a factor of 33 ml per kg
    And heat mode is on
    When the goal is calculated
    Then the goal is 3080 ml
    And the effective factor is 40

  Scenario Outline: Manual adjustment changes the goal
    Given a weight of 77.0 kg
    And a factor of 33 ml per kg
    And a manual adjustment of <pct> percent
    When the goal is calculated
    Then the goal is <goal> ml

    Examples:
      | pct | goal |
      | 15  | 2922 |
      | -15 | 2160 |
      | 20  | 3049 |
      | -20 | 2033 |

  Scenario: Out-of-range inputs are clamped and warned
    Given a weight of 300.0 kg
    And a factor of 50 ml per kg
    And a manual adjustment of 40 percent
    When the goal is calculated
    Then the goal is 12000 ml
    And the goal warnings include "WEIGHT_CLAMPED"
    And the goal warnings include "FACTOR_CLAMPED"
    And the goal warnings include "ADJUSTMENT_CLAMPED"
