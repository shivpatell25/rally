package com.shiv.rally.presentation.player

import com.shiv.rally.domain.model.StreamCandidate
import com.shiv.rally.domain.model.StreamQualityInfo
import com.shiv.rally.domain.model.StreamSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRecoveryTest {

    @Test
    fun recoverySkipsUnverifiedIptvEvenWhenItRanksFirst() {
        val unverified = candidate("iptv-wrong", exact = false, kind = StreamSourceKind.IPTV)
        val verified = candidate("stremio-right", exact = true, kind = StreamSourceKind.STREMIO)

        assertEquals(verified, chooseRecoveryCandidate(listOf(unverified, verified), emptySet()))
    }

    @Test
    fun recoverySkipsFailedAndPreflightRejectedSources() {
        val failed = candidate("https%3A%2F%2Fexample.test%2Ffailed.m3u8", exact = true)
        val rejected = candidate("rejected", exact = true, preflightPassed = false)

        val selected = chooseRecoveryCandidate(
            listOf(failed, rejected),
            setOf("https://example.test/failed.m3u8")
        )

        assertNull(selected)
    }

    private fun candidate(
        target: String,
        exact: Boolean,
        kind: StreamSourceKind = StreamSourceKind.STREMIO,
        preflightPassed: Boolean? = true
    ) = StreamCandidate(
        id = "$kind:$target",
        playbackTarget = target,
        title = target,
        sourceKind = kind,
        quality = StreamQualityInfo(resolution = "1080p"),
        qualityRank = 500,
        exactGameMatch = exact,
        matchConfidence = if (exact) .99f else .4f,
        matchEvidence = if (exact) "Exact event match" else "Unverified channel",
        preflightPassed = preflightPassed
    )
}
