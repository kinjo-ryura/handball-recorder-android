package com.handplus.handballrecorder.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.handplus.handballrecorder.ui.video.YouTubePlayerController
import io.github.kinjoryura.handballtoolkit.FactId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 通し再生が動画へ出す指示の口。**[YouTubePlayerController] そのものに依存しないための境界。**
 *
 * 使うのは `YT.Player` の公式メソッドに 1:1 で対応する 3 つだけ（`seekTo` + `playVideo` /
 * `pauseVideo` / `getCurrentTime`）。README「YouTube 連携（RMF）」の禁止事項に触れる操作は
 * ここに増やさないこと。
 */
interface ClipPlaybackTarget {

    /** その位置へ飛んで再生する（[YouTubePlayerController.seek] と同じく seek + play）。 */
    fun seek(seconds: Double)

    /** 一時停止する。**通し再生が最後まで終わったときだけ**呼ばれる。 */
    fun pause()

    /** 現在位置（秒）。位置が着地していなければ null。 */
    suspend fun currentTimeSeconds(): Double?
}

/** 動画を持たない画面・プレビュー用。何もしない。 */
object NoClipPlaybackTarget : ClipPlaybackTarget {
    override fun seek(seconds: Double) = Unit
    override fun pause() = Unit
    override suspend fun currentTimeSeconds(): Double? = null
}

/** [YouTubePlayerController] を [ClipPlaybackTarget] として見る。 */
fun YouTubePlayerController.asClipPlaybackTarget(): ClipPlaybackTarget {
    val player = this
    return object : ClipPlaybackTarget {
        override fun seek(seconds: Double) = player.seek(seconds)
        override fun pause() = player.pause()
        override suspend fun currentTimeSeconds(): Double? = player.currentTimeSeconds()
    }
}

/**
 * 通し再生の状態。画面はこれだけを見て描く。
 *
 * @property clips 再生対象のクリップ列（[ClipProgression.clips] の結果）
 * @property index 現在のクリップ。**「n / N」の n は `index + 1`**
 * @property isPlaying 通し再生中か。**動画が再生中かではない**（利用者がプレイヤーの
 *   コントロールで止めても通し再生の状態は変わらない — 位置が進まなくなるだけ）
 */
data class ClipPlaybackState(
    val clips: List<Clip> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
) {

    /** 強調表示する行。通し再生中でなければ null（＝どの行も強調しない）。 */
    val playingFactId: FactId? get() = if (isPlaying) clips.getOrNull(index)?.factId else null
}

/**
 * 通し再生（ハイライトの「すべて再生」）の駆動。
 *
 * 進行の**判定**は [ClipProgression]（純関数）が持ち、このクラスが持つのは
 * **ポーリングと副作用**（シーク・一時停止・状態の配布）だけ。iOS
 * `PlayerShotsPlaybackControllerV2` の `tick(currentVideoTime:)` を外から駆動する形と
 * 同じ分け方で、あちらの `clockPollTask` に当たるのが [start] が起こすループ。
 *
 * ## 停止しても動画は止めない
 *
 * 「停止」（[stop]）はポーリングを止めるだけで、**一時停止するのは最後まで見終わったとき
 * だけ**（[ClipStep.Finish] → [ClipPlaybackTarget.pause]）。web デモの `stopPlayAll` /
 * `finishPlayAll` と同じで、行タップで通し再生から抜けたときに再生が二重に操作されるのを
 * 避けるため。**iOS は画面を閉じるので `stop()` でも一時停止するが、こちらは同じ画面に
 * プレイヤーが残るので web デモに合わせてある**（意図的な差分）。
 *
 * ## スレッド
 *
 * **main スレッドからのみ使うこと。** [scope] は Compose の `rememberCoroutineScope`
 * （= Main）を想定していて、`seek` / `pause` は内部で main へ寄せるが、この
 * クラス自身の可変状態（[lastSeekSeconds] / [pollJob]）は寄せていない。
 */
