package com.handplus.handballrecorder.ui.playback

import kotlin.math.max

/**
 * 動画へ飛ぶときの助走秒数。**用途の違う定数を 1 つにまとめないこと。**
 *
 * ここには**別々に使う 3 つの値**が並んでいる。名前が似ているので、片方を直すときに
 * もう片方を巻き込まないこと:
 *
 * - **行タップ（[SEEK_OFFSET_SECONDS] = 3 秒）** — 記録された瞬間の少し手前から流し、
 *   何が起きたかを見せる。iOS は `AppConstants.Recording.defaultSeekOffsetSeconds`、
 *   web デモは `demo.js` の `SEEK_OFFSET_SECONDS`。iOS だけは設定画面で 0〜10 秒に
 *   変えられる（このアプリと web デモは設定 UI を持たないので既定値で固定）。
 * - **通し再生（[PLAYBACK_LEAD_IN_SECONDS] = 4 秒 / [PLAYBACK_TAIL_SECONDS] = 2 秒）** —
 *   名場面を繋いで流すときの 1 クリップの長さ。iOS は
 *   `AppConstants.Recording.defaultPlaybackLeadInSeconds` / `defaultPlaybackTailSeconds`、
 *   web デモは `PLAYBACK_LEAD_IN_SECONDS` / `PLAYBACK_TAIL_SECONDS`。
 *
 * **なぜ 3 秒と 4 秒を 1 つにまとめてはいけないか。** 「そのシーンへ飛ぶ」と
 * 「名場面を繋いで見る」では必要な助走が違う、というのが iOS も web デモも 3 者を
 * 別々の定数として持っている理由。まとめると、行タップの助走を詰めたい要求
 * （記録直後の場面をすぐ見たい）と、通し再生の助走を伸ばしたい要求（前の局面から
 * 流れで見たい）が衝突して、どちらかが必ず壊れる。加えて通し再生の lead-in は
 * [PLAYBACK_TAIL_SECONDS] と対で「クリップの長さ = 6 秒」を決めており、この 6 秒が
 * 重なり判定（[ClipProgression]）の前提になっている。**片方だけ動かすと重なりの
 * 起きる間隔が変わる。**
 */
object PlaybackOffsets {

    /**
     * 行タップで動画へ飛ぶときに、記録時刻より何秒手前から流すか。
     *
     * iOS / web デモの既定値と同じ 3 秒。変えるなら 3 者を揃えること
     * （挙動を一致させるのがこの値の目的）。
     *
     * **通し再生では使わない**（あちらは [PLAYBACK_LEAD_IN_SECONDS]）。
     */
    const val SEEK_OFFSET_SECONDS: Double = 3.0

    /**
     * 通し再生のクリップが、記録時刻より何秒手前から始まるか（lead-in）。
     *
     * iOS `AppConstants.Recording.defaultPlaybackLeadInSeconds` / web デモ
     * `PLAYBACK_LEAD_IN_SECONDS` と同じ 4 秒。**行タップの [SEEK_OFFSET_SECONDS]
     * とは別物。**
     */
    const val PLAYBACK_LEAD_IN_SECONDS: Double = 4.0

    /**
     * 通し再生のクリップが、記録時刻の何秒後まで続くか（tail）。
     *
     * iOS `AppConstants.Recording.defaultPlaybackTailSeconds` / web デモ
     * `PLAYBACK_TAIL_SECONDS` と同じ 2 秒。
     */
    const val PLAYBACK_TAIL_SECONDS: Double = 2.0

    /**
     * 記録された動画時刻 → 実際にシークする位置。
     *
     * **0 でクランプする。** 開始 3 秒以内のシーンで負の位置を渡すと、プレイヤーによって
     * 無視されたり末尾へ飛んだりする（web デモの `Math.max(0, …)` と同じ）。
     */
    fun seekTarget(videoSeconds: Double): Double = max(0.0, videoSeconds - SEEK_OFFSET_SECONDS)
}
