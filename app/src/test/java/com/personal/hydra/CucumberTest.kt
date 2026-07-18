package com.personal.hydra

import io.cucumber.junit.Cucumber
import io.cucumber.junit.CucumberOptions
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
    features = ["classpath:features"],
    glue = ["com.personal.hydra.steps"],
    plugin = ["pretty"],
)
class CucumberTest
