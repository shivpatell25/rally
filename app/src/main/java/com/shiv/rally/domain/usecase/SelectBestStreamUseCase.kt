package com.shiv.rally.domain.usecase

import com.shiv.rally.domain.model.ChannelGuide
import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.StreamCandidate
import com.shiv.rally.domain.model.StreamQualityInfo
import com.shiv.rally.domain.model.StreamSelection
import com.shiv.rally.domain.model.StreamSourceKind
import com.shiv.rally.domain.model.StremioStreamOption
import com.shiv.rally.domain.model.parseQualityFromChannelName
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.domain.repository.StremioRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class SelectBestStreamUseCase @Inject constructor(
    private val iptvRepository: IptvRepository,
    private val stremioRepository: StremioRepository,
    private val matcherService: MatcherService,
    private val preferencesManager: com.shiv.rally.data.local.PreferencesManager? = null,
    private val preflightProbe: com.shiv.rally.data.remote.network.StreamPreflightProbe? = null,
    private val diagnostics: com.shiv.rally.data.local.RallyDiagnostics? = null
) {
    private val guideSemaphore = Semaphore(4)

    suspend operator fun invoke(
        event: SportEvent,
        channels: List<IptvChannel>? = null,
        knownStremioStreams: List<StremioStreamOption>? = null
    ): StreamSelection = coroutineScope {
        val allChannelsDeferred = async {
            channels ?: runCatching { iptvRepository.getChannels() }.getOrDefault(emptyList())
        }
        val stremioDeferred = async {
            knownStremioStreams ?: runCatching { stremioRepository.getStreamsForEvent(event) }.getOrDefault(emptyList())
        }
        val allChannels = allChannelsDeferred.await()
        val relevant = runCatching { matcherService.getRelevantChannelsForEvent(event, allChannels) }
            .getOrDefault(emptyList())
        val stremio = stremioDeferred.await().distinctBy { it.streamUrl }

        val guideByChannel = relevant.take(16).map { relevantChannel ->
            async {
                val guide = guideSemaphore.withPermit {
                    runCatching { iptvRepository.getChannelGuide(relevantChannel.channel.id) }.getOrNull()
                }
                relevantChannel.channel.id to guide
            }
        }.awaitAll().toMap()

        // Some add-ons expose an HTML watch page through externalUrl. Keep those
        // visible in the source picker, but never hand them to ExoPlayer or let
        // them outrank a playable broadcast.
        val rawStremioCandidates = stremio
            .filter { it.isDirectPlayable }
            .map { stream -> stream.toCandidate() }
        val preflightById = if (preflightProbe != null) {
            rawStremioCandidates.sortedByDescending { it.qualityRank }.take(5).map { candidate ->
                async {
                    val result = withTimeoutOrNull(3_200L) { preflightProbe.probe(candidate) }
                    candidate.id to result
                }
            }.awaitAll().toMap()
        } else emptyMap()
        val stremioCandidates = rawStremioCandidates.map { candidate ->
            val result = preflightById[candidate.id]
            candidate.copy(
                preflightPassed = result?.passed,
                preflightLatencyMs = result?.latencyMs,
                preflightContentType = result?.contentType
            )
        }
        val iptvCandidates = relevant.map { match ->
            val guide = guideByChannel[match.channel.id]
            val channelWithGuide = match.channel.copy(guide = guide)
            val guideTitle = guide?.now?.title.orEmpty()
            val exactFromGuide = guideTitle.isNotBlank() && textMatchesEvent(guideTitle, event)
            val exactFromName = textMatchesEvent(match.channel.name, event)
            val isRedZone = match.channel.name.contains("redzone", true) || match.channel.name.contains("red zone", true)
            val exact = !isRedZone && (exactFromGuide || (exactFromName && match.likelihoodScore >= 90f))
            val qualityText = listOf(match.channel.name, guideTitle).joinToString(" ")
            val quality = parseQualityFromChannelName(qualityText)
            StreamCandidate(
                id = "iptv:${match.channel.id}",
                playbackTarget = match.channel.id,
                title = match.channel.name,
                sourceKind = StreamSourceKind.IPTV,
                quality = quality,
                qualityRank = qualityRank(quality),
                exactGameMatch = exact,
                matchConfidence = (match.likelihoodScore / 100f).coerceIn(0f, 1f),
                matchEvidence = when {
                    exactFromGuide -> "Now playing: $guideTitle"
                    exactFromName -> "Dedicated matchup channel"
                    guideTitle.isNotBlank() -> "Now playing: $guideTitle"
                    else -> match.matchBadge ?: "Unverified channel"
                },
                channel = channelWithGuide
            )
        }

        val candidates = (stremioCandidates + iptvCandidates).sortedWith(
            compareByDescending<StreamCandidate> { it.exactGameMatch }
                .thenByDescending { it.preflightPassed != false }
                .thenByDescending { it.qualityRank + (preferencesManager?.streamHealth(it.playbackTarget)?.score ?: 0) }
                .thenByDescending { it.sourceKind == StreamSourceKind.STREMIO }
                .thenByDescending { it.matchConfidence }
                .thenBy { it.title }
        )
        val primary = candidates.firstOrNull { it.exactGameMatch && it.preflightPassed != false }
        diagnostics?.record(
            kind = "Stream selection",
            message = if (primary == null) {
                "No verified source selected for ${event.name}"
            } else {
                "Selected ${primary.sourceKind.name.lowercase()} source for ${event.name}"
            },
            detail = buildStreamSelectionTrace(candidates, primary?.id)
        )
        StreamSelection(
            primary = primary,
            candidates = candidates,
            relevantChannels = relevant.map { item ->
                val guide = guideByChannel[item.channel.id]
                item.copy(channel = item.channel.copy(guide = guide))
            },
            stremioStreams = stremio
        )
    }
}