@Stable
class ClipPlaybackController(
    private val target: ClipPlaybackTarget,
    private val scope: CoroutineScope,
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
) {

    private val _state = MutableStateFlow(ClipPlaybackState())

    /** 画面が購読する状態。 */
    val state: StateFlow<ClipPlaybackState> = _state.asStateFlow()

    /**
     * 直近のシーク先。**ガード窓（[ClipProgression.SEEK_SETTLE_WINDOW_SECONDS]）の基準。**
     * 重なりで進んだときは null に戻す（シークしていないので基準を持ち越さない）。
     */
    private var lastSeekSeconds: Double? = null

    private var pollJob: Job? = null

    /**
     * 対象のクリップ列を差し替える（読み込み完了 / 再試行）。
     *
     * **差し替え前に必ず止める。** 前の対象を指したままポーリングが回り続けると、
     * 消えた行を強調しようとする（web デモの `render` が先頭で `stopPlayAll()` を
     * 呼んでいるのと同じ理由）。
     */
    fun setClips(clips: List<Clip>) {
        if (_state.value.clips == clips) return
        stop()
        _state.value = ClipPlaybackState(clips = clips)
    }

    /** ボタン 1 つで開始 / 停止を切り替える。 */
    fun toggle() {
        if (_state.value.isPlaying) stop() else start()
    }

    /**
     * [fromIndex] のクリップから通し再生を始める。
     *
     * ボタンは先頭（0）から、シーン一覧の行タップはその行の位置から呼ぶ
     * （**行タップの入口は web デモと同じ**。違いは、こちらは通し再生中の行タップだけが
     * ここへ来ることで、止まっているときの行タップは単発シークになる）。
     *
     * @return 開始できたか（クリップが無い / 範囲外なら false）
     */
    fun start(fromIndex: Int = 0): Boolean {
        val clips = _state.value.clips
        if (fromIndex !in clips.indices) return false
        _state.update { it.copy(index = fromIndex, isPlaying = true) }
        seekToCurrent()
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMillis)
                tick()
            }
        }
        return true
    }

    /**
     * 通し再生をやめる（利用者の停止操作 / 画面を離れる / 対象の差し替え）。
     * **動画は止めない**（クラスの doc を参照）。冪等。
     */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        lastSeekSeconds = null
        _state.update { it.copy(isPlaying = false) }
    }

    /** 最後のクリップまで見終わった。ここでだけ動画を一時停止する。 */
    private fun finish() {
        stop()
        target.pause()
    }

    /**
     * 1 tick。現在位置を 1 回読み、[ClipProgression.step] の答えを実行するだけ。
     *
     * 位置が読めない（まだ着地していない / 破棄済み）ときは**何もしない** —
     * 0 を「動画の 0 秒地点」と読むと通し再生が先頭へ巻き戻る（iOS #98）。
     */
    private suspend fun tick() {
        val snapshot = _state.value
        if (!snapshot.isPlaying) return
        val currentSeconds = target.currentTimeSeconds() ?: return
        when (val step = ClipProgression.step(snapshot.clips, snapshot.index, currentSeconds, lastSeekSeconds)) {
            is ClipStep.Hold -> Unit

            is ClipStep.Finish -> finish()

            is ClipStep.Advance -> {
                _state.update { it.copy(index = step.index) }
                val seekSeconds = step.seekSeconds
                if (seekSeconds == null) {
                    // 重なり。シークしていないのでガード窓の基準も持ち越さない。
                    lastSeekSeconds = null
                } else {
                    lastSeekSeconds = seekSeconds
                    target.seek(seekSeconds)
                }
            }
        }
    }

    private fun seekToCurrent() {
        val snapshot = _state.value
        val clip = snapshot.clips.getOrNull(snapshot.index) ?: return
        lastSeekSeconds = clip.startSeconds
        target.seek(clip.startSeconds)
    }

    companion object {

        /**
         * 再生位置を見る間隔（ミリ秒）。
         *
         * iOS `PlayerShotsPlaybackViewV2` の `clockPollTask` / web デモの
         * `PLAYBACK_POLL_MS` と同じ 250ms。**短くしても滑らかにはならない**
         * （クリップの境界は秒単位）し、1 回ごとに `evaluateJavascript` の往復が要る。
         */
        const val POLL_INTERVAL_MILLIS: Long = 250L
    }
}

/**
 * 画面のライフサイクルに紐づく [ClipPlaybackController] を用意する。
 *
 * ポーリングのループは `rememberCoroutineScope` に属するので、**画面を離れれば自動で止まる**。
 * それとは別に `onDispose` でも [ClipPlaybackController.stop] を呼ぶ（状態を残さないため）。
 *
 * @param player 動画を持たない経路・プレビューでは null。その場合 [NoClipPlaybackTarget] が入り、
 *   すべての操作が no-op になる
 */
@Composable
fun rememberClipPlaybackController(player: YouTubePlayerController?): ClipPlaybackController {
    val scope = rememberCoroutineScope()
    val controller = remember(player, scope) {
        ClipPlaybackController(
            target = player?.asClipPlaybackTarget() ?: NoClipPlaybackTarget,
            scope = scope,
        )
    }
    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }
    return controller
}
