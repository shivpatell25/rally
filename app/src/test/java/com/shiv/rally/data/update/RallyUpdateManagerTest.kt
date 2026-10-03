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
    @Test
    fun acceptsVerifiedRotationAndRejectsUnrelatedInstalledKey() {
        assertTrue(isSigningUpdateCompatible(setOf("old"), setOf("production"), setOf("old", "production")))
        assertTrue(isSigningUpdateCompatible(setOf("production"), setOf("production"), setOf("production")))
        assertTrue(!isSigningUpdateCompatible(setOf("other"), setOf("production"), setOf("old", "production")))
        assertTrue(!isSigningUpdateCompatible(setOf("old", "other"), setOf("production"), setOf("old", "other", "production")))
        assertTrue(!isSigningUpdateCompatible(emptySet(), setOf("production"), setOf("production")))
    }

    @Test
    fun selectsOnlyTheProductionArtifactForThePlatform() {
        assertTrue(isReleaseApkForDevice("rally-v1.0-beta12-android-tv.apk", 34))
        assertTrue(!isReleaseApkForDevice("rally-v1.0-beta12-android-tv-legacy.apk", 34))
        assertTrue(isReleaseApkForDevice("rally-v1.0-beta12-android-tv-legacy.apk", 26))
        assertTrue(!isReleaseApkForDevice("app-debug.apk", 34))
        assertTrue(!isReleaseApkForDevice("app-release-unsigned.apk", 34))
    }
}
