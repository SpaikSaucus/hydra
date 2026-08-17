Feature: JSON backup integrity
  Exported data round-trips through JSON without loss, and every export or import
  ends in a message the user can read.

  Scenario: A backup round-trips through JSON without loss
    Given a backup with 1 day and 2 intakes totalling 750 ml
    When the backup is encoded and decoded as JSON
    Then the decoded backup has 1 day and 2 intakes
    And the decoded day total is 750 ml

  # ------------------------------------------------- export/import feedback
  # The settings screen used to drop the Result and had no snackbar host, so a
  # silent success and a silent failure looked exactly the same: nothing.

  Scenario: A finished export reports what it wrote
    Given an "EXPORT" that handled 84 days and 512 entries
    When the backup outcome is resolved
    Then the outcome is a finished "EXPORT" of 84 days and 512 entries

  Scenario: A finished import reports what it restored
    Given an "IMPORT" that handled 84 days and 512 entries
    When the backup outcome is resolved
    Then the outcome is a finished "IMPORT" of 84 days and 512 entries

  Scenario Outline: A failed backup reports why it failed
    Given an "<op>" that failed with "<failure>"
    When the backup outcome is resolved
    Then the outcome is a failed "<op>" with code "<code>"

    Examples:
      | op     | failure                | code             |
      | EXPORT | unwritable destination | OPEN_DESTINATION |
      | IMPORT | unreadable source      | OPEN_SOURCE      |
      | IMPORT | newer version          | NEWER_SCHEMA     |
      | IMPORT | malformed json         | MALFORMED        |
      | IMPORT | unexpected crash       | UNKNOWN          |
