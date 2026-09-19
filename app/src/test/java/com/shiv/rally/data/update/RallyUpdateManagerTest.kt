package com.shiv.rally.data.update

import org.junit.Assert.assertTrue
import org.junit.Test

class RallyUpdateManagerTest {
    @Test
    fun `stable and newer beta versions compare correctly`() {
        assertTrue(compareVersions("v1.0-beta2", "1.0-beta1") > 0)
        assertTrue(compareVersions("1.0-rc1", "1.0-beta9") > 0)
        assertTrue(compareVersions("1.0", "1.0-rc9") > 0)
        assertTrue(compareVersions("2.0-beta1", "1.9") > 0)
    }
}
