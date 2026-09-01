package com.handplus.handballrecorder.ui.detail

import io.github.kinjoryura.handballtoolkit.ControlFact
import io.github.kinjoryura.handballtoolkit.FactAnchor
import io.github.kinjoryura.handballtoolkit.FactId
import io.github.kinjoryura.handballtoolkit.MatchClock
import io.github.kinjoryura.handballtoolkit.MatchFact
import io.github.kinjoryura.handballtoolkit.MatchFactPayload
import io.github.kinjoryura.handballtoolkit.Phase
import io.github.kinjoryura.handballtoolkit.PhaseKind
import io.github.kinjoryura.handballtoolkit.PhaseStartPayload
import io.github.kinjoryura.handballtoolkit.PlayEventKind
import io.github.kinjoryura.handballtoolkit.PlayFact
import io.github.kinjoryura.handballtoolkit.PossessionFact
import io.github.kinjoryura.handballtoolkit.ResolvedFact
import io.github.kinjoryura.handballtoolkit.StoppageKind
import io.github.kinjoryura.handballtoolkit.StoppagePayload
import io.github.kinjoryura.handballtoolkit.TeamId
import io.github.kinjoryura.handballtoolkit.VideoClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * タイムラインの phase グループ化。**画面が `when (kind)` を書かないための規則**を固定する。
 */
class TimelineGroupingTest {

    // ── phase ラベルの導出 ──

