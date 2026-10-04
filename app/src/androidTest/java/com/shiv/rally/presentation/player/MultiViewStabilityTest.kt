@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@file:androidx.media3.common.util.UnstableApi

package com.shiv.rally.presentation.player

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.shiv.rally.data.local.*
import com.shiv.rally.data.remote.stalker.*
import com.shiv.rally.data.remote.stremio.*
import com.shiv.rally.domain.model.*
import com.shiv.rally.domain.repository.*
import com.shiv.rally.domain.usecase.*
import com.shiv.rally.presentation.common.RallyTvBackdrop
import com.shiv.rally.presentation.theme.RallyTheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant

class MultiViewStabilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private lateinit var server: MultiViewFaultServer
    private lateinit var vm: MultiViewViewModel
    private var mounted by mutableStateOf(true)
    private val pairs = listOf("Chiefs" to "Ravens", "Packers" to "Bears", "Eagles" to "Cowboys", "Bills" to "Dolphins")
    private val games = pairs.mapIndexed { i, (home, away) -> SportEvent("game${i + 1}", "$away vs $home",
        Team("h$i", home, home.take(3).uppercase()), Team("a$i", away, away.take(3).uppercase()),
        Instant.now(), EventStatus.LIVE, scoreHome = 14, scoreAway = 10, sport = "Football", league = "NFL") }

    private fun start(portal: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".qa")) {
            "Run with -PrallyQa=true. Failure-injection tests must use the isolated QA application."
        }
        server = MultiViewFaultServer(games, instrumentation.context.assets.open("multiview/segment.ts").use { it.readBytes() })
        val preferences = PreferencesManager(instrumentation.targetContext).apply {
            portalUrl = server.base
            macAddress = "00:1A:79:11:22:33"
            authToken = "Bearer qa-session"
            stremioAddonUrls = if (portal) emptyList() else listOf("${server.base}/addon/manifest.json")
        }
        val retrofit = Retrofit.Builder().baseUrl("${server.base}/").addConverterFactory(GsonConverterFactory.create()).build()
        val dao = object : ChannelDao {
            private var channels = games.mapIndexed { i, game -> ChannelEntity(game.id, "${i + 1}", game.name, "Sports", null, "ffrt http://localhost/ch/${game.id}") }
            override suspend fun getAllChannels() = channels
            override suspend fun getChannelById(id: String) = channels.find { it.id == id }
            override suspend fun searchChannels(query: String, limit: Int) = channels.filter { it.name.contains(query) }.take(limit)
            override suspend fun getChannelCount() = channels.size
            override suspend fun insertChannels(channels: List<ChannelEntity>) { this.channels = channels }
            override suspend fun clearAll() { channels = emptyList() }
        }
        val stalker = StalkerIptvRepositoryImpl(retrofit.create(StalkerApi::class.java), preferences, dao).also { it.clearMemoryCache() }
        val emptyIptv = object : IptvRepository {
            override suspend fun authenticate() = true
            override suspend fun getChannels() = emptyList<IptvChannel>()
            override suspend fun getChannelStreamUrl(channelId: String) = error("Unexpected IPTV resolution")
        }
        val iptv: IptvRepository = if (portal) stalker else emptyIptv
        val addons = StremioRepositoryImpl(retrofit.create(StremioApi::class.java), preferences)
        val sports = object : SportsRepository {
            override suspend fun getLiveEvents() = games
            override suspend fun getUpcomingEvents() = emptyList<SportEvent>()
            override suspend fun getEventsByLeague(league: String) = games
            override suspend fun getEventById(eventId: String) = games.find { it.id == eventId }
            override suspend fun searchEvents(query: String) = games
            override fun observeLiveEvent(eventId: String) = emptyFlow<SportEvent>()
            override suspend fun getTvStationsForEvent(eventId: String) = emptyList<String>()
            override suspend fun getEventSummary(event: SportEvent) = event
        }
        val matcher = object : MatcherService {
            override suspend fun matchEventToChannels(event: SportEvent, channels: List<IptvChannel>) = emptyList<MatchResult>()
            override suspend fun getRelevantChannelsForEvent(event: SportEvent, channels: List<IptvChannel>) =
                channels.filter { it.id == event.id }.map { RelevantChannel(it, 100f) }
        }
        compose.setContent {
            vm = remember { MultiViewViewModel(SavedStateHandle(mapOf("eventIds" to games.joinToString(",") { it.id })),
                sports, iptv, SelectBestStreamUseCase(iptv, addons, matcher), preferences).also { store.put("vm", it) } }
            if (mounted) RallyTheme { RallyTvBackdrop { MultiViewScreen(vm, { _, _ -> }, {}) } }
        }
        playing(4)
    }
    private fun playing(count: Int) {
        compose.waitUntil(45_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing")).fetchSemanticsNodes().size == count
        }
        assertEquals(0, server.badHeaders.get())
    }
    private fun verifyVisibleVideo(name: String) {
        Thread.sleep(700)
        val nodes = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing")).fetchSemanticsNodes()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "stability-$name.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        try {
            nodes.forEachIndexed { index, node ->
                val b = node.boundsInRoot
                var bright = 0
                var samples = 0
                for (y in (b.top + b.height * .35f).toInt() until (b.top + b.height * .8f).toInt() step 6) {
                    for (x in (b.left + b.width * .25f).toInt() until (b.left + b.width * .75f).toInt() step 6) {
                        if (x !in 0 until screenshot.width || y !in 0 until screenshot.height) continue
                        val pixel = screenshot.getPixel(x, y)
                        if (maxOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel)) > 70) bright++
                        samples++
                    }
                }
                assertTrue("Tile ${index + 1} has rendered counters but no visible video: $bright/$samples", samples > 0 && bright > samples / 4)
            }
        } finally { screenshot.recycle() }
    }
    private fun settle(ms: Long) {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) { Thread.sleep(300); compose.waitForIdle() }
    }
    @After fun finish() {
        compose.runOnIdle { mounted = false; store.clear() }
        compose.waitForIdle()
        if (::server.isInitialized) server.close()
    }

    @Test fun fourAddonStreamsRecoverFromExpiredUrlsAndSurviveFocusLayoutAndSourceChanges() {
        start(portal = false)
        val before = vm.uiState.value.slots
        repeat(12) { n ->
            compose.runOnIdle { vm.setFocusedSlot(n % 4); vm.setLayoutMode(if (n % 2 == 0) MultiViewLayoutMode.QUAD_GRID else MultiViewLayoutMode.QUAD_FOCUS) }
            settle(150)
        }
        compose.onNodeWithText("Immersive", useUnmergedTree = true).performClick()
        playing(4)
        verifyVisibleVideo("addon-immersive-four")
        // Focus/layout changes must never discover new URLs or restart playback.
        assertTrue(server.links.values.all { it.get() == 1 })
        server.revoke(before[0].streamUrl)
        compose.waitUntil(45_000) { vm.uiState.value.slots[0].playbackRevision > before[0].playbackRevision }
        playing(4)
        assertTrue(server.denied.get() > 0)
        assertEquals(before.drop(1).map { it.streamUrl }, vm.uiState.value.slots.drop(1).map { it.streamUrl })
        repeat(6) {
            compose.runOnIdle { vm.retrySlot(it % 4) }
            playing(4)
        }
        repeat(3) {
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(300)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            playing(4)
        }
        settle(15_000)
        playing(4)
        verifyVisibleVideo("addon-restored-four")
        assertTrue(vm.uiState.value.slots.all { it.error == null })
    }

    @Test fun fourPortalStreamsRecoverAndOnlyFrozenTileRestarts() {
        start(portal = true)
        val before = vm.uiState.value.slots
        server.revoke(before[1].streamUrl)
        compose.waitUntil(45_000) { vm.uiState.value.slots[1].playbackRevision > before[1].playbackRevision }
        playing(4)
        assertTrue(server.links["game2"]!!.get() >= 2)
        assertEquals(before[0].streamUrl, vm.uiState.value.slots[0].streamUrl)
        val frozen = vm.uiState.value.slots[2]
        server.frozenGames.add("game3")
        compose.waitUntil(50_000) { vm.uiState.value.slots[2].playbackRevision > frozen.playbackRevision }
        server.frozenGames.clear()
        playing(4)
        repeat(4) {
            compose.runOnIdle { vm.swapSlot(0); vm.promoteSlot(3); vm.setAudioSlot(it) }
            settle(300)
        }
        playing(4)
        compose.runOnIdle { vm.removeSlot(3); vm.addStatsTile() }
        playing(3)
        compose.runOnIdle { vm.removeStatsTile(); vm.addSlotFromEvent(games[3]) }
        playing(4)
        settle(15_000)
        playing(4)
        assertEquals(0, server.badHeaders.get())
        verifyVisibleVideo("portal-four")
    }

    @Test fun permanentlyUnavailableSourceStopsRetryingWhileOtherGamesKeepPlaying() {
        start(portal = false)
        server.offlineGames.add("game1")
        compose.waitUntil(75_000) { vm.uiState.value.slots[0].recoveryAttempt == 3 && vm.uiState.value.slots[0].error?.contains("choose another") == true }
        playing(3)
        val attempts = server.links["game1"]!!.get()
        settle(8_000)
        assertEquals(attempts, server.links["game1"]!!.get())
        server.offlineGames.clear()
        compose.runOnIdle { vm.retrySlot(0) }
        playing(4)
    }
}
