package com.handplus.handballrecorder.ui.playback

import io.github.kinjoryura.handballtoolkit.FactId
import io.github.kinjoryura.handballtoolkit.MatchFactPayload
import io.github.kinjoryura.handballtoolkit.ResolvedFact
import kotlin.math.max

/**
 * 通し再生の 1 シーン分。動画の `[start, end]` 区間と、どの fact のシーンかだけを持つ。
 *
 * 長さは lead-in 4 秒 + tail 2 秒 = **6 秒**（[PlaybackOffsets]）。この 6 秒が
 * [ClipProgression] の重なり判定の前提になっている。
 *
 * @property factId 対応する fact。シーン一覧の行と結びつける鍵（強調表示と行タップの入口）
 * @property startSeconds 動画のこの位置から流す（0 でクランプ済み）
 * @property endSeconds 動画のこの位置を過ぎたら次のクリップへ
 */
data class Clip(
    val factId: FactId,
    val startSeconds: Double,
    val endSeconds: Double,
)

/**
 * 1 tick 分の進行の答え。**副作用は呼び出し側（[ClipPlaybackController]）が起こす。**
 */
sealed interface ClipStep {

    /** 何もしない（ガード窓の中 / まだ現在クリップの末尾に達していない）。 */
    data object Hold : ClipStep

    /**
     * 次のクリップへ進む。
     *
     * @param index 進む先のクリップ（**現在の +1 とは限らない** — 手動で先へ飛ばされていたら
     *   まだ終わっていない最初の後続まで飛ぶ）
     * @param seekSeconds シーク先。**null なら「重なっているのでシークしない」** —
     *   index と強調行だけを進めて、再生はそのまま流し続ける（親リポ #237）
     */
    data class Advance(val index: Int, val seekSeconds: Double?) : ClipStep

    /** 最後のクリップまで見終わった。 */
    data object Finish : ClipStep
}

/**
 * 通し再生の進行判定。**iOS `PlayerShotsPlaybackControllerV2` と web デモ `demo.js` の
 * `playAllTick` の移植で、規則を 1 つも変えていない。**
 *
 * 3 者は「片方を変えたらもう片方も揃える」という取り決めで結ばれている
 * （iOS の `advance(currentVideoTime:)` の doc / web デモ README「通し再生（すべて再生）」）。
 * **ここを直すときは iOS と web デモも直すこと。**
 *
 * 副作用（シーク・一時停止・ポーリング）を持たない純関数にしてあるのは、境界の扱いを
 * 単体テストで固定するため（`ClipProgressionTest`）。実機・エミュレータを起こさずに
 * 「境界ちょうど」「重なり」「手動で飛ばされた」を確かめられる。
 */
object ClipProgression {

    /**
     * シーク直後、ポーリング値を無視する窓（秒）。
     *
     * `seekTo` を投げてもプレイヤーがすぐ反映するとは限らず、その間 `getCurrentTime()` は
     * **前の位置**を返す。素直に信じると「まだ現在クリップの末尾より後ろに居る」と読めてしまい、
     * シークした直後に次のクリップへ誤って進む。iOS の `seekSettleWindowSeconds` /
     * web デモの `SEEK_SETTLE_WINDOW_SECONDS` と同じ 1 秒。
     */
    const val SEEK_SETTLE_WINDOW_SECONDS: Double = 1.0

