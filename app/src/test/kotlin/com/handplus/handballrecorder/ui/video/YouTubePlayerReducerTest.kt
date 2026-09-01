package com.handplus.handballrecorder.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * readiness の遷移。**前進のみ**であること、そして
 * **`Positioned` 未満では現在位置を読ませない**ことを固定する。
 *
 * 後者が本題で、cued / unstarted の `getCurrentTime()` が返す 0 を「動画の 0 秒地点」と
 * 誤認する事故（iOS #98）を防ぐための境界。
 */
class YouTubePlayerReducerTest {

    private val initial = YouTubePlayerState()

    private fun stateOf(readiness: PlayerReadiness) = YouTubePlayerState(readiness = readiness)

    @Test
    fun `初期状態は unloaded で位置を読めない`() {
        assertEquals(PlayerReadiness.Unloaded, initial.readiness)
        assertFalse(initial.readiness.allowsPositionReads)
        assertFalse(initial.readiness.isLoaded)
        assertFalse(initial.hasError)
    }

    @Test
    fun `ready で ready へ前進する`() {
        val next = YouTubePlayerReducer.reduce(stateOf(PlayerReadiness.Loading), YouTubeBridgeEvent.Ready)
        assertEquals(PlayerReadiness.Ready, next.readiness)
        // **まだ位置は読ませない。** onReady は「再生できる」であって「着地した」ではない。
        assertFalse(next.readiness.allowsPositionReads)
        assertTrue(next.readiness.isLoaded)
    }

    @Test
    fun `再生とバッファで positioned へ前進する`() {
        for (state in listOf(1, 3)) {
            val next = YouTubePlayerReducer.reduce(
                stateOf(PlayerReadiness.Ready),
                YouTubeBridgeEvent.StateChange(state),
            )
            assertEquals(PlayerReadiness.Positioned, next.readiness)
            assertTrue(next.readiness.allowsPositionReads)
        }
    }

    @Test
    fun `cued や一時停止では前進しない`() {
        // -1 unstarted / 0 ended / 2 paused / 5 cued。ここで positioned にすると
        // 「まだどこにも居ない 0 秒」を位置として読ませてしまう。
        for (state in listOf(-1, 0, 2, 5)) {
            val next = YouTubePlayerReducer.reduce(
                stateOf(PlayerReadiness.Ready),
                YouTubeBridgeEvent.StateChange(state),
            )
            assertEquals(PlayerReadiness.Ready, next.readiness)
            assertFalse(next.readiness.allowsPositionReads)
        }
    }

    @Test
    fun `一度着地したら cued が来ても維持する`() {
        val next = YouTubePlayerReducer.reduce(
            stateOf(PlayerReadiness.Positioned),
            YouTubeBridgeEvent.StateChange(5),
        )
        assertEquals(PlayerReadiness.Positioned, next.readiness)
    }

    @Test
    fun `遅れて来た ready で後退しない`() {
        val next = YouTubePlayerReducer.reduce(
            stateOf(PlayerReadiness.Positioned),
            YouTubeBridgeEvent.Ready,
        )
        assertEquals(PlayerReadiness.Positioned, next.readiness)
    }

    @Test
    fun `再生中フラグは state 1 のときだけ真`() {
        val playing = YouTubePlayerReducer.reduce(initial, YouTubeBridgeEvent.StateChange(1))
        assertTrue(playing.isPlaying)
        val paused = YouTubePlayerReducer.reduce(playing, YouTubeBridgeEvent.StateChange(2))
        assertFalse(paused.isPlaying)
        // 一時停止しても着地は維持する。
        assertEquals(PlayerReadiness.Positioned, paused.readiness)
    }

    @Test
    fun `error は前進を追い越して入る`() {
        val next = YouTubePlayerReducer.reduce(
            stateOf(PlayerReadiness.Positioned),
            YouTubeBridgeEvent.Error(150),
        )
        assertEquals(PlayerReadiness.Error(150), next.readiness)
        assertTrue(next.hasError)
        assertFalse(next.isPlaying)
        assertFalse(next.readiness.allowsPositionReads)
    }

    @Test
    fun `error は sticky で前進では解けない`() {
        val errored = stateOf(PlayerReadiness.Error(150))
        assertEquals(errored, YouTubePlayerReducer.reduce(errored, YouTubeBridgeEvent.Ready))
        assertEquals(
            PlayerReadiness.Error(150),
            YouTubePlayerReducer.reduce(errored, YouTubeBridgeEvent.StateChange(1)).readiness,
        )
        assertEquals(
            PlayerReadiness.Error(150),
            YouTubePlayerReducer.advanced(errored, PlayerReadiness.Positioned).readiness,
        )
    }

    @Test
    fun `error を解けるのは読み直しだけ`() {
        val errored = stateOf(PlayerReadiness.Error(150))
        val reloaded = YouTubePlayerReducer.reset(errored, PlayerReadiness.Loading)
        assertEquals(PlayerReadiness.Loading, reloaded.readiness)
        assertFalse(reloaded.hasError)
    }

    @Test
    fun `読み直しは着地を取り消す`() {
        // 動画を差し替えたら位置は未着地に戻す（前の動画の位置を引き継がない）。
        val reset = YouTubePlayerReducer.reset(
            YouTubePlayerState(PlayerReadiness.Positioned, isPlaying = true),
            PlayerReadiness.Ready,
        )
        assertEquals(PlayerReadiness.Ready, reset.readiness)
        assertFalse(reset.isPlaying)
        assertFalse(reset.readiness.allowsPositionReads)
    }

    @Test
    fun `再生速度の通知では状態が変わらない`() {
        val state = stateOf(PlayerReadiness.Ready)
        assertEquals(state, YouTubePlayerReducer.reduce(state, YouTubeBridgeEvent.PlaybackRateChange(2.0)))
    }

    @Test
    fun `前進の順序は unloaded から positioned まで`() {
        val order = listOf(
            PlayerReadiness.Unloaded,
            PlayerReadiness.Loading,
            PlayerReadiness.Ready,
            PlayerReadiness.Positioned,
        )
        for (i in 0 until order.size - 1) {
            assertTrue(order[i].progressRank < order[i + 1].progressRank)
            // 後ろから前へは戻れない。
            assertEquals(
                order[i + 1],
                YouTubePlayerReducer.advanced(stateOf(order[i + 1]), order[i]).readiness,
            )
        }
        // error は前進の枠外。
        assertTrue(PlayerReadiness.Error(null).progressRank < PlayerReadiness.Unloaded.progressRank)
    }
}
