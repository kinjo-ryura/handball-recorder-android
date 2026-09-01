package com.handplus.handballrecorder.domain

import io.github.kinjoryura.handballtoolkit.FactAnchor
import io.github.kinjoryura.handballtoolkit.MatchClock
import io.github.kinjoryura.handballtoolkit.MatchFact
import io.github.kinjoryura.handballtoolkit.MatchFactPayload
import io.github.kinjoryura.handballtoolkit.PlayEventKind
import io.github.kinjoryura.handballtoolkit.PlayFact
import io.github.kinjoryura.handballtoolkit.ResolvedFact
import io.github.kinjoryura.handballtoolkit.VideoClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * タイムラインの並べ替え。`resolvedFacts` は時系列順ではないので、キーと安定化の規則を固定する。
 */
class TimelineOrderingTest {

    @Test
    fun `キーは matchClock 優先で videoClock にフォールバックする`() {
        assertEquals(12.0, TimelineOrdering.sortKey(fact(match = 12.0, video = 900.0)), 0.0)
        assertEquals(900.0, TimelineOrdering.sortKey(fact(match = null, video = 900.0)), 0.0)
    }

    @Test
    fun `両方 null のキーは正の無限大`() {
        assertEquals(
            Double.POSITIVE_INFINITY,
            TimelineOrdering.sortKey(fact(match = null, video = null)),
            0.0,
        )
    }

    @Test
    fun `matchClock 昇順に並ぶ`() {
        val a = fact(match = 30.0, video = null)
        val b = fact(match = 10.0, video = null)
        val c = fact(match = 20.0, video = null)
        assertEquals(listOf(b, c, a), TimelineOrdering.sorted(listOf(a, b, c)))
    }

    @Test
    fun `片方だけ videoClock の fact も同じ軸で混ぜて並ぶ`() {
        // matchClock を持つ fact と videoClock しか持たない fact が同居する
        // （動画モードで phase 逆引きに失敗した fact など）。
        val a = fact(match = 30.0, video = null)
        val b = fact(match = null, video = 10.0)
        assertEquals(listOf(b, a), TimelineOrdering.sorted(listOf(a, b)))
    }

    @Test
    fun `時刻の無い fact は捨てずに末尾へ送る`() {
        val a = fact(match = null, video = null)
        val b = fact(match = 5.0, video = null)
        val sorted = TimelineOrdering.sorted(listOf(a, b))
        assertEquals(listOf(b, a), sorted)
        assertEquals(2, sorted.size)
    }

    @Test
    fun `同値は元の index の順を保つ`() {
        val a = fact(match = 10.0, video = null)
        val b = fact(match = 10.0, video = null)
        val c = fact(match = 10.0, video = null)
        assertEquals(listOf(a, b, c), TimelineOrdering.sorted(listOf(a, b, c)))
        assertEquals(listOf(c, b, a), TimelineOrdering.sorted(listOf(c, b, a)))
    }

    @Test
    fun `時刻の無い fact どうしも元の順を保つ`() {
        val a = fact(match = null, video = null)
        val b = fact(match = null, video = null)
        assertEquals(listOf(a, b), TimelineOrdering.sorted(listOf(a, b)))
        assertEquals(listOf(b, a), TimelineOrdering.sorted(listOf(b, a)))
    }

    @Test
    fun `入力のリストは変更しない`() {
        val a = fact(match = 30.0, video = null)
        val b = fact(match = 10.0, video = null)
        val input = listOf(a, b)
        TimelineOrdering.sorted(input)
        assertEquals(listOf(a, b), input)
    }

    /** `id` を毎回変えて、等価な fact どうしでも同一性で並びを判定できるようにする。 */
    private fun fact(match: Double?, video: Double?) = ResolvedFact(
        `fact` = MatchFact(
            `id` = UUID.randomUUID(),
            `recordedAt` = Instant.EPOCH,
            `payload` = MatchFactPayload.Play(
                PlayFact(
                    `kind` = PlayEventKind.GOAL,
                    `anchor` = FactAnchor.MatchClock(MatchClock(match ?: 0.0)),
                ),
            ),
        ),
        `resolvedMatchClock` = match?.let { MatchClock(it) },
        `resolvedVideoClock` = video?.let { VideoClock(it) },
    )
}
