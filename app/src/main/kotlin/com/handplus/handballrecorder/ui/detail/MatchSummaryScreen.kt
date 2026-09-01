package com.handplus.handballrecorder.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.handplus.handballrecorder.ui.labels.ControlLabel
import com.handplus.handballrecorder.ui.labels.DateLabel
import com.handplus.handballrecorder.ui.labels.FeedErrorLabel
import com.handplus.handballrecorder.ui.labels.FeedSubject
import com.handplus.handballrecorder.ui.labels.RateFormat
import com.handplus.handballrecorder.ui.labels.displayName
import io.github.kinjoryura.handballtoolkit.PlayerStatLine
import io.github.kinjoryura.handballtoolkit.awayAttempts
import io.github.kinjoryura.handballtoolkit.homeAttempts
import io.github.kinjoryura.handballtoolkit.scoringRate
import io.github.kinjoryura.handballtoolkit.shotAttempts

/**
 * 試合のサマリ。**試合詳細（[MatchDetailScreen]）の右上「サマリ」から開く別画面**。
 *
 * ## なぜ別画面か
 *
 * iOS がそうだから（`MatchDetailViewV2` の右上 →「サマリ」→ `MatchSummaryViewV2`）。
 * 以前は詳細の下に inline で並べていたが、**導線を iOS に合わせる**判断になった。
 * タイムラインを読むこととスタッツを読むことは別の用事で、混ぜると
 * 「タイムラインを最後まで送らないとスタッツに着かない」画面になる。
 *
 * ## 並び（iOS `MatchSummaryViewV2` と同じ順）
 *
 * 1. スコア（チーム名 + 得点を大きく）
 * 2. チーム別（得点 / シュートミス / シュート数 / 成功率）
 * 3. 前後半別（`summary.phaseSummaries`）
 * 4. 選手別（ホーム / アウェイ。得点降順 → 試投降順 → 名前順）
 * 5. 得点差の推移（[ScoreDiffChart]。`scoreProgression` が無い試合では**節ごと出さない**）
 *
 * **iOS の 6 枚目「共有カード」は入れていない。** スタッツ画像の生成と OS の共有シートが要り、
 * 見る専用 MVP の範囲外（README「現状」）。
 *
 * ## [com.handplus.handballrecorder.domain.MatchView] の所有
 *
 * **この画面は所有しない。** 表示に使う [MatchDetailViewModel] は試合詳細のものを
 * そのまま共有する（`MainActivity` が詳細の `NavBackStackEntry` を `viewModelStoreOwner` に
 * 渡す）。だから `close()` の責任は最後まで [MatchDetailViewModel] 側にあり、
 * この画面は**開くことも閉じることもしない** — 取り直しも `resolver` の二重解放も起きない。
 */
@Composable
fun MatchSummaryScreen(
    onBack: () -> Unit,
    viewModel: MatchDetailViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MatchSummaryScreen(state = state, onBack = onBack, onRetry = viewModel::retry)
}

/** 状態だけを受け取る本体。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSummaryScreen(
    state: MatchDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                // 試合名は本文のスコアに出るので、見出しは画面の役割を示す固定文言にする
                // （iOS の `navigationTitle("サマリ")` と同じ）。
                title = { Text("サマリ") },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                // 詳細と ViewModel を共有しているので、**詳細が読み込み中のうちに開かれうる**
                // （右上のボタンは読み込みの完了を待たない。iOS も同じ）。
                is MatchDetailUiState.Loading -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator()
                    Text("試合を読み込んでいます…", style = MaterialTheme.typography.bodyMedium)
                }

                is MatchDetailUiState.Error -> Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = FeedErrorLabel.message(state.failure, FeedSubject.MATCH),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onRetry) { Text("再試行") }
                }

                is MatchDetailUiState.Ready -> MatchSummaryContentList(content = state.content)
            }
        }
    }
}

@Composable
private fun MatchSummaryContentList(content: MatchDetailContent) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 1. スコア。
        item { SummaryScoreHeader(content) }

        // 2. チーム別。
        item { SectionTitle("チーム別") }
        item { TeamStatsTable(content) }

        // 3. 前後半別。
        item { SectionTitle("前後半別") }
        if (content.phaseStats.isEmpty()) {
            item { EmptyNote("記録なし") }
        }
        items(
            count = content.phaseStats.size,
            key = { "phase-stat-${content.phaseStats[it].line.phaseFactId}" },
        ) { index ->
            PhaseStatsBlock(block = content.phaseStats[index], content = content)
        }

        // 4. 選手別。
        item { SectionTitle("選手別") }
        item {
            PlayerStatsTable(
                teamName = content.homeTeamName,
                lines = content.homePlayerStats,
                content = content,
            )
        }
        item {
            PlayerStatsTable(
                teamName = content.awayTeamName,
                lines = content.awayPlayerStats,
                content = content,
            )
        }

        // 5. 得点差の推移。**作れない試合では節ごと出さない**（見出しだけ残して
        // 「記録なし」を出すと、記録が無いのか機能が無いのか読み分けられない）。
        content.scoreProgression?.let { progression ->
            item { SectionTitle("得点差の推移") }
            item {
                ScoreDiffChart(
                    progression = progression,
                    homeTeamName = content.homeTeamName,
                    awayTeamName = content.awayTeamName,
                )
            }
        }

        // 最後の要素が画面の下端に貼り付かないようにする。
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/**
 * スコア。**試合詳細のヘッダーより大きい**（この画面はスタッツを読むためのもので、
 * 最初に目に入るべきなのが結果そのものだから。iOS `scoreCard` と同じ扱い）。
 */
