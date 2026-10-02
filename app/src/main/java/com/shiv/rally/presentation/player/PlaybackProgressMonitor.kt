package com.shiv.rally.presentation.player

/** Uses wall time so buffering and a frozen video decoder cannot wait indefinitely. */
internal class PlaybackProgressMonitor(startedAtMs: Long) {
    private var lastProgressAtMs = startedAtMs
    private var lastPositionMs = 0L
    private var lastRenderedFrames = 0
    private var hasProgress = false

    fun check(
        nowMs: Long,
        wantsPlayback: Boolean,
        buffering: Boolean,
        ready: Boolean,
        positionMs: Long,
        renderedFrames: Int?
    ): PlaybackStallReason? {
        val progressed = if (renderedFrames != null) {
            renderedFrames != lastRenderedFrames && renderedFrames > 0
        } else positionMs != lastPositionMs
        lastPositionMs = positionMs
        lastRenderedFrames = renderedFrames ?: 0
        if (!wantsPlayback || (!ready && !buffering)) {
            lastProgressAtMs = nowMs
            return null
        }
        if (ready && progressed) {
            hasProgress = true
            lastProgressAtMs = nowMs
            return null
        }
        val deadlineMs = if (hasProgress) 15_000 else 25_000
        if (nowMs - lastProgressAtMs < deadlineMs) return null
        return when {
            !hasProgress -> PlaybackStallReason.STARTUP
            buffering -> PlaybackStallReason.BUFFERING
            else -> PlaybackStallReason.VIDEO
        }
    }
}

internal enum class PlaybackStallReason(val message: String) {
    STARTUP("This source did not start. Choose another source or retry."),
    BUFFERING("This source stopped delivering video. Choose another source or retry."),
    VIDEO("The video stopped updating. Choose another source or retry.")
}
