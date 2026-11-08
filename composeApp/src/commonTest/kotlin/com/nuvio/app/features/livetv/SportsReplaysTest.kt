package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.features.livetv.sports.Side
import com.nuvio.app.features.livetv.sports.Sport
import com.nuvio.app.features.livetv.sports.SportsEvent
import com.nuvio.app.features.livetv.sports.SportsReplays
import com.nuvio.app.features.livetv.sports.TeamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The YouTube searches the Sports page builds for replays and event videos. */
class SportsReplaysTest {

    private fun team(name: String) = TeamInfo(
        leagueId = "football/nfl",
        id = name.lowercase(),
        name = name,
        fullName = name,
        abbr = name.take(3).uppercase(),
        location = null,
        color = null,
        altColor = null,
        logo = null,
    )

    private fun side(name: String, home: Boolean) = Side(
        team = team(name),
        score = null,
        record = null,
        winner = false,
        home = home,
    )

    private fun event(state: GameState = GameState.POST) = SportsEvent(
        id = "test-game",
        leagueId = "football/nfl",
        startMs = 0L,
        hasTime = true,
        state = state,
        title = "Bears at Lions",
        subtitle = null,
        status = null,
        away = side("Bears", home = false),
        home = side("Lions", home = true),
    )

    @Test
    fun postEventSearchUsesTeamsLeagueAndHighlights() {
        val query = SportsReplays.postEventQuery(event(), Sport.AMERICAN_FOOTBALL)
        assertTrue(query.contains("Bears vs Lions"), query)
        assertTrue(query.contains("NFL"), query)
        assertTrue(query.contains("highlights"), query)
    }

    @Test
    fun preEventSearchUsesPreviewsAndWeighIns() {
        val teamQuery = SportsReplays.preEventQuery(event(GameState.PRE), Sport.AMERICAN_FOOTBALL)
        assertTrue(teamQuery.contains("Bears vs Lions"), teamQuery)
        assertTrue(teamQuery.contains("preview"), teamQuery)

        val fight = event(GameState.PRE).copy(leagueId = "fighting/ufc", title = "UFC 300")
        val fightQuery = SportsReplays.preEventQuery(fight, Sport.FIGHTING)
        assertTrue(fightQuery.contains("weigh in"), fightQuery)
    }

    @Test
    fun anEventWithoutTwoTeamsFallsBackToItsTitle() {
        val race = event(GameState.POST).copy(
            leagueId = "racing/f1",
            title = "Monaco Grand Prix",
            away = null,
            home = null,
        )
        assertTrue(SportsReplays.postEventQuery(race, Sport.RACING).startsWith("Monaco Grand Prix"))
        assertTrue(SportsReplays.preEventQuery(race, Sport.RACING).startsWith("Monaco Grand Prix"))
    }

    @Test
    fun everySportHasItsOwnReplaySearch() {
        val queries = Sport.entries.map { SportsReplays.sportQuery(it) }
        assertTrue(queries.all { it.isNotBlank() })
        assertEquals(queries.size, queries.distinct().size)
    }
}
