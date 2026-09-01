package com.handplus.handballrecorder.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.handplus.handballrecorder.data.FeedResult
import com.handplus.handballrecorder.data.SampleFeed
import com.handplus.handballrecorder.domain.MatchView
import com.handplus.handballrecorder.domain.loadHighlightView
import com.handplus.handballrecorder.ui.labels.ControlLabel
import com.handplus.handballrecorder.ui.labels.DateLabel
import com.handplus.handballrecorder.ui.labels.displayName
import com.handplus.handballrecorder.ui.playback.Clip
import com.handplus.handballrecorder.ui.playback.ClipProgression
import io.github.kinjoryura.handballtoolkit.FactId
import io.github.kinjoryura.handballtoolkit.MatchFactPayload
import io.github.kinjoryura.handballtoolkit.PlayEventKind
import io.github.kinjoryura.handballtoolkit.PlayerStatLine
import io.github.kinjoryura.handballtoolkit.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ハイライト詳細の状態。試合詳細（[MatchDetailUiState]）と同じ 3 状態。
 *
 * **失敗は分類のまま持つ**（文言に潰さない）。`FeedErrorLabel` が主語つきで日本語にする。
 */
sealed interface HighlightDetailUiState {

    data object Loading : HighlightDetailUiState

    data class Error(val failure: FeedResult.Failure) : HighlightDetailUiState

    data class Ready(val content: HighlightDetailContent) : HighlightDetailUiState
}

/**
 * シーン一覧の 1 行。**全 play fact が 1 行になる**（得点に絞らない）。
 *
 * @property factId 通し再生のクリップと行を結ぶ鍵
 * @property kind 種別チップの元（得点 / シュートミス / メモ …）
 * @property label `#7 安平光佑`。`title` を持つ fact（`freeNote`）は `#7 安平光佑・ナイスパス`
 * @property videoSeconds 動画の位置。**null なら押せない行**（飛び先が無い）
 */
data class HighlightScene(
    val factId: FactId,
    val kind: PlayEventKind,
    val label: String,
    val videoSeconds: Double?,
)

/** 「このハイライトの記録」の 1 行（選手名 + 集計）。 */
data class HighlightPlayerStat(val name: String, val line: PlayerStatLine)

/**
 * 画面が描くのに必要なものを取り出した表示用モデル。
 *
 * **試合詳細（[MatchDetailContent]）とは別の型にしてある。** 兼用にしないのは web デモが
 * 描画を分けているのと同じ理由で、ハイライトには**スコアも両サイド表示も成立しない**
 * （片チームの選手だけを取り上げるのでアウェイ列が常に空、phase を持たないので試合時計も常に空、
 * `summary.homeScore` は試合スコアではなく「このハイライトに写っている得点数」）。
 * 共通の型に寄せると、使わないフィールドを埋める側と無視する側が両方できて事故る。
 *
 * @property videoSource null なら動画枠を出さない。**本文の描画をこれに依存させないこと**
 * @property clips 通し再生のクリップ列。[scenes] のうち動画位置を持つものと 1:1
 */
data class HighlightDetailContent(
    val title: String,
    /** `Ohrid vs Vardar・2026年5月9日`。何の切り抜きかを補う（web デモの `demo-subtitle`）。 */
    val subtitle: String,
    val videoSource: VideoSource?,
    val scenes: List<HighlightScene>,
    val clips: List<Clip>,
    val playerStats: List<HighlightPlayerStat>,
)

/**
 * ハイライト詳細の ViewModel。構造は [MatchDetailViewModel] と同じ
 * （**[MatchView] の所有者はここ**で、`resolver` を [onCleared] で必ず閉じる）。
 *
 * 違いは取得経路だけ — `loadHighlightView` は configuration を `videoHighlight` で
 * 固定した [MatchView] を返す。
 */