@Composable
private fun SummaryScoreHeader(content: MatchDetailContent) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = DateLabel.ymd(content.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreColumn(
                name = content.homeTeamName,
                score = content.homeScore,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "-",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            ScoreColumn(
                name = content.awayTeamName,
                score = content.awayScore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScoreColumn(name: String, score: Long, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$score",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** チーム別（得点 / シュートミス / シュート数 / 成功率）。iOS `teamStatsCard` と同じ 4 行。 */
@Composable
private fun TeamStatsTable(content: MatchDetailContent) {
    val home = content.homeTeamLine
    val away = content.awayTeamLine
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        StatRow("", content.homeTeamName, content.awayTeamName, header = true)
        StatRow("得点", "${home.goals}", "${away.goals}")
        StatRow("シュートミス", "${home.shotMisses}", "${away.shotMisses}")
        StatRow("シュート数", "${home.shotAttempts}", "${away.shotAttempts}")
        StatRow("成功率", RateFormat.withFraction(home), RateFormat.withFraction(away))
    }
}

/** 前後半別（phase ごとに 得点 / シュート数 / 成功率）。iOS `phaseStatBlock` と同じ 3 行。 */
@Composable
private fun PhaseStatsBlock(block: PhaseStatBlock, content: MatchDetailContent) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = block.label, style = MaterialTheme.typography.titleSmall)
        StatRow("", content.homeTeamName, content.awayTeamName, header = true)
        StatRow("得点", "${block.line.homeGoals}", "${block.line.awayGoals}")
        StatRow("シュート数", "${block.line.homeAttempts}", "${block.line.awayAttempts}")
        StatRow(
            "成功率",
            RateFormat.homeWithFraction(block.line),
            RateFormat.awayWithFraction(block.line),
        )
    }
}

@Composable
private fun StatRow(label: String, home: String, away: String, header: Boolean = false) {
    val style = if (header) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.bodyMedium
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f),
        )
        Text(text = home, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(text = away, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** 選手別（チームごと）。並びは [PlayerStatsOrdering]（得点降順 → 試投降順 → 名前順）。 */
@Composable
private fun PlayerStatsTable(teamName: String, lines: List<PlayerStatLine>, content: MatchDetailContent) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = teamName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (lines.isEmpty()) {
            EmptyNote("記録なし")
            return@Column
        }
        PlayerStatRow("選手", "得点", "試投", "成功率", header = true)
        lines.forEach { line ->
            PlayerStatRow(
                name = content.playersById[line.playerId]?.displayName ?: ControlLabel.UNKNOWN_PLAYER,
                goals = "${line.goals}",
                attempts = "${line.shotAttempts}",
                rate = RateFormat.percent(line.scoringRate),
            )
        }
    }
}

@Composable
private fun PlayerStatRow(
    name: String,
    goals: String,
    attempts: String,
    rate: String,
    header: Boolean = false,
) {
    val style = if (header) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.bodyMedium
    }
    val color = if (header) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = name,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f),
        )
        Text(text = goals, style = style, color = color, modifier = Modifier.weight(1f))
        Text(text = attempts, style = style, color = color, modifier = Modifier.weight(1f))
        Text(text = rate, style = style, color = color, modifier = Modifier.weight(1f))
    }
}
