Feature: Morning/afternoon intake balance
  The daily goal is paced piecewise: the first half of the wake->cutoff window
  targets morningSharePct (default 65%) of the goal and the second half the
  complement (35%), to front-load intake and protect night sleep. The share is
  clamped to 45..70. Defaults: weight 70 kg -> goal 2310 ml, wake 07:00, sleep
  23:00, cutoff at 20:00 -> 780-min window, halfway point at 13:30 where the
  accumulated target is 65% = 1501 ml.

  Scenario: On pace against the 65% morning target stays silent
    Given a balance config with morning share 65
    When the balance is evaluated with 1360 ml consumed and last intake 10 minutes ago at "13:30"
    Then the balance decision posts no reminder

  Scenario: Old linear pace now counts as behind in the morning half
    Given a balance config with morning share 65
    When the balance is evaluated with 1200 ml consumed and last intake 10 minutes ago at "13:30"
    Then the balance decision posts a reminder
    And the balance decision reason is "BEHIND"

  Scenario: A 50 percent share reproduces the even linear pace
    Given a balance config with morning share 50
    When the balance is evaluated with 1010 ml consumed and last intake 10 minutes ago at "13:30"
    Then the balance decision posts no reminder

  Scenario: The afternoon segment eases off but still flags real lag
    Given a balance config with morning share 65
    When the balance is evaluated with 1700 ml consumed and last intake 10 minutes ago at "17:00"
    Then the balance decision posts a reminder
    And the balance decision reason is "BEHIND"

  Scenario: On pace in the afternoon segment stays silent
    Given a balance config with morning share 65
    When the balance is evaluated with 1800 ml consumed and last intake 10 minutes ago at "17:00"
    Then the balance decision posts no reminder

  Scenario: Shares below the safe band are clamped up to 45
    Given a balance config with morning share 10
    When the balance is evaluated with 500 ml consumed and last intake 10 minutes ago at "13:30"
    Then the balance decision posts a reminder
    And the balance decision reason is "BEHIND"

  Scenario: Shares above the safe band are clamped down to 70
    Given a balance config with morning share 95
    When the balance is evaluated with 1500 ml consumed and last intake 10 minutes ago at "13:30"
    Then the balance decision posts no reminder
