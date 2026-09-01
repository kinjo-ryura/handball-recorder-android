package com.handplus.handballrecorder.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handplus.handballrecorder.ui.labels.ClockFormat
import com.handplus.handballrecorder.ui.labels.ControlLabel
import com.handplus.handballrecorder.ui.labels.DateLabel
import com.handplus.handballrecorder.ui.labels.FeedErrorLabel
import com.handplus.handballrecorder.ui.labels.FeedSubject
import com.handplus.handballrecorder.ui.labels.displayName
import com.handplus.handballrecorder.ui.labels.label
import com.handplus.handballrecorder.ui.playback.PlaybackOffsets
import com.handplus.handballrecorder.ui.video.YouTubePlayerController
import com.handplus.handballrecorder.ui.video.YouTubePlayerFrame
import io.github.kinjoryura.handballtoolkit.ControlFact
import io.github.kinjoryura.handballtoolkit.MatchFactPayload
import io.github.kinjoryura.handballtoolkit.PlayEventKind
import io.github.kinjoryura.handballtoolkit.PlayFact
import io.github.kinjoryura.handballtoolkit.ResolvedFact
import io.github.kinjoryura.handballtoolkit.VideoProvider
import io.github.kinjoryura.handballtoolkit.VideoSource

/**
 * 試合詳細。上から **動画枠 / スコア / タイムライン**。
 *
 * **スタッツはここに置かない。** 右上の「サマリ」から別画面（[MatchSummaryScreen]）で開く。
 * iOS（`MatchDetailViewV2` の右上 →「サマリ」）に導線を合わせたもので、以前はこの画面の
 * 下に inline で並べていた。**ハイライト詳細は変えていない** — iOS もあちらの右上は
 * 「すべて再生」で、サマリの概念が当てはまらないため。
 *
 * **本文の描画を動画の有無に依存させない。** 配信 46 件の大半は PDF 由来のタイマー試合
 * （動画なし）で、動画が無いことは異常ではない。`videoSource` が null なら動画枠を出さず、
 * スコアとタイムラインだけを描く。動画が**再生できなかった**ときも同じで、注記を足すだけで
 * 本文はそのまま残す。
 *
 * **動画枠だけはスクロールしない**（リストの上に固定する）。理由は
 * [com.handplus.handballrecorder.ui.video.YouTubePlayerFrame] の説明を参照。
 *
 * @param onOpenSummary 右上「サマリ」。[MatchSummaryScreen] への遷移に繋ぐ
 * @param onSeek 行タップで動画をその位置へ飛ばす（秒）。[player] の `seek` に繋ぐ
 * @param player 動画を持つ試合で使うプレイヤー。null なら動画枠を出さない
 *   （プレビューや、プレイヤーを用意できない経路のため）
 */
@Composable
fun MatchDetailScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenSummary: () -> Unit,
    onSeek: (Double) -> Unit,
    player: YouTubePlayerController?,
    viewModel: MatchDetailViewModel = viewModel(factory = MatchDetailViewModel.factory(slug)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MatchDetailScreen(
        state = state,
        onBack = onBack,
        onOpenSummary = onOpenSummary,
        onRetry = viewModel::retry,
        onSeek = onSeek,
        player = player,
    )
}

/** 状態だけを受け取る本体。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    state: MatchDetailUiState,
    onBack: () -> Unit,
    onOpenSummary: () -> Unit,
    onRetry: () -> Unit,
    onSeek: (Double) -> Unit,
    player: YouTubePlayerController?,
) {
    val title = (state as? MatchDetailUiState.Ready)?.content?.title ?: "試合"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
                // **読み込みの完了を待たずに出す**（iOS も toolbar の出し分けは
                // ハイライトかどうかだけで、読み込み状態を見ない）。読み込み中に押しても
                // サマリ側が同じ状態を共有しているので、あちらで読み込み表示になる。
                actions = { TextButton(onClick = onOpenSummary) { Text("サマリ") } },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
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

                is MatchDetailUiState.Ready -> Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 動画枠。**動画を持たない試合では丸ごと出さない**（配信 46 件の大半）。
                    //
                    // **リストの中ではなく上に固定する。** `LazyColumn` の item にすると
                    // スクロールで item ごと破棄され、同じ `WebView` を別の親へ付け直す形に
                    // なる。加えて、下のほうの行をタップしても飛び先が画面外になる。
                    val source = playableVideoSource(state.content.videoSource)
                    if (source != null && player != null) {
                        YouTubePlayerFrame(controller = player, videoSource = source)
                    }
                    MatchDetailContentList(content = state.content, onSeek = onSeek)
                }
            }
        }
    }
}

/**
 * このアプリで再生できる動画ソースだけを通す。
 *
 * `local` は iOS 端末内の PHAsset を指すので Android では開けない。**枠を出さずに
 * スコアとタイムラインだけを描く**（動画なしの試合と同じ扱い）。
 */
private fun playableVideoSource(source: VideoSource?): VideoSource? =
    source?.takeIf { it.provider == VideoProvider.YOUTUBE }

