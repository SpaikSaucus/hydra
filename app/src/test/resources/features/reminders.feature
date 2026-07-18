Feature: Reminder decision
  Given the config, what has been consumed and the time, decide whether to remind.
  Defaults: weight 70 kg, factor 33 (goal 2310 ml), wake 07:00, sleep 23:00,
  night cutoff 180 min (cutoff 20:00), reminder interval 90 min.

  Scenario: Reminds inside the window with no recent intake
    Given the default reminder config
    When evaluated with 0 ml consumed and no recent intake at "10:00"
    Then a reminder is posted
    And the reminder goal is 2310 ml

  Scenario: Silent after the night cutoff
    Given the default reminder config
    When evaluated with 0 ml consumed and no recent intake at "21:00"
    Then no reminder is posted
    And the reminder reason is "NIGHT_CUTOFF"

  Scenario: Silent when the goal is reached
    Given the default reminder config
    When evaluated with 2310 ml consumed and last intake 0 minutes ago at "12:00"
    Then no reminder is posted
    And the reminder reason is "ALREADY_DONE"

  Scenario: Not due when on track and recently drank
    Given the default reminder config
    When evaluated with 600 ml consumed and last intake 10 minutes ago at "10:00"
    Then no reminder is posted

  Scenario: Exactly at the night cutoff is already outside the window
    Given the default reminder config
    When evaluated with 0 ml consumed and no recent intake at "20:00"
    Then no reminder is posted
    And the reminder reason is "NIGHT_CUTOFF"

  Scenario: One minute before the night cutoff is still inside the window
    Given the default reminder config
    When evaluated with 0 ml consumed and no recent intake at "19:59"
    Then a reminder is posted
