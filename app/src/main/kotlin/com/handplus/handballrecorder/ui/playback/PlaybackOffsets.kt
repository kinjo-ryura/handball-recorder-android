package com.handplus.handballrecorder.ui.playback

import kotlin.math.max

/**
 * 動画へ飛ぶときの助走秒数。**用途の違う定数を 1 つにまとめないこと。**
 *
 * ここが持つのは「行タップでそのシーンへ飛ぶ」ときの [SEEK_OFFSET_SECONDS]（3 秒）だけで、
 * ハイライトの**通し再生**が使う lead-in 4 秒 / tail 2 秒とは**別物**。混ぜてはいけない。
 *
 * - **行タップ（3 秒）** — 記録された瞬間の少し手前から流し、何が起きたかを見せる。
 *   iOS は `AppConstants.Recording.defaultSeekOffsetSeconds`、web デモは `demo.js` の
 *   `SEEK_OFFSET_SECONDS`。iOS だけは設定画面で 0〜10 秒に変えられる（このアプリと
 *   web デモは設定 UI を持たないので既定値で固定）。
 * - **通し再生（lead-in 4 秒 / tail 2 秒）** — 名場面を繋いで流すときのクリップ長。
 *   iOS は `AppConstants.Recording.defaultPlaybackLeadInSeconds` /
 *   `defaultPlaybackTailSeconds`、web デモは `PLAYBACK_LEAD_IN_SECONDS` /
 *   `PLAYBACK_TAIL_SECONDS`。**このチャンクでは使わない**（ハイライト詳細と一緒に入る）。
 *
 * iOS も web デモも 3 者を別々の定数として持っている。「そのシーンへ飛ぶ」と
 * 「名場面を繋いで見る」で必要な助走が違う、というのが分けてある理由なので、
 * 片方を変えるときにもう片方を巻き込まないこと。
 */
object PlaybackOffsets {

    /**
     * 行タップで動画へ飛ぶときに、記録時刻より何秒手前から流すか。
     *
     * iOS / web デモの既定値と同じ 3 秒。変えるなら 3 者を揃えること
     * （挙動を一致させるのがこの値の目的）。
     */
    const val SEEK_OFFSET_SECONDS: Double = 3.0

    /**
     * 記録された動画時刻 → 実際にシークする位置。
     *
     * **0 でクランプする。** 開始 3 秒以内のシーンで負の位置を渡すと、プレイヤーによって
     * 無視されたり末尾へ飛んだりする（web デモの `Math.max(0, …)` と同じ）。
     */
    fun seekTarget(videoSeconds: Double): Double = max(0.0, videoSeconds - SEEK_OFFSET_SECONDS)
}
