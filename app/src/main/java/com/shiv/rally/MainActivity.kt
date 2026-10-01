package com.shiv.rally

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.presentation.event.EventScreen
import com.shiv.rally.presentation.home.HomeScreen
import com.shiv.rally.presentation.home.LiveGamesScreen
import com.shiv.rally.presentation.iptv.IptvBrowserScreen
import com.shiv.rally.presentation.player.PlayerScreen
import com.shiv.rally.presentation.player.MultiViewScreen
import com.shiv.rally.presentation.settings.SettingsScreen
import com.shiv.rally.presentation.team.TeamHubScreen
import com.shiv.rally.presentation.league.LeagueHubScreen
import com.shiv.rally.presentation.league.LeaguesScreen
import com.shiv.rally.presentation.search.SearchScreen
import com.shiv.rally.presentation.theme.RallyTheme
import com.shiv.rally.presentation.theme.LocalRallyAccessibility
import com.shiv.rally.presentation.theme.RallyAccessibilitySettings
import com.shiv.rally.presentation.common.TvActionGate
import com.shiv.rally.presentation.common.RallyTvBackdrop
import com.shiv.rally.presentation.common.RallyTvChromeFocus
import com.shiv.rally.presentation.common.RallyTvDestination
import com.shiv.rally.presentation.common.RallyTvTopBar
import com.shiv.rally.presentation.common.RallyScoreSaverHost
import com.shiv.rally.presentation.highlights.HighlightsScreen
import com.shiv.rally.presentation.watchlist.WatchlistScreen
import com.shiv.rally.presentation.onboarding.OnboardingScreen
import com.shiv.rally.presentation.schedule.ScheduleScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private var activeNavController: NavHostController? = null
    private var lastDirectionalInputAt = 0L
    private val interactionTicks = MutableStateFlow(0L)

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) interactionTicks.value = SystemClock.uptimeMillis()
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode.isDirectionalKey()) {
            val now = SystemClock.uptimeMillis()
            // Some TV remotes and ADB bridges deliver directional bursts faster than Compose can
            // complete focus layout. Coalesce only impossible-to-render duplicates; normal remote
            // repeat cadence remains untouched.
            if (now - lastDirectionalInputAt < 40L) return true
            lastDirectionalInputAt = now
        }

        return try {
            super.dispatchKeyEvent(event)
        } catch (error: IllegalStateException) {
            // Compose TV can race focus search with an async shelf replacement. Dropping that one
            // stale key event is preferable to terminating playback or the entire Home screen.
            if (
                error.message?.contains("LayoutCoordinate operations are only valid when isAttached is true") == true ||
                error.message?.contains("FocusRequester is not initialized") == true
            ) {
                android.util.Log.w("RallyFocus", "Ignored a stale focus target during navigation", error)
                true
            } else {
                throw error
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 301)
        }
        setContent {
            RallyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    val navController = rememberNavController()
                    activeNavController = navController
                    val navigationGate = remember { TvActionGate(350L) }
                    val startDest = if (preferencesManager.hasCredentials()) "home" else "onboarding"
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route.orEmpty()
                    val accessibility = remember(currentRoute) {
                        RallyAccessibilitySettings(
                            reducedMotion = preferencesManager.reducedMotion,
                            highContrastFocus = preferencesManager.highContrastFocus,
                            largeText = preferencesManager.largeText,
                            spokenScoreSummaries = preferencesManager.spokenScoreSummaries
                        )
                    }
                    val baseDensity = LocalDensity.current
                    val chromeFocus = remember { RallyTvChromeFocus() }
                    var homeResetRequest by rememberSaveable { mutableIntStateOf(0) }
                    val contentFocusRequester = remember(currentRoute) { FocusRequester() }
                    val showChrome = currentRoute != "onboarding" &&
                        !currentRoute.startsWith("player/") &&
                        !currentRoute.startsWith("multiview")
                    // Home content starts below the persistent navigation shell. This keeps
                    // scrolling hero text and cards from leaking behind the logo and tabs.
                    val immersiveHero = currentRoute.startsWith("event/")
                    val selectedDestination = when {
                        currentRoute == "home" -> RallyTvDestination.HOME
                        currentRoute == "iptv" -> RallyTvDestination.LIVE
                        currentRoute == "live-games" -> RallyTvDestination.LIVE
                        currentRoute.startsWith("event/") -> RallyTvDestination.LIVE
                        currentRoute == "schedule" -> RallyTvDestination.SCHEDULE
                        currentRoute == "leagues" || currentRoute.startsWith("league/") -> RallyTvDestination.LEAGUES
                        currentRoute == "highlights" -> RallyTvDestination.HIGHLIGHTS
                        currentRoute == "watchlist" || currentRoute.startsWith("team/") -> RallyTvDestination.MY_RALLY
                        currentRoute == "search" -> RallyTvDestination.SEARCH
                        currentRoute == "settings" -> RallyTvDestination.SETTINGS
                        else -> null
                    }

                    fun navigateTopLevel(route: String) {
                        if (navigationGate.tryAcquire("top-level:$route")) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }

                    CompositionLocalProvider(
                        LocalRallyAccessibility provides accessibility,
                        LocalDensity provides Density(baseDensity.density, if (accessibility.largeText) 1.12f else baseDensity.fontScale)
                    ) {
                    RallyTvBackdrop {
                        Box(Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = startDest,
                        modifier = Modifier.fillMaxSize().padding(top = if (showChrome && !immersiveHero) 70.dp else 0.dp)
                    ) {
                        composable("onboarding") {
                            OnboardingScreen(
                                onContinue = {
                                    navController.navigate("settings") {
                                        popUpTo("onboarding") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onSaved = {
                                    if (!navController.popBackStack("home", inclusive = false)) {
                                        navController.navigate("home") {
                                            popUpTo("settings") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                onEventClick = { event ->
                                    if (navigationGate.tryAcquire("navigate")) {
                                        navController.navigate("event/${event.id.routeEncoded()}") {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onSettingsClick = {
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("settings") { launchSingleTop = true }
                                },
                                onNavigateToPlayer = { channelId, clipTitle ->
                                    if (navigationGate.tryAcquire("navigate")) {
                                        val encoded = channelId.routeEncoded()
                                        navController.navigate("player/$encoded${clipTitle?.let { "?clipTitle=${it.routeEncoded()}" }.orEmpty()}") { launchSingleTop = true }
                                    }
                                },
                                onNavigateToIptv = {
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("iptv") { launchSingleTop = true }
                                },
                                onNavigateToLiveGames = { navigateTopLevel("live-games") },
                                onNavigateToHighlights = { navigateTopLevel("highlights") },
                                onLeagueClick = { league ->
                                    if (navigationGate.tryAcquire("navigate")) {
                                        navController.navigate("league/${league.routeEncoded()}") { launchSingleTop = true }
                                    }
                                },
                                onScheduleClick = { navigateTopLevel("schedule") },
                                initialFocusRequester = contentFocusRequester,
                                topNavigationFocusRequester = chromeFocus.home,
                                homeResetRequest = homeResetRequest
                            )
                        }
                        composable("schedule") {
                            ScheduleScreen(
                                onEventClick = { event ->
                                    if (navigationGate.tryAcquire("navigate")) {
                                        navController.navigate("event/${event.id.routeEncoded()}") { launchSingleTop = true }
                                    }
                                },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                        composable("live-games") {
                            LiveGamesScreen(
                                onEventClick = { event ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("event/${event.id.routeEncoded()}") { launchSingleTop = true }
                                },
                                onBrowseChannels = { navigateTopLevel("iptv") },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                        composable("search") {
                            SearchScreen(
                                onEvent = { event ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("event/${event.id.routeEncoded()}") { launchSingleTop = true }
                                },
                                onTeam = { team ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("team/${team.league.routeEncoded()}/${team.id.routeEncoded()}") { launchSingleTop = true }
                                },
                                onLeague = { league ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("league/${league.routeEncoded()}") { launchSingleTop = true }
                                },
                                onPlay = { target ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("player/${target.routeEncoded()}") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                        composable("highlights") {
                            HighlightsScreen(
                                onPlay = { target, title ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("player/${target.routeEncoded()}?clipTitle=${title.routeEncoded()}") { launchSingleTop = true }
                                },
                                onEvent = { event ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("event/${event.id.routeEncoded()}") { launchSingleTop = true }
                                },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                        composable("leagues") {
                            LeaguesScreen(initialFocusRequester = contentFocusRequester) { league ->
                                if (navigationGate.tryAcquire("navigate")) navController.navigate("league/${league.routeEncoded()}") { launchSingleTop = true }
                            }
                        }
                        composable("watchlist") {
                            WatchlistScreen(
                                onTeam = { team ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("team/${team.league.routeEncoded()}/${team.id.routeEncoded()}") { launchSingleTop = true }
                                },
                                onEvent = { event ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("event/${event.id.routeEncoded()}") { launchSingleTop = true }
                                },
                                onManage = { navigateTopLevel("settings") },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                        composable(
                            route = "team/{league}/{teamId}",
                            arguments = listOf(
                                navArgument("league") { type = NavType.StringType },
                                navArgument("teamId") { type = NavType.StringType }
                            )
                        ) {
                            TeamHubScreen(
                                onEventClick = { event ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("event/${event.id.routeEncoded()}") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                        composable(
                            route = "league/{league}",
                            arguments = listOf(navArgument("league") { type = NavType.StringType })
                        ) {
                            LeagueHubScreen(
                                onEventClick = { event ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("event/${event.id.routeEncoded()}") { launchSingleTop = true }
                                },
                                onWatchChannel = { channelId ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("player/${channelId.routeEncoded()}") { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                        composable(
                            route = "event/{eventId}",
                            arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
                            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "rally://event/{eventId}" })
                        ) {
                            EventScreen(
                                onPlayHighlight = { target, title ->
                                    if (navigationGate.tryAcquire("navigate")) navController.navigate("player/${target.routeEncoded()}?clipTitle=${title.routeEncoded()}") { launchSingleTop = true }
                                },
                                onWatchLive = { streamOrChannelId ->
                                    if (!navigationGate.tryAcquire("navigate")) return@EventScreen
                                    val encoded = streamOrChannelId.routeEncoded()
                                    val eventId = it.arguments?.getString("eventId")
                                    val targetRoute = if (!eventId.isNullOrEmpty() && eventId != "null") {
                                        "player/$encoded?eventId=${eventId.routeEncoded()}"
                                    } else {
                                        "player/$encoded"
                                    }
                                    navController.navigate(targetRoute) { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                        composable(
                            route = "player/{channelId}?eventId={eventId}&clipTitle={clipTitle}",
                            arguments = listOf(
                                navArgument("channelId") { type = NavType.StringType },
                                navArgument("eventId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("clipTitle") { type = NavType.StringType; nullable = true; defaultValue = null }
                            ),
                            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "rally://player/{channelId}?eventId={eventId}" })
                        ) {
                            PlayerScreen(
                                onNavigateToMultiView = { channelId, eventId, pairedEventId ->
                                    if (!navigationGate.tryAcquire("navigate")) return@PlayerScreen
                                    val route = if (!pairedEventId.isNullOrEmpty() && !eventId.isNullOrEmpty()) {
                                        "multiview?eventIds=${eventId.routeEncoded()},${pairedEventId.routeEncoded()}"
                                    } else {
                                        val encodedChan = channelId.routeEncoded()
                                        if (!eventId.isNullOrBlank()) {
                                            "multiview?channelId=$encodedChan&eventId=${eventId.routeEncoded()}"
                                        } else {
                                            "multiview?channelId=$encodedChan"
                                        }
                                    }
                                    navController.navigate(route) { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "multiview?channelId={channelId}&eventId={eventId}&eventIds={eventIds}",
                            arguments = listOf(
                                navArgument("channelId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("eventId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("eventIds") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) {
                            MultiViewScreen(
                                onFullScreen = { channelOrUrl, eventId ->
                                    if (!navigationGate.tryAcquire("navigate")) return@MultiViewScreen
                                    val encoded = channelOrUrl.routeEncoded()
                                    val route = if (!eventId.isNullOrEmpty() && eventId != "null") {
                                        "player/$encoded?eventId=${eventId.routeEncoded()}"
                                    } else {
                                        "player/$encoded"
                                    }
                                    navController.navigate(route) { launchSingleTop = true }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("iptv") {
                            IptvBrowserScreen(
                                onChannelClick = { channelId ->
                                    if (navigationGate.tryAcquire("navigate")) {
                                        val encoded = channelId.routeEncoded()
                                        navController.navigate("player/$encoded") { launchSingleTop = true }
                                    }
                                },
                                onBack = { navController.popBackStack() },
                                initialFocusRequester = contentFocusRequester
                            )
                        }
                    }
                        if (showChrome) {
                            RallyTvTopBar(
                                selected = selectedDestination,
                                onHome = {
                                    homeResetRequest += 1
                                    navigateTopLevel("home")
                                },
                                onLive = { navigateTopLevel("live-games") },
                                onSchedule = { navigateTopLevel("schedule") },
                                onLeagues = { navigateTopLevel("leagues") },
                                onHighlights = { navigateTopLevel("highlights") },
                                onMyRally = { navigateTopLevel("watchlist") },
                                onSearch = { navigateTopLevel("search") },
                                onSettings = { navigateTopLevel("settings") },
                                focus = chromeFocus,
                                contentFocusRequester = contentFocusRequester,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                        RallyScoreSaverHost(
                            enabled = preferencesManager.scoreSaverEnabled,
                            interactionEvents = interactionTicks,
                            currentRoute = currentRoute,
                            onDismiss = { interactionTicks.value = SystemClock.uptimeMillis() }
                        )
                    }
                    }
                }
            }
        }
    }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeNavController?.handleDeepLink(intent)
    }

    override fun onDestroy() {
        activeNavController = null
        super.onDestroy()
    }
}

private fun String.routeEncoded(): String = Uri.encode(this)

private fun Int.isDirectionalKey(): Boolean =
    this == KeyEvent.KEYCODE_DPAD_UP ||
        this == KeyEvent.KEYCODE_DPAD_DOWN ||
        this == KeyEvent.KEYCODE_DPAD_LEFT ||
        this == KeyEvent.KEYCODE_DPAD_RIGHT