class HighlightDetailViewModel(
    private val slug: String,
    private val feed: SampleFeed = SampleFeed(),
) : ViewModel() {

    private val _state = MutableStateFlow<HighlightDetailUiState>(HighlightDetailUiState.Loading)
    val state: StateFlow<HighlightDetailUiState> = _state.asStateFlow()

    /** 開いているハイライト。**close の責任がこの参照にある。** */
    private var matchView: MatchView? = null

    init {
        load()
    }

    /** 「再試行」ボタン。**自動リトライはしない**ので、取り直しはここからだけ。 */
    fun retry() = load()

    private fun load() {
        closeMatchView()
        _state.value = HighlightDetailUiState.Loading
        viewModelScope.launch {
            when (val result = feed.loadHighlightView(slug)) {
                is FeedResult.Success -> {
                    val view = result.value
                    matchView = view
                    val content = withContext(Dispatchers.Default) { buildHighlightContent(view) }
                    _state.value = HighlightDetailUiState.Ready(content)
                }

                is FeedResult.Failure -> _state.value = HighlightDetailUiState.Error(result)
            }
        }
    }

    override fun onCleared() {
        closeMatchView()
        super.onCleared()
    }

    private fun closeMatchView() {
        matchView?.close()
        matchView = null
    }

    companion object {

        /** slug を渡すための factory（[MatchDetailViewModel.factory] と同じ形）。 */
        fun factory(slug: String) = viewModelFactory {
            initializer { HighlightDetailViewModel(slug) }
        }
    }
}

/**
 * [MatchView] → [HighlightDetailContent]。
 *
 * **並びは `MatchView.orderedFacts` のまま**（[com.handplus.handballrecorder.domain.TimelineOrdering]）。
 * あちらのキーは `resolvedMatchClock → resolvedVideoClock → +∞` で、**ハイライトは
 * `resolvedMatchClock` が全 fact で null になる**ので、結果は videoClock 昇順（同値は元 index）
 * に一致する。ここで `sortedBy { videoClock }` を書き直さないこと — 比較子を 2 本持つと、
 * 同じ fact 列が画面ごとに違う順で並ぶ日が来る（`TimelineOrdering` の doc）。
 *
 * **試合詳細と違い `resolver` には触らない。** phase の逆引きが要らない（phase を持たない）ので、
 * FFI 呼び出しはこの関数の中で 1 回も起きない。
 */
internal fun buildHighlightContent(view: MatchView): HighlightDetailContent {
    val playersById = view.playersById
    val scenes = view.orderedFacts.mapNotNull { resolved ->
        val payload = resolved.fact.payload as? MatchFactPayload.Play ?: return@mapNotNull null
        val play = payload.v1
        val name = play.playerId?.let { playersById[it] }?.displayName
        val title = play.title?.takeIf { it.isNotBlank() }
        HighlightScene(
            factId = resolved.fact.id,
            kind = play.kind,
            // 選手名とタイトルは**併記する**（誰のシーンかは種別によらず知りたい情報なので、
            // タイトルがあっても名前を落とさない。web デモの `[name, title].join('・')`）。
            // どちらも無い fact は空欄になると行が壊れて見えるので「不明」に落とす
            // （web デモは空欄のまま。**意図的な差分** — 1 列の行で名前欄だけが消えると
            // チップと時刻の間が抜けて見える）。
            label = listOfNotNull(name, title).joinToString("・").ifEmpty { ControlLabel.UNKNOWN_PLAYER },
            videoSeconds = resolved.resolvedVideoClock?.elapsedSeconds,
        )
    }
    return HighlightDetailContent(
        title = view.match.title?.takeIf { it.isNotBlank() }
            ?: "${view.homeTeam.name} vs ${view.awayTeam.name}",
        subtitle = listOf(
            "${view.homeTeam.name} vs ${view.awayTeam.name}",
            DateLabel.ymd(view.match.date),
        ).joinToString("・"),
        videoSource = view.videoSource,
        scenes = scenes,
        clips = ClipProgression.clips(view.orderedFacts),
        // **チーム別ではなく選手別だけ**（型の doc を参照）。記録のある選手だけに絞るのは
        // web デモの `renderPlayerTable` と同じ（ハイライトに写っていない選手を 0 行で並べない）。
        //
        // 並びは試合詳細と同じ [PlayerStatsOrdering]（得点降順 → 試投降順 → 名前）。
        // web デモは「得点降順 → シュートミス降順」だが、**試投 = 得点 + シュートミスなので
        // 得点が同じ行どうしでは試投順とシュートミス順が一致する** — つまり同じ並びで、
        // 最後の名前順が付くぶんだけ安定する。比較子を 2 本持たない理由はこれ。
        playerStats = PlayerStatsOrdering
            .sorted(view.summary.playerStats.filter { it.goals > 0L || it.shotMisses > 0L }, playersById)
            .map { line ->
                HighlightPlayerStat(
                    name = playersById[line.playerId]?.displayName ?: ControlLabel.UNKNOWN_PLAYER,
                    line = line,
                )
            },
    )
}
