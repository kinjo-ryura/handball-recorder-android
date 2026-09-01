package com.handplus.handballrecorder.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handplus.handballrecorder.Routes
import com.handplus.handballrecorder.ui.labels.DateLabel
import com.handplus.handballrecorder.ui.labels.FeedErrorLabel
import com.handplus.handballrecorder.ui.labels.FeedSubject
import com.handplus.handballrecorder.ui.theme.HandballRecorderTheme
import io.github.kinjoryura.handballtoolkit.SampleHighlightSummaryV2
import io.github.kinjoryura.handballtoolkit.SampleMatchSummaryV2
import java.time.Instant

/**
 * 一覧画面。配信中のサンプル試合 / ハイライトをタブで出す。
 *
 * 状態は [MatchListViewModel] が持ち、この Composable は**受け取って描くだけ**。
 */
@Composable
fun MatchListScreen(
    onOpen: (kind: String, slug: String) -> Unit,
    viewModel: MatchListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MatchListScreen(
        state = state,
        onSelectTab = viewModel::selectTab,
        onSelectVideoFilter = viewModel::selectVideoFilter,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onOpen = onOpen,
    )
}

/** 状態だけを受け取る本体（Preview と、状態を差し替えたい呼び出し用）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchListScreen(
    state: MatchListUiState,
    onSelectTab: (ListTab) -> Unit,
    onSelectVideoFilter: (VideoFilter) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (kind: String, slug: String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("ハンド記録") }) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                ListTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(tab.title) },
                    )
                }
            }

            // 絞りは試合タブだけ。ハイライトは全件動画つきなので出さない。
            if (state.tab == ListTab.MATCH) {
                VideoFilterRow(selected = state.videoFilter, onSelect = onSelectVideoFilter)
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state.tab) {
                    ListTab.MATCH -> MatchTabContent(state = state, onRetry = onRetry, onOpen = onOpen)
                    ListTab.HIGHLIGHT -> HighlightTabContent(state = state, onRetry = onRetry, onOpen = onOpen)
                }
            }
        }
    }
}

/** 「すべて / 動画あり / 動画なし」。現在の選択がその場で読めるよう、メニューではなくチップで出す。 */
@Composable
private fun VideoFilterRow(selected: VideoFilter, onSelect: (VideoFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VideoFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.title) },
            )
        }
    }
}

@Composable
private fun MatchTabContent(
    state: MatchListUiState,
    onRetry: () -> Unit,
    onOpen: (kind: String, slug: String) -> Unit,
) {
    when (val matches = state.matches) {
        is FeedUiState.Loading -> LoadingList("試合を読み込んでいます…")
        is FeedUiState.Error -> ErrorList(
            message = FeedErrorLabel.message(matches.failure, FeedSubject.MATCH),
            onRetry = onRetry,
        )

        is FeedUiState.Success -> {
            val items = state.visibleMatches
            if (items.isEmpty()) {
                // 配信は読めているがフィルタで 0 件になった（iOS の「該当する試合がありません」）。
                EmptyList(
                    if (matches.items.isEmpty()) "配信中の試合がありません。" else "該当する試合がありません。",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.slug }) { summary ->
                        MatchRow(
                            summary = summary,
                            onClick = { onOpen(Routes.KIND_MATCH, summary.slug) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightTabContent(
    state: MatchListUiState,
    onRetry: () -> Unit,
    onOpen: (kind: String, slug: String) -> Unit,
) {
    when (val highlights = state.highlights) {
        is FeedUiState.Loading -> LoadingList("ハイライトを読み込んでいます…")
        is FeedUiState.Error -> ErrorList(
            message = FeedErrorLabel.message(highlights.failure, FeedSubject.HIGHLIGHT),
            onRetry = onRetry,
        )

        is FeedUiState.Success ->
            if (highlights.items.isEmpty()) {
                EmptyList("配信中のハイライトがありません。")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(highlights.items, key = { it.slug }) { summary ->
                        HighlightRow(
                            summary = summary,
                            onClick = { onOpen(Routes.KIND_HIGHLIGHT, summary.slug) },
                        )
                        HorizontalDivider()
                    }
                }
            }
    }
}

/**
 * 試合 1 件。上段に日付（+ 動画あり）、下段に名前とスコア。
 *
 * **表示は index summary だけで組む**（本体 JSON は開くまで取りに行かない）。
 */
@Composable
private fun MatchRow(summary: SampleMatchSummaryV2, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = DateLabel.ymd(summary.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.description?.takeIf { it.isNotEmpty() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (summary.hasVideo) {
                Text(
                    text = VIDEO_MARK,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${summary.homeScore} - ${summary.awayScore}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** ハイライト 1 件。上段に日付とカード（`A vs B`）、下段にタイトルとシーン数。 */
@Composable
private fun HighlightRow(summary: SampleHighlightSummaryV2, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${DateLabel.ymd(summary.date)}・${summary.homeTeamName} vs ${summary.awayTeamName}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$VIDEO_MARK ${summary.factCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingList(message: String) {
    // **引っぱって更新を殺さないよう LazyColumn に載せる。** 中央寄せの Box に置くと
    // スクロールできる祖先が無くなり、読み込み中に引っぱっても何も起きない。
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * 失敗表示。**文言 + 「再試行」だけを出す**（自動リトライはしない）。
 *
 * 例外の `message` / `detail` は出さない（toolkit ADR 0002 決定 5）。
 */
@Composable
private fun ErrorList(message: String, onRetry: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRetry) { Text("再試行") }
            }
        }
    }
}

@Composable
private fun EmptyList(message: String) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }
}

/**
 * 「動画あり」を表すしるし。
 *
 * アイコンフォント（`material-icons-*`）を足さずに済ませるため記号にしてある。
 * web デモも同じ ▶ を「押すと動画が飛ぶ」目印に使っている。
 */
private const val VIDEO_MARK = "▶"

@Preview(showBackground = true)
@Composable
private fun MatchListPreview() {
    HandballRecorderTheme {
        MatchListScreen(
            state = MatchListUiState(
                matches = FeedUiState.Success(
                    listOf(
                        SampleMatchSummaryV2(
                            slug = "sample-a",
                            displayName = "A高校 vs B高校",
                            description = "県大会 準決勝",
                            date = Instant.parse("2026-04-10T00:00:00Z"),
                            homeScore = 28,
                            awayScore = 25,
                            hasVideo = true,
                        ),
                    ),
                ),
            ),
            onSelectTab = {},
            onSelectVideoFilter = {},
            onRetry = {},
            onRefresh = {},
            onOpen = { _, _ -> },
        )
    }
}
