package com.handplus.handballrecorder.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 行タップのシーク位置。**通し再生の lead-in / tail とは別の定数**であることを固定する。
 */
class PlaybackOffsetsTest {

    @Test
    fun `行タップのオフセットは 3 秒`() {
        // iOS `AppConstants.Recording.defaultSeekOffsetSeconds` / web デモ `SEEK_OFFSET_SECONDS`。
        assertEquals(3.0, PlaybackOffsets.SEEK_OFFSET_SECONDS, 0.0)
    }

    @Test
    fun `記録時刻の 3 秒手前へ飛ぶ`() {
        assertEquals(97.0, PlaybackOffsets.seekTarget(100.0), 0.0)
    }

    @Test
    fun `開始直後は 0 でクランプする`() {
        // 負の位置を渡すと、プレイヤーによって無視されたり末尾へ飛んだりする。
        assertEquals(0.0, PlaybackOffsets.seekTarget(2.0), 0.0)
        assertEquals(0.0, PlaybackOffsets.seekTarget(0.0), 0.0)
    }

    @Test
    fun `ちょうど 3 秒は 0 になる`() {
        assertEquals(0.0, PlaybackOffsets.seekTarget(3.0), 0.0)
    }

    @Test
    fun `端数はそのまま残す`() {
        // 丸めはしない（プレイヤー側が秒未満を受け取れる）。
        assertEquals(1.5, PlaybackOffsets.seekTarget(4.5), 1e-9)
    }

    @Test
    fun `通し再生の lead-in は 4 秒 tail は 2 秒`() {
        // iOS `AppConstants.Recording.defaultPlaybackLeadInSeconds` / `defaultPlaybackTailSeconds`、
        // web デモ `PLAYBACK_LEAD_IN_SECONDS` / `PLAYBACK_TAIL_SECONDS`。
        assertEquals(4.0, PlaybackOffsets.PLAYBACK_LEAD_IN_SECONDS, 0.0)
        assertEquals(2.0, PlaybackOffsets.PLAYBACK_TAIL_SECONDS, 0.0)
    }

    @Test
    fun `行タップの 3 秒と通し再生の lead-in は別の値`() {
        // **同じ値にしないこと。**「そのシーンへ飛ぶ」と「名場面を繋いで見る」で必要な
        // 助走が違うので、iOS も web デモも別々の定数として持っている。片方を直したときに
        // もう片方を巻き込んでいないかを、この検査が押さえる。
        assertNotEquals(
            PlaybackOffsets.SEEK_OFFSET_SECONDS,
            PlaybackOffsets.PLAYBACK_LEAD_IN_SECONDS,
        )
    }
}
