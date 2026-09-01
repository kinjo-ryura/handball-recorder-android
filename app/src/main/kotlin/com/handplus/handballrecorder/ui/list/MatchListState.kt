package com.handplus.handballrecorder.ui.list

import com.handplus.handballrecorder.data.FeedResult
import io.github.kinjoryura.handballtoolkit.SampleHighlightSummaryV2
import io.github.kinjoryura.handballtoolkit.SampleMatchSummaryV2

/**
 * 一覧のタブ。**試合とハイライトは出所の違う別コレクション**なので 1 スクロールに積まない
 * （iOS `MatchListViewV2` の segmented と同じ切り分け。あちらは「自分の試合」を含む 3 択だが、
 * 見る専用 MVP は自分の記録を持たないので 2 択になる）。
 */
enum class ListTab(val title: String) {
    MATCH("試合"),
    HIGHLIGHT("ハイライト"),
}

/**
 * 試合タブの動画有無フィルタ。
 *
 * **既定は [WITH_VIDEO]。** 配信 46 件の大半は PDF 由来のタイマー試合（動画なし）で、
 * 絞らないと動画つきの試合が埋もれる（iOS #105 と同じ判断）。「すべて」は明示的に選ぶ。
 *
 * **ハイライトタブには出さない** — 配信されているハイライトは全件動画つきなので、
 * 絞り込みが常に無意味な操作になる。
 */
enum class VideoFilter(val title: String) {
    ALL("すべて"),
    WITH_VIDEO("動画あり"),
    WITHOUT_VIDEO("動画なし"),
}

/**
 * 配信 index の絞り込み。**画面から切り出した純関数**（テストで規則を固定するため）。
 *
 * **`hasVideo` は index summary を正とする。** 本体 JSON を取ってきて configuration を
 * 見るのではない（iOS `MatchListViewV2.sampleHasVideo` と同じ）。一覧で 46 件ぶんの本体を
 * 引く必要が無くなるうえ、配信側が「動画つきとして出している」判断をそのまま尊重できる。
 *
 * **並べ替えない。** 配信 index の配列順をそのまま保つ（配信側で日付降順に確定済み）。
 * iOS は「動画ありを先頭 → 日付降順」で並べ直すが、あちらはフィルタ既定が同じでも
 * 「すべて」を選んだときに動画つきを上へ寄せたいという事情がある。こちらは
 * **配信の順を唯一の順序**にしておき、順序の規則が 2 つに増えないようにする。
 */
fun List<SampleMatchSummaryV2>.filteredBy(filter: VideoFilter): List<SampleMatchSummaryV2> =
    when (filter) {
        VideoFilter.ALL -> this
        VideoFilter.WITH_VIDEO -> filter { it.hasVideo }
        VideoFilter.WITHOUT_VIDEO -> filter { !it.hasVideo }
    }

/**
 * 1 コレクションぶんの読み込み状態。
 *
 * 取得中 / 成功 / 失敗の 3 状態しか持たない。**自動リトライをしない**ので「再試行中」に
 * 相当する中間状態は無く、再取得はユーザーの操作（引っぱって更新 / 「再試行」）から
 * [Loading] へ戻る。
 */
sealed interface FeedUiState<out T> {

    data object Loading : FeedUiState<Nothing>

    data class Success<out T>(val items: List<T>) : FeedUiState<T>

    /** 失敗。文言は `FeedErrorLabel` が決めるので、ここでは分類だけ持ち回る。 */
    data class Error(val failure: FeedResult.Failure) : FeedUiState<Nothing>
}

/** 一覧画面の状態一式。Composable はこれを受け取って描くだけにする。 */
data class MatchListUiState(
    val tab: ListTab = ListTab.MATCH,
    val videoFilter: VideoFilter = VideoFilter.WITH_VIDEO,
    val matches: FeedUiState<SampleMatchSummaryV2> = FeedUiState.Loading,
    val highlights: FeedUiState<SampleHighlightSummaryV2> = FeedUiState.Loading,
    /** 引っぱって更新の実行中。初回の [FeedUiState.Loading] とは別に持つ（表示が別物なので）。 */
    val isRefreshing: Boolean = false,
) {

    /** 試合タブに実際に並ぶ行（フィルタ適用後）。 */
    val visibleMatches: List<SampleMatchSummaryV2>
        get() = when (val state = matches) {
            is FeedUiState.Success -> state.items.filteredBy(videoFilter)
            else -> emptyList()
        }
}
