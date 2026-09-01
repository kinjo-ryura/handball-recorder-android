package com.handplus.handballrecorder.ui.detail

import io.github.kinjoryura.handballtoolkit.Player
import io.github.kinjoryura.handballtoolkit.PlayerId
import io.github.kinjoryura.handballtoolkit.PlayerStatLine
import io.github.kinjoryura.handballtoolkit.TeamId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/**
 * 選手別スタッツの並びとチーム分け。iOS `MatchSummaryViewV2.sortedLines` と同じ答えを固定する。
 */
class PlayerStatsOrderingTest {

    private val homeTeam: TeamId = UUID.randomUUID()
    private val awayTeam: TeamId = UUID.randomUUID()

    @Test
    fun `得点の多い順に並ぶ`() {
        val low = line(goals = 1, misses = 0)
        val high = line(goals = 5, misses = 0)
        val players = players(low to "低" to homeTeam, high to "高" to homeTeam)
        assertEquals(
            listOf(high, low),
            PlayerStatsOrdering.sorted(listOf(low, high), players),
        )
    }

    @Test
    fun `得点が同じなら試投の多い順`() {
        // 試投 = 得点 + シュートミス（`.aar` のシムが供給する `shotAttempts`）。
        val fewer = line(goals = 3, misses = 1)
        val more = line(goals = 3, misses = 7)
        val players = players(fewer to "少" to homeTeam, more to "多" to homeTeam)
        assertEquals(
            listOf(more, fewer),
            PlayerStatsOrdering.sorted(listOf(fewer, more), players),
        )
    }

    @Test
    fun `得点も試投も同じなら名前順`() {
        val a = line(goals = 2, misses = 1)
        val b = line(goals = 2, misses = 1)
        val players = players(a to "あさひ" to homeTeam, b to "いずみ" to homeTeam)
        assertEquals(listOf(a, b), PlayerStatsOrdering.sorted(listOf(b, a), players))
    }

    @Test
    fun `名前が引けない行も落とさない`() {
        // 並べ替えの対象から外すと「記録はあるのに表に出てこない」になる。
        // 名前は空文字に倒すので、同点同試投なら名前のある行より前に来る（iOS も同じ）。
        val known = line(goals = 1, misses = 0)
        val unknown = line(goals = 1, misses = 0)
        val players = players(known to "あさひ" to homeTeam)
        assertEquals(
            listOf(unknown, known),
            PlayerStatsOrdering.sorted(listOf(known, unknown), players),
        )
    }

    @Test
    fun `入力のリストは変更しない`() {
        val low = line(goals = 1, misses = 0)
        val high = line(goals = 5, misses = 0)
        val input = listOf(low, high)
        PlayerStatsOrdering.sorted(input, players(low to "低" to homeTeam, high to "高" to homeTeam))
        assertEquals(listOf(low, high), input)
    }

    @Test
    fun `チーム分けは選手の teamId で行う`() {
        val homeLine = line(goals = 1, misses = 0)
        val awayLine = line(goals = 1, misses = 0)
        val players = players(homeLine to "ホーム選手" to homeTeam, awayLine to "アウェイ選手" to awayTeam)
        val lines = listOf(homeLine, awayLine)
        assertEquals(listOf(homeLine), PlayerStatsOrdering.linesForTeam(lines, players, homeTeam))
        assertEquals(listOf(awayLine), PlayerStatsOrdering.linesForTeam(lines, players, awayTeam))
    }

    @Test
    fun `選手が見つからない行はどちらのチームにも出さない`() {
        val orphan = line(goals = 1, misses = 0)
        assertEquals(
            emptyList<PlayerStatLine>(),
            PlayerStatsOrdering.linesForTeam(listOf(orphan), emptyMap(), homeTeam),
        )
    }

    @Test
    fun `チーム分けは元の並びを保つ`() {
        val first = line(goals = 1, misses = 0)
        val second = line(goals = 9, misses = 0)
        val players = players(first to "一" to homeTeam, second to "二" to homeTeam)
        assertEquals(
            listOf(first, second),
            PlayerStatsOrdering.linesForTeam(listOf(first, second), players, homeTeam),
        )
    }

    private fun line(goals: Long, misses: Long) = PlayerStatLine(
        `playerId` = UUID.randomUUID(),
        `goals` = goals,
        `shotMisses` = misses,
    )

    private fun players(
        vararg entries: Pair<Pair<PlayerStatLine, String>, TeamId>,
    ): Map<PlayerId, Player> = entries.associate { (lineAndName, teamId) ->
        val (statLine, name) = lineAndName
        statLine.playerId to Player(
            `id` = statLine.playerId,
            `teamId` = teamId,
            `name` = name,
        )
    }
}