    /**
     * fact 列 → クリップ列。
     *
     * - 対象は**全 play fact**（得点に絞らない）。ハイライトは記録の過半が `freeNote`
     *   （ナイスパス等）の回もあるため。web デモの `buildClips` と同じ範囲で、
     *   **シーン一覧に出る行とちょうど一致する**（「n / N」と行の 1:1 対応の根拠）。
     *   iOS の `allHighlightsOf` だけは `goal` / `shotMissed` / `freeNote` の 3 種に
     *   絞っているが、**あちらは「すべて再生」が別画面でシーン一覧を持たない**ので
     *   1:1 の要請が無い。カード類（イエロー / 2 分 / レッド）を落とすと、こちらでは
     *   一覧に出ている行が通し再生から静かに消える（**意図的な差分**）
     * - **動画位置を持たない fact は載せない**（飛び先が無い）。シーン一覧には行として出るが、
     *   押せない行になる
     * - 並びは `startSeconds` 昇順。**入力（`orderedFacts`）が既に動画時刻順でも省かない** —
     *   ハイライトの `resolvedMatchClock` は全 fact で null になる前提だが、そうでない
     *   データが来たときに進行判定の前提（昇順）が崩れないようにする
     *
     * **クリップ列はマージしない。** 重なっていても 1 本に畳まないのは、「n / N」の N を
     * 減らさず、シーン一覧の行と 1:1 で対応させ続けるため（iOS / web デモと同じ）。
     */
    fun clips(orderedFacts: List<ResolvedFact>): List<Clip> = orderedFacts
        .mapNotNull { resolved ->
            if (resolved.fact.payload !is MatchFactPayload.Play) return@mapNotNull null
            val video = resolved.resolvedVideoClock ?: return@mapNotNull null
            Clip(
                factId = resolved.fact.id,
                // 開始 4 秒以内のシーンで負の位置を渡さない（プレイヤーによっては
                // 無視されたり末尾へ飛んだりする）。iOS / web デモと同じクランプ。
                startSeconds = max(0.0, video.elapsedSeconds - PlaybackOffsets.PLAYBACK_LEAD_IN_SECONDS),
                endSeconds = video.elapsedSeconds + PlaybackOffsets.PLAYBACK_TAIL_SECONDS,
            )
        }
        // 安定ソート。同じ開始位置（0 クランプで揃うことがある）の並びは入力順のまま。
        .sortedBy { it.startSeconds }

    /**
     * 現在位置を 1 回見て、次に何をするかを決める。
     *
     * 判定の順に:
     *
     * 1. クリップが無い / [index] が範囲外なら [ClipStep.Hold]（呼び出し側の状態が壊れていても落ちない）
     * 2. **シーク直後のガード窓**（[lastSeekSeconds] より [SEEK_SETTLE_WINDOW_SECONDS] 以上手前）
     *    なら [ClipStep.Hold]。まだシークが反映されていない古い値なので使わない
     * 3. 現在クリップの末尾に**達していなければ** [ClipStep.Hold]。判定は `>=` で、
     *    **末尾ちょうどは「進む」**
     * 4. **まだ終わっていない最初の後続クリップまで一度に送る。** 手動で再生位置を先へ
     *    飛ばされると後続がまとめて過去になっていることがあり、それを 250ms ずつ辿らない。
     *    ここの判定が `>` なのは、末尾ちょうど（同時刻の記録で境界が一致する場合）を
     *    「まだ終わっていない」側に入れ、そのクリップも一度は現在クリップとして見せるため
     *    （3 の `>=` と**意図的に非対称**）
     * 5. 後続が尽きたら [ClipStep.Finish]
     * 6. **現在位置が既に次のクリップの中なら、シークしない**（[ClipStep.Advance] の
     *    `seekSeconds` が null）。lead-in 4 秒 + tail 2 秒 = 6 秒なので、6 秒未満の間隔で
     *    記録された 2 件はクリップが重なる。素直に次の頭へ飛ぶと**巻き戻って同じ映像を
     *    二度流す**ことになり、実際には「シュートミスの直後の得点」のように繋がった
     *    1 つのプレーであることが多い（親リポ #237）
     *
     * @param clips [clips] が作ったクリップ列（`startSeconds` 昇順）
     * @param index 現在のクリップ
     * @param currentSeconds ポーリングで読んだ動画の現在位置
     * @param lastSeekSeconds 直近のシーク先。**重なりで進んだ後は null**（シークしていないので
     *   ガード窓の基準を持ち越さない）
     */
    fun step(
        clips: List<Clip>,
        index: Int,
        currentSeconds: Double,
        lastSeekSeconds: Double?,
    ): ClipStep {
        val current = clips.getOrNull(index) ?: return ClipStep.Hold
        if (lastSeekSeconds != null && currentSeconds < lastSeekSeconds - SEEK_SETTLE_WINDOW_SECONDS) {
            return ClipStep.Hold
        }
        if (currentSeconds < current.endSeconds) return ClipStep.Hold

        var next = index + 1
        while (next < clips.size && currentSeconds > clips[next].endSeconds) next += 1
        if (next >= clips.size) return ClipStep.Finish

        val target = clips[next]
        // 重なり: 既に次のクリップの中に居るので、シークせず index と強調行だけ進める。
        val seekSeconds = if (currentSeconds >= target.startSeconds) null else target.startSeconds
        return ClipStep.Advance(index = next, seekSeconds = seekSeconds)
    }
}
