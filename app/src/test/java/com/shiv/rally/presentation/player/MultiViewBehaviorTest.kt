package com.shiv.rally.presentation.player

import com.shiv.rally.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MultiViewBehaviorTest {
    private fun game(id: String, time: String = "2026-10-04T17:00:00Z", league: String = "NFL") = SportEvent(
        id, "Chiefs vs Ravens", Team("kc", "Chiefs", "KC"), Team("bal", "Ravens", "BAL"),
        Instant.parse(time), EventStatus.LIVE, sport = "Football", league = league
    )

    @Test fun everyLayoutPreservesVideoAspectAndScreenBounds() {
        listOf(960f to 540f, 848f to 425f, 1280f to 720f).forEach { (width, height) ->
            (1..4).forEach { count -> MultiViewLayoutMode.values().forEach { mode ->
                multiViewBounds(width, height, count, mode, 6f).forEach { cell ->
                    assertEquals(16f / 9f, cell.width / cell.height, .001f)
                    assertTrue(cell.x >= -.001f && cell.y >= -.001f)
                    assertTrue(cell.x + cell.width <= width + .001f)
                    assertTrue(cell.y + cell.height <= height + .001f)
                }
            } }
        }
    }

    @Test fun immersiveQuadFillsScreenWithSharedEdges() {
        val cells = multiViewBounds(960f, 540f, 4, MultiViewLayoutMode.QUAD_GRID, 0f)
        assertEquals(0f, cells[0].x, .001f)
        assertEquals(0f, cells[0].y, .001f)
        assertEquals(cells[0].width, cells[1].x, .001f)
        assertEquals(cells[0].height, cells[2].y, .001f)
        assertEquals(960f, cells[3].x + cells[3].width, .001f)
        assertEquals(540f, cells[3].y + cells[3].height, .001f)
    }

    @Test fun immersiveFocusLayoutsHaveNoInternalGaps() {
        val three = multiViewBounds(960f, 540f, 3, MultiViewLayoutMode.TRIPLE_FOCUS, 0f)
        assertEquals(three[0].x + three[0].width, three[1].x, .001f)
        assertEquals(three[1].y + three[1].height, three[2].y, .001f)
        assertEquals(three[0].y + three[0].height, three[2].y + three[2].height, .001f)
        val four = multiViewBounds(960f, 540f, 4, MultiViewLayoutMode.QUAD_FOCUS, 0f)
        assertEquals(four[0].y + four[0].height, four[3].y + four[3].height, .001f)
    }

    @Test fun focusNeighborsFollowVisualGrid() {
        val cells = multiViewBounds(960f, 540f, 4, MultiViewLayoutMode.QUAD_GRID, 0f)
        assertEquals(1, multiViewNeighbor(cells, 0, true, true))
        assertEquals(2, multiViewNeighbor(cells, 0, false, true))
        assertEquals(1, multiViewNeighbor(cells, 3, false, false))
        assertNull(multiViewNeighbor(cells, 0, true, false))
    }

    @Test fun audioSwitchMutesOldStreamBeforeEnablingNewStream() {
        val events = mutableListOf<Pair<String, Boolean>>()
        val router = MultiViewAudioRouter()
        router.register("one") { events.add("one" to it) }
        router.register("two") { events.add("two" to it) }
        router.select("one")
        events.clear()
        router.select("two")
        assertEquals(listOf("one" to false, "two" to false, "two" to true), events)
    }

    @Test fun lateRegistrationAndRemovalCannotLeaveAudioPlaying() {
        val events = mutableListOf<Boolean>()
        val router = MultiViewAudioRouter()
        router.select("one")
        router.register("one") { events.add(it) }
        router.unregister("one")
        assertEquals(listOf(false, true, false), events)
    }

    @Test fun systemAudioFocusLossMutesAndRestoresOnlySelectedStream() {
        val events = mutableListOf<Pair<String, Boolean>>()
        val router = MultiViewAudioRouter()
        router.register("one") { events.add("one" to it) }
        router.register("two") { events.add("two" to it) }
        router.select("one")
        router.setAudioAllowed(false)
        router.select("two")
        events.clear()
        router.setAudioAllowed(true)
        assertEquals(listOf("one" to false, "two" to false, "two" to true), events)
    }

    @Test fun redZoneUsesEasternDateAndAfternoonCoverage() {
        val games = listOf(game("early", "2026-10-04T13:30:00Z"), game("one"),
            game("late", "2026-10-04T20:25:00Z"), game("night", "2026-10-05T00:20:00Z"),
            game("college", league = "NCAAF"), game("canceled").copy(status = EventStatus.CANCELED))
        assertEquals(listOf("one", "late"), redZoneGamesForDay(games, LocalDate.of(2026, 10, 4)).map { it.id })
    }

    @Test fun redZoneIncludesCompletedAfternoonGamesAndDeduplicatesSharedGame() {
        val one = game("one").copy(status = EventStatus.FINISHED)
        val two = game("two")
        val slots = listOf(MultiViewSlot(event = one), MultiViewSlot(event = one), MultiViewSlot(title = "NFL Red Zone HD"))
        assertEquals(listOf("one", "two"), multiviewStatsGames(slots, listOf(one, two, two)).map { it.id })
    }

    @Test fun removingRedZoneRemovesItsExtraGames() {
        val one = game("one")
        assertEquals(listOf(one), multiviewStatsGames(listOf(MultiViewSlot(event = one)), listOf(game("two"))))
    }

    @Test fun titleFallsBackToActualEventNameWhenAbbreviationsAreMissing() {
        assertEquals("Chiefs vs Ravens", multiViewTitle(game("one").copy(homeTeam = null, awayTeam = null)))
        assertEquals("BAL vs KC", multiViewTitle(game("one")))
    }

    @Test fun statLabelsStayPairedWithValuesEvenWhenProviderOmitsLabels() {
        val table = PlayerStatTable(teamName = "Chiefs", teamAbbreviation = "KC", labels = listOf("YDS"))
        val row = PlayerStatRow(displayName = "Player", stats = listOf("284", "2"))
        assertEquals(listOf("YDS" to "284", "Stat 2" to "2"), playerStatPairs(table, row))
        assertEquals("Kick Returns", statCategoryLabel("kickReturns"))
    }
}
