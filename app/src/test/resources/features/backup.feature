Feature: JSON backup integrity
  Exported data round-trips through JSON without loss.

  Scenario: A backup round-trips through JSON without loss
    Given a backup with 1 day and 2 intakes totalling 750 ml
    When the backup is encoded and decoded as JSON
    Then the decoded backup has 1 day and 2 intakes
    And the decoded day total is 750 ml