internal fun buildStreamSelectionTrace(candidates: List<StreamCandidate>, selectedId: String? = null): String =
    if (candidates.isEmpty()) {
        "No direct-playable candidates were returned."
    } else {
        candidates.take(8).mapIndexed { index, candidate ->
            val quality = buildList {
                candidate.quality.resolution?.let(::add)
                candidate.quality.fps?.let(::add)
                if (candidate.quality.isHdr) add("HDR")
            }.joinToString(" · ").ifBlank { "quality unknown" }
            val eligibility = when {
                !candidate.exactGameMatch -> "rejected: game not verified"
                candidate.preflightPassed == false -> "rejected: preflight failed"
                candidate.id == selectedId -> "selected"
                else -> "fallback"
            }
            "${index + 1}. ${candidate.sourceKind.name.lowercase()} · $quality · $eligibility · ${candidate.matchEvidence}"
        }.joinToString("\n")
    }

internal fun textMatchesEvent(text: String, event: SportEvent): Boolean {
    if (text.isBlank()) return false
    val normalized = normalizeMatchText(text)
    val home = event.homeTeam ?: return false
    val away = event.awayTeam ?: return false
    return teamMatchesText(normalized, home.name, home.abbreviation) &&
        teamMatchesText(normalized, away.name, away.abbreviation)
}

private fun teamMatchesText(normalizedText: String, teamName: String, abbreviation: String): Boolean {
    val normalizedName = normalizeMatchText(teamName)
    val nickname = normalizedName.substringAfterLast(' ')
    val city = normalizedName.substringBeforeLast(' ', "")
    val abbr = normalizeMatchText(abbreviation)
    return normalizedName.length > 3 && normalizedText.contains(normalizedName) ||
        nickname.length > 3 && containsWord(normalizedText, nickname) ||
        city.length > 4 && normalizedText.contains(city) ||
        abbr.length >= 3 && containsWord(normalizedText, abbr)
}

private fun containsWord(text: String, value: String): Boolean =
    text.split(' ').any { it == value }

private fun normalizeMatchText(value: String): String = value.lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun StremioStreamOption.toCandidate(): StreamCandidate {
    val qualityInfo = parseQualityFromChannelName(listOf(title, description, quality, bitrate).joinToString(" "))
    return StreamCandidate(
        id = "stremio:$streamUrl",
        playbackTarget = streamUrl,
        title = title,
        sourceKind = StreamSourceKind.STREMIO,
        quality = qualityInfo,
        qualityRank = qualityRank(qualityInfo),
        exactGameMatch = true,
        matchConfidence = 0.98f,
        matchEvidence = "Exact event match",
        headers = headers,
        stremioStream = this
    )
}

internal fun qualityRank(quality: StreamQualityInfo): Int {
    val resolution = when {
        quality.is4K -> 700
        quality.resolution?.contains("1080", true) == true -> 500
        quality.resolution?.contains("720", true) == true || quality.resolution == "HD" -> 300
        else -> 100
    }
    return resolution + (if (quality.isHdr) 60 else 0) + (if (quality.is60Fps) 30 else 0)
}
