Feature: Unit conversion
  Internally everything is canonical ml and kg; imperial is presentation only.

  Scenario: Weight round-trips through pounds
    Given a weight of 77.0 kg
    Then converting to pounds and back is 77.0 kg

  Scenario: Volume round-trips through fluid ounces
    Given a volume of 250 ml
    Then converting to fluid ounces and back is 250 ml

  Scenario: Imperial display of a 500 ml volume
    Given a volume of 500 ml
    Then the imperial display is 17 fl oz

  Scenario: Litres for a large volume
    Given a volume of 2541 ml
    Then the value in litres is 2.54
