package com.github.bgalek.movetoversioncatalog

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GradleDslTest {

    @Test
    fun `accepts any letters-only configuration name`() {
        assertTrue(GradleDsl.isDependencyConfigurationName("implementation"))
        assertTrue(GradleDsl.isDependencyConfigurationName("testImplementation"))
        assertTrue(GradleDsl.isDependencyConfigurationName("customFeatureApi"))
        assertTrue(GradleDsl.isDependencyConfigurationName("classpath"))
    }

    @Test
    fun `rejects names with digits or symbols`() {
        assertFalse(GradleDsl.isDependencyConfigurationName("impl2"))
        assertFalse(GradleDsl.isDependencyConfigurationName("my-config"))
        assertFalse(GradleDsl.isDependencyConfigurationName(""))
        assertFalse(GradleDsl.isDependencyConfigurationName("foo_bar"))
    }
}