@Composable
private fun MatchDetailContentList(content: MatchDetailContent, onSeek: (Double) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 2. スコア（サマリ画面の大きいスコアとは別。ここは日付つきの 1 行見出し）。
        item { ScoreHeader(content) }

        // 3. タイムライン。
        item { SectionTitle("タイムライン") }
        if (content.timeline.isEmpty()) {
            item { EmptyNote("記録なし") }
        }
        content.timeline.forEachIndexed { groupIndex, group ->
            group.label?.let { label ->
                // **キーに index を混ぜる。** 同じ phase の中に時刻を解決できない fact が
                // 挟まるとグループが分かれ、phaseFactId だけでは重複しうる。
                item(key = "phase-$groupIndex-${group.phaseFactId}") { PhaseHeader(label) }
            }
            items(
                count = group.facts.size,
                key = { index -> "fact-${group.facts[index].fact.id}" },
            ) { index ->
                TimelineRow(
                    resolved = group.facts[index],
                    content = content,
                    onSeek = onSeek,
                )
            }
        }
    }
}

@Composable
private fun ScoreHeader(content: MatchDetailContent) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
            Text(
                text = content.homeTeamName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${content.homeScore} - ${content.awayScore}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = content.awayTeamName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 節の見出し。**[MatchSummaryScreen] と共有する**（同じ package の内部部品）。 */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** 「記録なし」等の控えめな注記。**[MatchSummaryScreen] と共有する**。 */
@Composable
internal fun EmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** phase の見出し（前半 / 後半 / 延長前半 / 7mTC）。 */
@Composable
private fun PhaseHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * タイムラインの 1 行。
 *
 * play fact は **3 カラム等幅**（`[ホーム側セル] [中央に時刻] [アウェイ側セル]`）。
 * 反対側は透明のプレースホルダで幅を確保するので、**片側にしか出ない行でも時刻が行の中央**に来る
 * （iOS `EventRowView` と同じ手法）。
 *
 * 時刻は**試合時計**。もう一方の時計へ fallback せず、解決できなければ `--:--` を出す
 * （動画時間の数字が同じ書式で紛れると誤読させる。iOS `EventRowView.formattedTime`）。
 */
@Composable
private fun TimelineRow(resolved: ResolvedFact, content: MatchDetailContent, onSeek: (Double) -> Unit) {
    // シーク先。**記録時刻そのものではなく少し手前へ飛ばす**（PlaybackOffsets の 3 秒）。
    // 動画時刻が解決できない fact と、動画を持たない試合では null = 押せない行になる。
    val seekTarget = resolved.resolvedVideoClock
        ?.takeIf { playableVideoSource(content.videoSource) != null }
        ?.let { PlaybackOffsets.seekTarget(it.elapsedSeconds) }
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { modifier ->
            if (seekTarget == null) modifier else modifier.clickable { onSeek(seekTarget) }
        }
        .padding(horizontal = 16.dp, vertical = 6.dp)

    when (val payload = resolved.fact.payload) {
        is MatchFactPayload.Play -> PlayRow(
            play = payload.v1,
            timeText = matchClockText(resolved),
            content = content,
            modifier = rowModifier,
        )

        is MatchFactPayload.Control -> when (val control = payload.v1) {
            // phaseStart は見出しとして描くので行には来ない（TimelineGrouping が畳んである）。
            is ControlFact.PhaseStart -> Unit
            is ControlFact.Stoppage -> ControlRow(
                label = ControlLabel.stoppage(control.v1),
                timeText = matchClockText(resolved),
                modifier = rowModifier,
            )
        }

        // possession は行にしない（#217。TimelineGrouping が落としてある）。
        is MatchFactPayload.Possession -> Unit
    }
    HorizontalDivider()
}

private fun matchClockText(resolved: ResolvedFact): String =
    resolved.resolvedMatchClock?.let { ClockFormat.mmss(it.elapsedSeconds) } ?: "--:--"

@Composable
private fun PlayRow(
    play: PlayFact,
    timeText: String,
    content: MatchDetailContent,
    modifier: Modifier,
) {
    val onLeft = TimelineGrouping.isOnLeft(play.teamId, content.homeTeamId, content.isHomeOnLeft)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (onLeft) PlayCell(play = play, content = content, alignEnd = false)
        }
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (!onLeft) PlayCell(play = play, content = content, alignEnd = true)
        }
    }
}

/**
 * 片側のセル（種別のしるし + 選手表記）。
 *
 * **種別のしるしは日本語ラベルのチップ**にしてある。iOS は形で区別するアイコンを使うが、
 * それには `material-icons-*` を足すことになるので、依存を増やさずに同じ情報量を出せる
 * 文字にした（web デモのシーン一覧も種別ラベルのチップ）。**意図的な差分**。
 *
 * `freeNote` は選手名の下にタイトルも出す（誰のシーンかは種別によらず知りたい情報なので、
 * タイトルがあっても名前を落とさない。iOS / web デモと同じ）。
 */
@Composable
private fun PlayCell(play: PlayFact, content: MatchDetailContent, alignEnd: Boolean) {
    val name = play.playerId
        ?.let { content.playersById[it] }
        ?.displayName
        ?: ControlLabel.UNKNOWN_PLAYER
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!alignEnd) KindChip(play.kind)
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            play.title?.takeIf { it.isNotBlank() && play.kind == PlayEventKind.FREE_NOTE }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (alignEnd) KindChip(play.kind)
    }
}

/** 種別のしるし。得点だけ塗って、スコアが流れの中で目に留まるようにする。 */
@Composable
private fun KindChip(kind: PlayEventKind) {
    val isGoal = kind == PlayEventKind.GOAL
    Text(
        text = kind.label,
        style = MaterialTheme.typography.labelSmall,
        color = if (isGoal) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isGoal) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 中断 / タイムアウトの行（ラベルと時刻を中央寄せ）。 */
@Composable
private fun ControlRow(label: String, timeText: String, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
