package com.handplus.handballrecorder.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handplus.handballrecorder.data.FeedResult
import com.handplus.handballrecorder.data.SampleFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 一覧画面の状態を持つ ViewModel。
 *
 * **画面は状態を受け取って描くだけ**にする（取得も分岐もここ）。Composable の中で
 * `LaunchedEffect` から `SampleFeed` を直に叩くと、回転や再コンポーズのたびに配信へ
 * 取りに行ってしまう。
 *
 * **自動リトライはしない。** 失敗したら文言と「再試行」を出し、次の取得は
 * [retry] / [refresh] というユーザーの操作からだけ始まる。
 */
class MatchListViewModel(
    private val feed: SampleFeed = SampleFeed(),
) : ViewModel() {

    private val _state = MutableStateFlow(MatchListUiState())
    val state: StateFlow<MatchListUiState> = _state.asStateFlow()

    init {
        // 2 コレクションを**並行**で取りに行く。片方の失敗がもう片方を巻き込まないよう、
        // 状態も別々に持つ（タブを切り替えれば生きているほうは読める）。
        loadMatches()
        loadHighlights()
    }

    fun selectTab(tab: ListTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun selectVideoFilter(filter: VideoFilter) {
        _state.update { it.copy(videoFilter = filter) }
    }

    /** 表示中のタブだけを取り直す（「再試行」ボタン）。 */
    fun retry() {
        when (_state.value.tab) {
            ListTab.MATCH -> loadMatches()
            ListTab.HIGHLIGHT -> loadHighlights()
        }
    }

    /**
     * 引っぱって更新。**両方のコレクションを取り直す**（iOS の `.refreshable` と同じ）。
     *
     * タブを跨いだ後で「更新したはずなのに古い」が起きないよう、片方だけにしない。
     */
    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val matches = feed.fetchMatchIndex()
            val highlights = feed.fetchHighlightIndex()
            _state.update {
                it.copy(
                    matches = matches.toUiState(),
                    highlights = highlights.toUiState(),
                    isRefreshing = false,
                )
            }
        }
    }

    private fun loadMatches() {
        _state.update { it.copy(matches = FeedUiState.Loading) }
        viewModelScope.launch {
            val result = feed.fetchMatchIndex()
            _state.update { it.copy(matches = result.toUiState()) }
        }
    }

    private fun loadHighlights() {
        _state.update { it.copy(highlights = FeedUiState.Loading) }
        viewModelScope.launch {
            val result = feed.fetchHighlightIndex()
            _state.update { it.copy(highlights = result.toUiState()) }
        }
    }
}

/**
 * 取得結果 → 画面の状態。
 *
 * 失敗は**分類のまま**持ち回る（文言に潰さない）。`FeedErrorLabel` が主語つきで
 * 日本語にするので、ここで文字列にしてしまうと試合 / ハイライトの言い分けができなくなる。
 */
private fun <T> FeedResult<List<T>>.toUiState(): FeedUiState<T> = when (this) {
    is FeedResult.Success -> FeedUiState.Success(value)
    is FeedResult.Failure -> FeedUiState.Error(this)
}
