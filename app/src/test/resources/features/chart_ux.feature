Feature: Chart interaction and layout
  The bits of the history charts that are easy to get subtly wrong: the range
  picker's state machine, how a period resolves to real days, and whether the
  heat-map squares actually fill the card.

  # ---------------------------------------------------------------- selection

  Scenario: The first tap only sets the start of the range
    Given an empty chart selection
    When the chart bar "2026-07-20" is tapped
    Then the chart anchor is "2026-07-20"
    And the chart selection has no range

  Scenario: The second tap closes the range and switches to it
    Given an empty chart selection
    When the chart bar "2026-07-20" is tapped
    And the chart bar "2026-07-24" is tapped
    Then the chart range is "2026-07-20" to "2026-07-24"
    And the chart period is "SELECTION"
    And the chart has no anchor

  Scenario: A range picked backwards is normalised
    Given an empty chart selection
    When the chart bar "2026-07-24" is tapped
    And the chart bar "2026-07-20" is tapped
    Then the chart range is "2026-07-20" to "2026-07-24"

  Scenario: Starting a new pick never leaves the period on a range that is gone
    Given a chart selection of "2026-07-20" to "2026-07-24"
    When the chart bar "2026-07-26" is tapped
    Then the chart selection has no range
    And the chart period is "MONTH"
    And the chart anchor is "2026-07-26"

  Scenario: Clearing the selection returns to the 30-day period
    Given a chart selection of "2026-07-20" to "2026-07-24"
    When the chart selection is cleared
    Then the chart selection has no range
    And the chart period is "MONTH"

  Scenario: Dragging picks the whole span in one gesture
    Given an empty chart selection
    When the chart is dragged from "2026-07-22" to "2026-07-27"
    Then the chart range is "2026-07-22" to "2026-07-27"
    And the chart period is "SELECTION"
    And the chart has no anchor

  Scenario: The selection period cannot be chosen without a range
    Given an empty chart selection
    When the chart period "SELECTION" is chosen
    Then the chart period is "MONTH"

  # Changing the period is a decision to stop looking at the pick, so the pick has
  # to GO — leaving it stored kept the "Selection" chip lit and could still paint
  # the band, which reads as "I changed the period but the old range is stuck".
  Scenario: Leaving the selection period drops the range for good
    Given a chart selection of "2026-07-20" to "2026-07-24"
    When the chart period "MONTH" is chosen
    Then the chart selection has no range
    And the chart period is "MONTH"

  Scenario: A dropped range cannot be picked up again without choosing it on the chart
    Given a chart selection of "2026-07-20" to "2026-07-24"
    When the chart period "WEEK" is chosen
    And the chart period "SELECTION" is chosen
    Then the chart period is "WEEK"
    And the chart selection has no range

  # ---------------------------------------------------------------- cancelling

  Scenario: Cancelling a pick in progress returns to the plain period
    Given an empty chart selection
    When the chart bar "2026-07-20" is tapped
    And the chart pick is cancelled
    Then the chart has no anchor
    And the chart selection has no range
    And the chart period is "MONTH"

  Scenario: Cancelling from the 7-day period stays on 7 days
    Given an empty chart selection
    When the chart period "WEEK" is chosen
    And the chart bar "2026-07-20" is tapped
    And the chart pick is cancelled
    Then the chart period is "WEEK"
    And the chart has no anchor

  Scenario: Cancelling a pick started over a range does not bring that range back
    Given a chart selection of "2026-07-20" to "2026-07-24"
    When the chart bar "2026-07-26" is tapped
    And the chart pick is cancelled
    Then the chart has no anchor
    And the chart selection has no range
    And the chart period is "MONTH"

  # ------------------------------------------------------------------ window

  Scenario: The 7-day period resolves to the last seven days
    Given an empty chart selection
    When the chart period "WEEK" is chosen
    Then the chart window on "2026-07-28" is "2026-07-22" to "2026-07-28"

  Scenario: The 30-day period resolves to the last thirty days
    Given an empty chart selection
    Then the chart window on "2026-07-28" is "2026-06-29" to "2026-07-28"

  Scenario: The selection period resolves to the picked range
    Given a chart selection of "2026-07-20" to "2026-07-24"
    Then the chart window on "2026-07-28" is "2026-07-20" to "2026-07-24"

  Scenario: The 90-day period resolves to the last ninety days
    Given an empty chart selection
    When the chart period "QUARTER" is chosen
    Then the chart window on "2026-07-28" is "2026-04-30" to "2026-07-28"

  # ------------------------------------------------------------- chart slots
  # The bar chart follows the period instead of being pinned to 30 days, but the
  # rule that survives is "one slot per CALENDAR day" — a day with no record is
  # an empty track, never a bar squeezed in beside its neighbours.

  Scenario: The bar chart draws one slot per calendar day of the period
    Given an empty chart selection
    When the chart period "WEEK" is chosen
    Then the chart draws 7 day slots on "2026-07-28"
    And the first day slot is "2026-07-22"
    And the last day slot is "2026-07-28"

  Scenario: The 90-day period draws ninety slots
    Given an empty chart selection
    When the chart period "QUARTER" is chosen
    Then the chart draws 90 day slots on "2026-07-28"
    And the first day slot is "2026-04-30"
    And the last day slot is "2026-07-28"

  Scenario: Picking a range zooms the bar chart into it
    Given a chart selection of "2026-07-20" to "2026-07-24"
    Then the chart draws 5 day slots on "2026-07-28"
    And the first day slot is "2026-07-20"
    And the last day slot is "2026-07-24"

  # ----------------------------------------------------------------- heatmap

  Scenario: The heat-map squares fill the width of the card
    Given a heat-map 296 dp wide with 12 columns
    Then the heat-map cell side is 22 dp
    And the heat-map grid width is 296 dp
    And the heat-map grid height is 171 dp

  Scenario: The heat-map squares also fill a narrow card
    Given a heat-map 200 dp wide with 12 columns
    Then the heat-map cell side is 14 dp
    And the heat-map grid width is 200 dp
    And the heat-map grid height is 115 dp

  # The dark card is near-black, so an alpha-stacked ramp composited every low step
  # down to almost the card colour: a week with no water looked exactly like a week
  # that missed the goal. The ramp has to be built RELATIVE to the card so it moves
  # away from it on either theme — which is also why the legend can no longer claim
  # a direction ("darker") that is only true in the light theme.
  Scenario Outline: The heat-map ramp reads on either theme
    Given the heat-map palette on the "<theme>" card
    Then the empty square is distinguishable from the card
    And each heat-map step is distinguishable from the previous one
    And the ramp moves further from the card colour at every step

    Examples:
      | theme |
      | dark  |
      | light |

  Scenario Outline: Which heat-map step a day falls in
    Then a day at <pct> percent of the goal is heat-map step <step>

    Examples:
      | pct | step |
      | 0   | 1    |
      | 49  | 1    |
      | 50  | 2    |
      | 74  | 2    |
      | 75  | 3    |
      | 99  | 3    |
      | 100 | 4    |
      | 140 | 4    |

  Scenario: A day with no record is the empty heat-map step
    Then a day with no record is heat-map step 0

  # Same failure as the heat-map's, mirrored: the pace chart shades the drinking
  # window with surfaceVariant at 30%, which on the LIGHT card is almost the card
  # itself. Blending away from the card is what makes a fill survive both themes.
  Scenario Outline: The pace window band is visible on either theme
    Given the pace window band on the "<theme>" card
    Then the band is distinguishable from the card

    Examples:
      | theme |
      | light |
      | dark  |
