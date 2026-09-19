package com.shiv.rally.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortalUrlNormalizerTest {
    @Test
    fun `portal normalizer preserves real ports and removes endpoint suffixes`() {
        assertEquals(
            "http://example.com:8080/c",
            PortalUrlNormalizer.normalizePortal("example.com:8080/c/server/load.php")
        )
    }

    @Test
    fun `portal normalizer repairs common remote colon typo`() {
        assertEquals(
            "http://tv.stream4k.cc/c",
            PortalUrlNormalizer.normalizePortal("tv:stream4k.cc/c")
        )
    }

    @Test
    fun `addon normalizer only accepts valid web urls`() {
        assertEquals(
            "https://addon.example.com/manifest.json",
            PortalUrlNormalizer.normalizeAddon("addon.example.com")
        )
        assertEquals(
            "https://sports.example.to/manifest.json",
            PortalUrlNormalizer.normalizeAddon("https://sports.example.to")
        )
        assertNull(PortalUrlNormalizer.normalizeAddon("not a url"))
    }

    @Test
    fun `xtream normalizer preserves domain suffix ports and endpoint path`() {
        assertEquals(
            "https://provider.example.to:8080",
            PortalUrlNormalizer.normalizeXtreamServer("https://provider.example.to:8080/player_api.php")
        )
    }
}
