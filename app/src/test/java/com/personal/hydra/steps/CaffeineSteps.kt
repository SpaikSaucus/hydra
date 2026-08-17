package com.personal.hydra.steps

import com.personal.hydra.domain.CaffeineCutoff
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.Assert.assertEquals
import java.time.LocalTime

/** Steps for the caffeine cut-off advisory. Pure clock arithmetic, no Android. */
class CaffeineSteps {

    private var sleepMinute = 0

    @Given("a bedtime of {string}")
    fun bedtime(time: String) {
        sleepMinute = minuteOf(time)
    }

    @Then("the caffeine notice starts at {string}")
    fun noticeStarts(time: String) =
        assertEquals(minuteOf(time), CaffeineCutoff.warningStartMinute(sleepMinute))

    @Then("the caffeine notice at {string} is {word}")
    fun noticeAt(time: String, shown: String) = assertEquals(
        shown == "yes",
        CaffeineCutoff.shouldWarn(minuteOf(time), sleepMinute),
    )

    private fun minuteOf(time: String): Int = LocalTime.parse(time).let { it.hour * 60 + it.minute }
}
