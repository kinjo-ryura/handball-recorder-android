package com.handplus.handballrecorder.db

import io.github.kinjoryura.handballtoolkit.ControlFact
import io.github.kinjoryura.handballtoolkit.FactAnchor
import io.github.kinjoryura.handballtoolkit.MatchClock
import io.github.kinjoryura.handballtoolkit.MatchFact
import io.github.kinjoryura.handballtoolkit.MatchFactPayload
import io.github.kinjoryura.handballtoolkit.PhaseKind
import io.github.kinjoryura.handballtoolkit.PhaseStartPayload
import io.github.kinjoryura.handballtoolkit.PlayEventKind
import io.github.kinjoryura.handballtoolkit.PlayFact
import io.github.kinjoryura.handballtoolkit.PossessionFact
import io.github.kinjoryura.handballtoolkit.StoppageKind
import io.github.kinjoryura.handballtoolkit.StoppagePayload
import io.github.kinjoryura.handballtoolkit.VideoClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * `MatchFact` ↔ `FactRow` の往復。**sum type をどう平坦化するかがシェル契約の一部**なので、
 * payload の 4 種すべてについて「入れた値がそのまま戻る」ことを固定する。
 *
 * とくに possession の `endAnchor` は任意フィールドで、**書き忘れても読み忘れても
 * null で素通りしてしまう**（ポゼッションは endAnchor 無しでも正当なため）。
 * 往復で捕まえるほかない。
 */
class DomainMappingTest {

    private val matchId = UUID.fromString("00000000-0000-4000-8000-0000000000aa")
    private val teamId = UUID.fromString("00000000-0000-4000-8000-0000000000bb")
    private val playerId = UUID.fromString("00000000-0000-4000-8000-0000000000cc")

    // ── possession ──

    @Test
    fun `点として記録された possession は endAnchor が null のまま往復する`() {
        val fact = possession(endAnchor = null)

        val row = fact.toRow(matchId)
        assertNull(row.endAnchorKind)
        assertNull(row.endMatchSeconds)
        assertNull(row.endVideoSeconds)

        assertEquals(fact, row.toDomain())
    }

    @Test
    fun `matchClock の endAnchor を持つ possession が往復する`() {
        val fact = possession(endAnchor = FactAnchor.MatchClock(MatchClock(312.5)))

        val row = fact.toRow(matchId)
        assertEquals("MATCH_CLOCK", row.endAnchorKind)
        assertEquals(312.5, row.endMatchSeconds!!, 0.0)
        assertNull(row.endVideoSeconds)

        assertEquals(fact, row.toDomain())
    }

    @Test
    fun `videoClock の endAnchor を持つ possession が往復する`() {
        val fact = possession(endAnchor = FactAnchor.VideoClock(VideoClock(931.25)))

        val row = fact.toRow(matchId)
        assertEquals("VIDEO_CLOCK", row.endAnchorKind)
        assertNull(row.endMatchSeconds)
        assertEquals(931.25, row.endVideoSeconds!!, 0.0)

        assertEquals(fact, row.toDomain())
    }

    @Test
    fun `both の endAnchor を持つ possession が往復する`() {
        val fact = possession(
            endAnchor = FactAnchor.Both(
                matchClock = MatchClock(312.5),
                videoClock = VideoClock(931.25),
            ),
        )

        val row = fact.toRow(matchId)
        assertEquals("BOTH", row.endAnchorKind)
        assertEquals(312.5, row.endMatchSeconds!!, 0.0)
        assertEquals(931.25, row.endVideoSeconds!!, 0.0)

        assertEquals(fact, row.toDomain())
    }

    // ── 他の payload（endAnchor の扱いを変えた巻き添えが無いこと）──

    @Test
    fun `play が往復する`() {
        val fact = fact(
            MatchFactPayload.Play(
                PlayFact(
                    kind = PlayEventKind.GOAL,
                    teamId = teamId,
                    playerId = playerId,
                    relatedPlayerId = null,
                    anchor = FactAnchor.VideoClock(VideoClock(120.0)),
                    title = null,
                    note = "速攻",
                ),
            ),
        )

        val row = fact.toRow(matchId)
        // play は区間を持たないので end 列は常に null。
        assertNull(row.endAnchorKind)

        assertEquals(fact, row.toDomain())
    }

    @Test
    fun `phaseStart が往復する`() {
        val fact = fact(
            MatchFactPayload.Control(
                ControlFact.PhaseStart(
                    PhaseStartPayload(
                        kind = PhaseKind.REGULAR,
                        startAnchor = FactAnchor.MatchClock(MatchClock(0.0)),
                        endAnchor = FactAnchor.MatchClock(MatchClock(1800.0)),
                    ),
                ),
            ),
        )

        assertEquals(fact, fact.toRow(matchId).toDomain())
    }

    @Test
    fun `endAnchor を持たない stoppage が往復する`() {
        val fact = fact(
            MatchFactPayload.Control(
                ControlFact.Stoppage(
                    StoppagePayload(
                        kind = StoppageKind.TIMEOUT,
                        startAnchor = FactAnchor.MatchClock(MatchClock(600.0)),
                        endAnchor = null,
                        note = null,
                    ),
                ),
            ),
        )

        assertEquals(fact, fact.toRow(matchId).toDomain())
    }

    @Test
    fun `endAnchor を持つ stoppage が往復する`() {
        val fact = fact(
            MatchFactPayload.Control(
                ControlFact.Stoppage(
                    StoppagePayload(
                        kind = StoppageKind.TIMEOUT,
                        startAnchor = FactAnchor.MatchClock(MatchClock(600.0)),
                        endAnchor = FactAnchor.MatchClock(MatchClock(660.0)),
                        note = "ホームのチームタイムアウト",
                    ),
                ),
            ),
        )

        assertEquals(fact, fact.toRow(matchId).toDomain())
    }

    // ── 小道具 ──

    private fun possession(endAnchor: FactAnchor?) = fact(
        MatchFactPayload.Possession(
            PossessionFact(
                teamId = teamId,
                anchor = FactAnchor.MatchClock(MatchClock(300.0)),
                endAnchor = endAnchor,
            ),
        ),
    )

    /** `recordedAt` はナノ秒まで入れる（2 列に分けた意味がここで効く）。 */
    private fun fact(payload: MatchFactPayload) = MatchFact(
        id = UUID.fromString("00000000-0000-4000-8000-0000000000dd"),
        recordedAt = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L),
        payload = payload,
    )
}