    @Test
    fun `regular の出現順からラベルを作る`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val labels = TimelineGrouping.phaseLabels(
            listOf(phase(first, PhaseKind.REGULAR), phase(second, PhaseKind.REGULAR)),
        )
        assertEquals("前半", labels[first])
        assertEquals("後半", labels[second])
    }

    @Test
    fun `shootout は regular の index を消費しない`() {
        // 前半 / 後半 / 延長前半 / 延長後半 のあとに 7mTC が来るのが実試合の並び。
        val ids = List(5) { UUID.randomUUID() }
        val kinds = listOf(
            PhaseKind.REGULAR,
            PhaseKind.REGULAR,
            PhaseKind.REGULAR,
            PhaseKind.REGULAR,
            PhaseKind.SHOOTOUT,
        )
        val labels = TimelineGrouping.phaseLabels(ids.zip(kinds).map { (id, kind) -> phase(id, kind) })
        assertEquals(
            listOf("前半", "後半", "延長前半", "延長後半", "7mTC"),
            ids.map { labels[it] },
        )
    }

    @Test
    fun `phase が無ければラベルも空`() {
        assertTrue(TimelineGrouping.phaseLabels(emptyList()).isEmpty())
    }

    // ── グループ化 ──

    @Test
    fun `phaseStart は行ではなく見出しになる`() {
        val phaseId = UUID.randomUUID()
        val goal = play(match = 10.0)
        val groups = TimelineGrouping.groups(
            orderedFacts = listOf(phaseStart(phaseId, match = 0.0), goal),
            phaseLabelByFactId = mapOf(phaseId to "前半"),
            phaseFactIdAt = { phaseId },
        )
        assertEquals(1, groups.size)
        assertEquals("前半", groups[0].label)
        assertEquals(listOf(goal), groups[0].facts)
    }

    @Test
    fun `phase ごとにグループが分かれる`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val firstGoal = play(match = 10.0)
        val secondGoal = play(match = 2000.0)
        val groups = TimelineGrouping.groups(
            orderedFacts = listOf(
                phaseStart(first, match = 0.0),
                firstGoal,
                phaseStart(second, match = 1800.0),
                secondGoal,
            ),
            phaseLabelByFactId = mapOf(first to "前半", second to "後半"),
            phaseFactIdAt = { seconds -> if (seconds < 1800.0) first else second },
        )
        assertEquals(listOf("前半", "後半"), groups.map { it.label })
        assertEquals(listOf(firstGoal), groups[0].facts)
        assertEquals(listOf(secondGoal), groups[1].facts)
    }

    @Test
    fun `possession は行にしない`() {
        // CV 出力は 1 試合に 110〜200 件入り、出すと得点やカードが埋もれる（親リポ #217）。
        val phaseId = UUID.randomUUID()
        val goal = play(match = 10.0)
        val groups = TimelineGrouping.groups(
            orderedFacts = listOf(phaseStart(phaseId, match = 0.0), possession(match = 5.0), goal),
            phaseLabelByFactId = mapOf(phaseId to "前半"),
            phaseFactIdAt = { phaseId },
        )
        assertEquals(listOf(goal), groups.single().facts)
    }

    @Test
    fun `stoppage は行として残す`() {
        val phaseId = UUID.randomUUID()
        val timeout = stoppage(match = 300.0)
        val groups = TimelineGrouping.groups(
            orderedFacts = listOf(phaseStart(phaseId, match = 0.0), timeout),
            phaseLabelByFactId = mapOf(phaseId to "前半"),
            phaseFactIdAt = { phaseId },
        )
        assertEquals(listOf(timeout), groups.single().facts)
    }

    @Test
    fun `記録の無い phase も見出しだけ残す`() {
        // 落とすと「後半が無い試合」に見えてしまう。
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val groups = TimelineGrouping.groups(
            orderedFacts = listOf(phaseStart(first, match = 0.0), phaseStart(second, match = 1800.0)),
            phaseLabelByFactId = mapOf(first to "前半", second to "後半"),
            phaseFactIdAt = { seconds -> if (seconds < 1800.0) first else second },
        )
        assertEquals(listOf("前半", "後半"), groups.map { it.label })
        assertTrue(groups.all { it.facts.isEmpty() })
    }

    @Test
    fun `phase を持たない記録は見出しなしのグループになる`() {
        // ハイライト（phase を持たず動画時刻しか無い）がこの形。
        val scene = play(match = null, video = 42.0)
        val groups = TimelineGrouping.groups(
            orderedFacts = listOf(scene),
            phaseLabelByFactId = emptyMap(),
            phaseFactIdAt = { null },
        )
        assertNull(groups.single().label)
        assertEquals(listOf(scene), groups.single().facts)
    }

    @Test
    fun `phase 開始より前の記録は見出しなしで先頭に置く`() {
        val phaseId = UUID.randomUUID()
        val before = play(match = null, video = 1.0)
        val after = play(match = 10.0)
        val groups = TimelineGrouping.groups(
            orderedFacts = listOf(before, phaseStart(phaseId, match = 0.0), after),
            phaseLabelByFactId = mapOf(phaseId to "前半"),
            phaseFactIdAt = { phaseId },
        )
        assertEquals(listOf(null, "前半"), groups.map { it.label })
        assertEquals(listOf(before), groups[0].facts)
        assertEquals(listOf(after), groups[1].facts)
    }

    @Test
    fun `fact が 1 件も無ければグループも無い`() {
        assertTrue(
            TimelineGrouping.groups(emptyList(), emptyMap()) { null }.isEmpty(),
        )
    }

    @Test
    fun `possession しか無い場合も空になる`() {
        assertTrue(
            TimelineGrouping.groups(listOf(possession(match = 1.0)), emptyMap()) { null }.isEmpty(),
        )
    }

    // ── 左右の振り分け ──

    @Test
    fun `ホーム左のときホームの記録は左`() {
        val home = UUID.randomUUID()
        val away = UUID.randomUUID()
        assertTrue(TimelineGrouping.isOnLeft(home, home, isHomeOnLeft = true))
        assertFalse(TimelineGrouping.isOnLeft(away, home, isHomeOnLeft = true))
    }

    @Test
    fun `ホーム右のときは左右が入れ替わる`() {
        val home = UUID.randomUUID()
        val away = UUID.randomUUID()
        assertFalse(TimelineGrouping.isOnLeft(home, home, isHomeOnLeft = false))
        assertTrue(TimelineGrouping.isOnLeft(away, home, isHomeOnLeft = false))
    }

    @Test
    fun `チーム不明の記録は左に出す`() {
        val home = UUID.randomUUID()
        assertTrue(TimelineGrouping.isOnLeft(null, home, isHomeOnLeft = true))
        assertTrue(TimelineGrouping.isOnLeft(null, home, isHomeOnLeft = false))
    }

    // ── フィクスチャ ──

    private fun phase(id: FactId, kind: PhaseKind) = Phase(
        `factId` = id,
        `kind` = kind,
        `matchElapsedStart` = null,
        `matchElapsedEnd` = null,
        `videoElapsedStart` = null,
        `videoElapsedEnd` = null,
    )

    private fun play(match: Double?, video: Double? = null, teamId: TeamId? = null) = resolved(
        payload = MatchFactPayload.Play(
            PlayFact(
                `kind` = PlayEventKind.GOAL,
                `teamId` = teamId,
                `anchor` = FactAnchor.MatchClock(MatchClock(match ?: 0.0)),
            ),
        ),
        match = match,
        video = video,
    )

    private fun phaseStart(id: FactId, match: Double) = resolved(
        payload = MatchFactPayload.Control(
            ControlFact.PhaseStart(
                PhaseStartPayload(
                    `kind` = PhaseKind.REGULAR,
                    `startAnchor` = FactAnchor.MatchClock(MatchClock(match)),
                    `endAnchor` = FactAnchor.MatchClock(MatchClock(match + 1800.0)),
                ),
            ),
        ),
        match = match,
        video = null,
        id = id,
    )

    private fun stoppage(match: Double) = resolved(
        payload = MatchFactPayload.Control(
            ControlFact.Stoppage(
                StoppagePayload(
                    `kind` = StoppageKind.TIMEOUT,
                    `startAnchor` = FactAnchor.MatchClock(MatchClock(match)),
                ),
            ),
        ),
        match = match,
        video = null,
    )

    private fun possession(match: Double) = resolved(
        payload = MatchFactPayload.Possession(
            PossessionFact(
                `teamId` = UUID.randomUUID(),
                `anchor` = FactAnchor.MatchClock(MatchClock(match)),
            ),
        ),
        match = match,
        video = null,
    )

    private fun resolved(
        payload: MatchFactPayload,
        match: Double?,
        video: Double?,
        id: FactId = UUID.randomUUID(),
    ) = ResolvedFact(
        `fact` = MatchFact(`id` = id, `recordedAt` = Instant.EPOCH, `payload` = payload),
        `resolvedMatchClock` = match?.let { MatchClock(it) },
        `resolvedVideoClock` = video?.let { VideoClock(it) },
    )
}
