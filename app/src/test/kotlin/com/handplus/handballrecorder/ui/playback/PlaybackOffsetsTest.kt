package com.handplus.handballrecorder.ui.playback

import org.junit.Assert.assertEquals
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
}
