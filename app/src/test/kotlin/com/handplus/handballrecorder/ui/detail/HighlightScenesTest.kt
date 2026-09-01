package com.handplus.handballrecorder.ui.detail

import com.handplus.handballrecorder.ui.playback.ClipPlaybackState
import com.handplus.handballrecorder.ui.playback.ClipProgression
import io.github.kinjoryura.handballtoolkit.ControlFact
import io.github.kinjoryura.handballtoolkit.FactAnchor
import io.github.kinjoryura.handballtoolkit.MatchClock
import io.github.kinjoryura.handballtoolkit.MatchFact
import io.github.kinjoryura.handballtoolkit.MatchFactPayload
import io.github.kinjoryura.handballtoolkit.PhaseKind
import io.github.kinjoryura.handballtoolkit.PhaseStartPayload
import io.github.kinjoryura.handballtoolkit.PlayEventKind
import io.github.kinjoryura.handballtoolkit.PlayFact
import io.github.kinjoryura.handballtoolkit.Player
import io.github.kinjoryura.handballtoolkit.PlayerId
import io.github.kinjoryura.handballtoolkit.ResolvedFact
import io.github.kinjoryura.handballtoolkit.VideoClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * シーン一覧の行と通し再生のクリップの対応。
 *
 * **一覧は全 play fact / 通し再生は goal・shotMissed・freeNote の 3 種**なので、
 * 両者の件数は食い違う。ここで固定するのはその食い違いを前提にした 2 点:
 *
 * - 対象外の行（カード類）は一覧に残るが `isPlaybackTarget` が false になる
 *   （画面はこれを ▶ の有無に落とし、「N シーン」の N はクリップ数を使う）
 * - **強調行とクリップ `index` の対応は `factId` 経由で崩れない**（行番号で引くと
 *   カードの数だけずれる）
 */
class HighlightScenesTest {

    // ── 一覧に出す範囲 ──

    @Test
    fun `カード類も行としては出す`() {
        // 記録された事実を一覧から落とすと「アプリが取りこぼした」ように見える。
        val facts = listOf(
            play(video = 10.0, kind = PlayEventKind.GOAL),
            play(video = 20.0, kind = PlayEventKind.YELLOW_CARD),
            play(video = 30.0, kind = PlayEventKind.TWO_MINUTE_SUSPENSION),
            play(video = 40.0, kind = PlayEventKind.RED_CARD),
        )
        val scenes = buildHighlightScenes(facts, emptyMap(), ClipProgression.clips(facts))
        assertEquals(4, scenes.size)
        assertEquals(
            listOf(
                PlayEventKind.GOAL,
                PlayEventKind.YELLOW_CARD,
                PlayEventKind.TWO_MINUTE_SUSPENSION,
                PlayEventKind.RED_CARD,
            ),
            scenes.map { it.kind },
        )
    }

    @Test
    fun `play 以外の fact は行にしない`() {
        val facts = listOf(phaseStart(video = 0.0), play(video = 10.0))
        val scenes = buildHighlightScenes(facts, emptyMap(), ClipProgression.clips(facts))
        assertEquals(1, scenes.size)
        assertEquals(PlayEventKind.GOAL, scenes[0].kind)
    }

    // ── 通し再生の対象かどうか ──

    @Test
    fun `3 種の行は通し再生の対象になる`() {
        val facts = listOf(
            play(video = 10.0, kind = PlayEventKind.GOAL),
            play(video = 20.0, kind = PlayEventKind.SHOT_MISSED),
            play(video = 30.0, kind = PlayEventKind.FREE_NOTE),
        )
        val scenes = buildHighlightScenes(facts, emptyMap(), ClipProgression.clips(facts))
        assertTrue(scenes.all { it.isPlaybackTarget })
    }

    @Test
    fun `カード類の行は通し再生の対象にならない`() {
        // ▶ を出さない根拠。押せば単発シークはするので `videoSeconds` は残る。
        val facts = listOf(
            play(video = 10.0, kind = PlayEventKind.YELLOW_CARD),
            play(video = 20.0, kind = PlayEventKind.TWO_MINUTE_SUSPENSION),
            play(video = 30.0, kind = PlayEventKind.RED_CARD),
        )
        val scenes = buildHighlightScenes(facts, emptyMap(), ClipProgression.clips(facts))
        assertTrue(scenes.none { it.isPlaybackTarget })
        assertEquals(listOf(10.0, 20.0, 30.0), scenes.map { it.videoSeconds })
    }

    @Test
    fun `動画位置を持たない行も対象にならない`() {
        // 飛び先が無いのでクリップにならない（種別は 3 種のうちの 1 つでも同じ）。
        val facts = listOf(play(video = null, kind = PlayEventKind.GOAL))
        val scenes = buildHighlightScenes(facts, emptyMap(), ClipProgression.clips(facts))
        assertEquals(1, scenes.size)
        assertFalse(scenes[0].isPlaybackTarget)
        assertNull(scenes[0].videoSeconds)
    }

    // ── 行数とクリップ数が食い違うとき ──

    @Test
    fun `N はクリップ数で一覧の行数と一致しない`() {
        val facts = mixedFacts()
        val clips = ClipProgression.clips(facts)
        val scenes = buildHighlightScenes(facts, emptyMap(), clips)
        // 「すべて再生（3 シーン）」なのに一覧は 5 行。**これが正しい状態**。
        assertEquals(5, scenes.size)
        assertEquals(3, clips.size)
        assertEquals(3, scenes.count { it.isPlaybackTarget })
    }

