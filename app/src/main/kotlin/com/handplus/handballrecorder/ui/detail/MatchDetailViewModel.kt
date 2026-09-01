package com.handplus.handballrecorder.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.handplus.handballrecorder.data.FeedResult
import com.handplus.handballrecorder.data.SampleFeed
import com.handplus.handballrecorder.domain.MatchView
import com.handplus.handballrecorder.domain.loadMatchView
import com.handplus.handballrecorder.ui.labels.PhaseLabel
import io.github.kinjoryura.handballtoolkit.PhaseSummaryLine
import io.github.kinjoryura.handballtoolkit.Player
import io.github.kinjoryura.handballtoolkit.PlayerId
import io.github.kinjoryura.handballtoolkit.PlayerStatLine
import io.github.kinjoryura.handballtoolkit.TeamId
import io.github.kinjoryura.handballtoolkit.TeamSummaryLine
import io.github.kinjoryura.handballtoolkit.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * 試合詳細の状態。
 *
 * **失敗は分類のまま持つ**（文言に潰さない）。`FeedErrorLabel` が主語つきで日本語にする。
 */
sealed interface MatchDetailUiState {

    data object Loading : MatchDetailUiState

    data class Error(val failure: FeedResult.Failure) : MatchDetailUiState

    data class Ready(val content: MatchDetailContent) : MatchDetailUiState
}

/** 前後半別スタッツの 1 ブロック（見出し + 集計行）。 */
data class PhaseStatBlock(val label: String, val line: PhaseSummaryLine)

/**
 * 画面が描くのに必要なものを取り出した表示用モデル。
 *
 * **[MatchView] そのものを画面へ渡さない。** `resolver` はネイティブのハンドルで、
 * 画面が触れると FFI 呼び出しが再コンポーズのたびに走る。phase の逆引きは
 * ViewModel 側で 1 回だけ済ませ、ここには畳んだ結果だけを載せる。
 */
data class MatchDetailContent(
    val title: String,
    val date: Instant,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Long,
    val awayScore: Long,
    /** null なら動画を持たない試合（配信の大半）。**本文の描画をこれに依存させないこと。** */
    val videoSource: VideoSource?,
    val timeline: List<TimelineGroup>,
    val homeTeamId: TeamId,
    val isHomeOnLeft: Boolean,
    val playersById: Map<PlayerId, Player>,
    val homeTeamLine: TeamSummaryLine,
    val awayTeamLine: TeamSummaryLine,
    val phaseStats: List<PhaseStatBlock>,
    val homePlayerStats: List<PlayerStatLine>,
    val awayPlayerStats: List<PlayerStatLine>,
)

/**
 * 試合詳細の ViewModel。
 *
 * **[MatchView] の所有者はここ**。`resolver` は Rust 側のハンドルなので、
 * [onCleared] で必ず [MatchView.close] する（閉じないとネイティブのメモリが残る）。
 */
class MatchDetailViewModel(
    private val slug: String,
    private val feed: SampleFeed = SampleFeed(),
) : ViewModel() {

    private val _state = MutableStateFlow<MatchDetailUiState>(MatchDetailUiState.Loading)
    val state: StateFlow<MatchDetailUiState> = _state.asStateFlow()

    /** 開いている試合。**close の責任がこの参照にある。** */
    private var matchView: MatchView? = null

    init {
        load()
    }

    /** 「再試行」ボタン。**自動リトライはしない**ので、取り直しはここからだけ。 */
    fun retry() = load()

    private fun load() {
        // 取り直す前に前回ぶんを閉じる（失敗からの再試行では通常 null だが、
        // 「閉じ忘れが起きない置き方」を優先して無条件に畳む）。
        closeMatchView()
        _state.value = MatchDetailUiState.Loading
        viewModelScope.launch {
            when (val result = feed.loadMatchView(slug)) {
                is FeedResult.Success -> {
                    val view = result.value
                    matchView = view
                    // phase の逆引きは fact 数ぶんの FFI 呼び出しになるので Main で回さない。
                    val content = withContext(Dispatchers.Default) { buildContent(view) }
                    _state.value = MatchDetailUiState.Ready(content)
                }

                is FeedResult.Failure -> _state.value = MatchDetailUiState.Error(result)
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

        /**
         * slug を渡すための factory。
         *
         * ナビゲーションの引数を ViewModel に渡す経路をここ 1 本にしておく
         * （画面ごとに `SavedStateHandle` から引き直すと、キーの綴りが二重管理になる）。
         */
        fun factory(slug: String) = viewModelFactory {
            initializer { MatchDetailViewModel(slug) }
        }
    }
}

/**
 * [MatchView] → [MatchDetailContent]。
 *
 * **FFI（`resolver`）に触るのはここだけ。** `allPhases()` と `phaseForMatchElapsed` を
 * この 1 回で使い切り、以降は畳んだ結果だけを画面へ渡す。
 */
internal fun buildContent(view: MatchView): MatchDetailContent {
    val resolver = view.resolver
    val phaseLabelByFactId = TimelineGrouping.phaseLabels(resolver.allPhases())
    val summary = view.summary
    return MatchDetailContent(
        // 表示名は `Match.title`。無ければ「A vs B」に落とす（配信の試合には基本入っている）。
        title = view.match.title?.takeIf { it.isNotBlank() }
            ?: "${view.homeTeam.name} vs ${view.awayTeam.name}",
        date = view.match.date,
        homeTeamName = view.homeTeam.name,
        awayTeamName = view.awayTeam.name,
        homeScore = summary.homeScore,
        awayScore = summary.awayScore,
        videoSource = view.videoSource,
        timeline = TimelineGrouping.groups(
            orderedFacts = view.orderedFacts,
            phaseLabelByFactId = phaseLabelByFactId,
            phaseFactIdAt = { seconds -> resolver.phaseForMatchElapsed(seconds)?.factId },
        ),
        homeTeamId = view.homeTeam.id,
        isHomeOnLeft = view.match.isHomeOnLeft,
        playersById = view.playersById,
        homeTeamLine = summary.homeTeam,
        awayTeamLine = summary.awayTeam,
        phaseStats = summary.phaseSummaries.map { line ->
            PhaseStatBlock(
                // タイムラインの見出しと**同じ導出**を使う。取れなければ `PhaseLabel` の
                // サマリ経路へ落とす（どちらも `PhaseLabel` 1 本を通るので名前はずれない）。
                label = phaseLabelByFactId[line.phaseFactId] ?: PhaseLabel.label(line),
                line = line,
            )
        },
        homePlayerStats = PlayerStatsOrdering.sorted(
            PlayerStatsOrdering.linesForTeam(summary.playerStats, view.playersById, view.homeTeam.id),
            view.playersById,
        ),
        awayPlayerStats = PlayerStatsOrdering.sorted(
            PlayerStatsOrdering.linesForTeam(summary.playerStats, view.playersById, view.awayTeam.id),
            view.playersById,
        ),
    )
}