    @Test
    fun `クリップ index から強調行を factId で引き当てられる`() {
        val facts = mixedFacts()
        val clips = ClipProgression.clips(facts)
        val scenes = buildHighlightScenes(facts, emptyMap(), clips)
        // 行番号は 0 / 2 / 4。**クリップ index（0 / 1 / 2）とは一致しない** — 行番号で
        // 引き当てるとカードの行を強調してしまう。
        assertEquals(listOf(0, 2, 4), clips.map { scenes.indexOfFact(it.factId) })
        // 引き当てた行はどれも通し再生の対象。
        assertTrue(clips.all { scenes[scenes.indexOfFact(it.factId)!!].isPlaybackTarget })
    }

    @Test
    fun `再生中の状態から強調行を引き当てても崩れない`() {
        val facts = mixedFacts()
        val clips = ClipProgression.clips(facts)
        val scenes = buildHighlightScenes(facts, emptyMap(), clips)
        // 画面が実際に通す経路（ClipPlaybackState.playingFactId → indexOfFact）で確かめる。
        val rows = clips.indices.map { index ->
            val state = ClipPlaybackState(clips = clips, index = index, isPlaying = true)
            scenes.indexOfFact(state.playingFactId)
        }
        assertEquals(listOf(0, 2, 4), rows)
    }

    @Test
    fun `停止中はどの行も強調しない`() {
        val facts = mixedFacts()
        val clips = ClipProgression.clips(facts)
        val scenes = buildHighlightScenes(facts, emptyMap(), clips)
        val state = ClipPlaybackState(clips = clips, index = 1, isPlaying = false)
        assertNull(scenes.indexOfFact(state.playingFactId))
    }

    @Test
    fun `知らない factId は行番号に化けない`() {
        // `indexOfFirst` の -1 をそのまま返すと、先頭行が強調されたりスクロールが暴れる。
        val facts = mixedFacts()
        val scenes = buildHighlightScenes(facts, emptyMap(), ClipProgression.clips(facts))
        assertNull(scenes.indexOfFact(UUID.randomUUID()))
        assertNull(scenes.indexOfFact(null))
    }

    // ── 行の文言 ──

    @Test
    fun `選手名とタイトルは併記する`() {
        val playerId = UUID.randomUUID()
        val fact = play(video = 10.0, kind = PlayEventKind.FREE_NOTE, playerId = playerId, title = "ナイスパス")
        val players: Map<PlayerId, Player> = mapOf(
            playerId to Player(`id` = playerId, `teamId` = UUID.randomUUID(), `name` = "安平光佑"),
        )
        val scenes = buildHighlightScenes(listOf(fact), players, ClipProgression.clips(listOf(fact)))
        assertEquals("安平光佑・ナイスパス", scenes[0].label)
    }

    // ── ヘルパ ──

    /** 得点 / イエロー / シュートミス / レッド / メモ の 5 行（= クリップは 3 本）。 */
    private fun mixedFacts(): List<ResolvedFact> = listOf(
        play(video = 10.0, kind = PlayEventKind.GOAL, id = FIXED_IDS[0]),
        play(video = 20.0, kind = PlayEventKind.YELLOW_CARD, id = FIXED_IDS[1]),
        play(video = 30.0, kind = PlayEventKind.SHOT_MISSED, id = FIXED_IDS[2]),
        play(video = 40.0, kind = PlayEventKind.RED_CARD, id = FIXED_IDS[3]),
        play(video = 50.0, kind = PlayEventKind.FREE_NOTE, id = FIXED_IDS[4]),
    )

    private fun play(
        video: Double?,
        kind: PlayEventKind = PlayEventKind.GOAL,
        playerId: PlayerId? = null,
        title: String? = null,
        id: UUID = UUID.randomUUID(),
    ) = ResolvedFact(
        `fact` = MatchFact(
            `id` = id,
            `recordedAt` = Instant.EPOCH,
            `payload` = MatchFactPayload.Play(
                PlayFact(
                    `kind` = kind,
                    `playerId` = playerId,
                    `anchor` = FactAnchor.VideoClock(VideoClock(video ?: 0.0)),
                    `title` = title,
                ),
            ),
        ),
        // ハイライトは phase を持たないので試合時計は常に null。
        `resolvedMatchClock` = null,
        `resolvedVideoClock` = video?.let { VideoClock(it) },
    )

    private fun phaseStart(video: Double) = ResolvedFact(
        `fact` = MatchFact(
            `id` = UUID.randomUUID(),
            `recordedAt` = Instant.EPOCH,
            `payload` = MatchFactPayload.Control(
                ControlFact.PhaseStart(
                    PhaseStartPayload(
                        `kind` = PhaseKind.REGULAR,
                        `startAnchor` = FactAnchor.VideoClock(VideoClock(video)),
                        `endAnchor` = FactAnchor.VideoClock(VideoClock(video + 1800.0)),
                    ),
                ),
            ),
        ),
        `resolvedMatchClock` = MatchClock(0.0),
        `resolvedVideoClock` = VideoClock(video),
    )

    private companion object {

        /** `mixedFacts()` を 2 回呼んでも同じ fact になるように id を固定する。 */
        val FIXED_IDS: List<UUID> = List(5) { UUID.randomUUID() }
    }
}
