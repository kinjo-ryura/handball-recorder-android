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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handplus.handballrecorder.ui.labels.ClockFormat
import com.handplus.handballrecorder.ui.labels.FeedErrorLabel
import com.handplus.handballrecorder.ui.labels.FeedSubject
import com.handplus.handballrecorder.ui.labels.RateFormat
import com.handplus.handballrecorder.ui.labels.label
import com.handplus.handballrecorder.ui.playback.ClipPlaybackController
import com.handplus.handballrecorder.ui.playback.PlaybackOffsets
import com.handplus.handballrecorder.ui.playback.rememberClipPlaybackController
import com.handplus.handballrecorder.ui.video.YouTubePlayerController
import com.handplus.handballrecorder.ui.video.YouTubePlayerFrame
import com.handplus.handballrecorder.ui.video.YouTubePlayerState
import com.handplus.handballrecorder.ui.video.isLoaded
import io.github.kinjoryura.handballtoolkit.FactId
import io.github.kinjoryura.handballtoolkit.PlayEventKind
import io.github.kinjoryura.handballtoolkit.VideoProvider
import io.github.kinjoryura.handballtoolkit.VideoSource
import io.github.kinjoryura.handballtoolkit.scoringRate
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ハイライト詳細。上から **見出し / 動画枠 / 「すべて再生」/ シーン一覧 / このハイライトの記録**。
 *
 * ## 試合詳細と描画を分けてある理由（web デモと同じ）
 *
 * - **得点タイムラインではなくシーン一覧（1 列）**。ハイライトは記録の過半が `freeNote`
 *   （ナイスパス等）の回もあり、得点に絞ると大半のシーンが消える。全 play fact を種別
 *   チップつきで並べる
 * - **一覧の行数と通し再生のシーン数は一致しない**。通し再生に載るのは iOS と同じ
 *   goal / shotMissed / freeNote の 3 種（`ClipProgression.isPlaybackTarget`）で、
 *   カード類は**行としては出すが ▶ を出さず、「すべて再生（N シーン）」の N にも数えない**。
 *   タップの単発シークだけは効く。強調行と `index` の対応は必ず `factId` で取ること
 *   （[indexOfFact]）
 * - **両サイド表示が成立しない**。片チームの選手だけを取り上げるのでアウェイ列が常に空になり、
 *   phase を持たないので中央の試合時計も常に空になる
 * - **スコアを出さない**。`summary.homeScore` は試合スコアではなく「このハイライトに写っている
 *   得点数」なので、両チーム列で並べると「6–0 で勝った試合」に見える。だから見出しも
 *   「スタッツ」ではなく**「このハイライトの記録」**
 *
 * ## 時刻は動画時間
 *
 * `resolvedMatchClock` は全 fact で null になるので、行に出るのは**動画の位置**。
 * 同じ書式の数字が試合時間にも動画時間にも見えると誤読するため、見出しに
 * 「時刻は動画時間」を添えている（iOS `EventRowView.formattedTime` が「もう一方の時計へ
 * fallback しない」としているのと同じ理由）。
 *
 * @param onSeek 行タップの単発シーク（秒）。[player] の `seek` に繋ぐ
 * @param player 動画のプレイヤー。null なら動画枠も通し再生も出さない（プレビュー用）
 */
@Composable
fun HighlightDetailScreen(
    slug: String,
    onBack: () -> Unit,
    onSeek: (Double) -> Unit,
    player: YouTubePlayerController?,
    viewModel: HighlightDetailViewModel = viewModel(factory = HighlightDetailViewModel.factory(slug)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HighlightDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSeek = onSeek,
        player = player,
        playback = rememberClipPlaybackController(player),
    )
}

/** 状態だけを受け取る本体。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightDetailScreen(
    state: HighlightDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSeek: (Double) -> Unit,
    player: YouTubePlayerController?,
    playback: ClipPlaybackController,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                // **タイトルは固定文言**。ハイライトの名前は本文の見出しに出る（試合詳細と違い、
                // 見出しがスクロールで隠れないので同じ文字列を 2 か所に出す必要が無い）。
                title = { Text("ハイライト") },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state) {
                is HighlightDetailUiState.Loading -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator()
                    Text("ハイライトを読み込んでいます…", style = MaterialTheme.typography.bodyMedium)
                }

                is HighlightDetailUiState.Error -> Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = FeedErrorLabel.message(state.failure, FeedSubject.HIGHLIGHT),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onRetry) { Text("再試行") }
                }

                is HighlightDetailUiState.Ready -> HighlightDetailReady(
                    content = state.content,
                    onSeek = onSeek,
                    player = player,
                    playback = playback,
                )
            }
        }
    }
}

@Composable
private fun HighlightDetailReady(
    content: HighlightDetailContent,
    onSeek: (Double) -> Unit,
    player: YouTubePlayerController?,
    playback: ClipPlaybackController,
) {
    // 対象が変わったら通し再生を作り直す（前の対象を指したまま回り続けさせない）。
    LaunchedEffect(playback, content.clips) { playback.setClips(content.clips) }

    val playbackState by playback.state.collectAsStateWithLifecycle()
    // プレイヤーが無い経路（プレビュー）では既定値の状態を配る流れを使う。
    // **毎回の再コンポーズで新しい Flow を作らない**ように remember に包む。
    val playerStateFlow = remember(player) { player?.state ?: MutableStateFlow(YouTubePlayerState()) }
    val playerState by playerStateFlow.collectAsStateWithLifecycle()

    val source = playableVideoSource(content.videoSource)
    val listState = rememberLazyListState()

    // 再生中のシーンが画面外にあると、強調しても見えない。行が変わったら一覧を寄せる
    // （web デモの `markPlayingRow` がカード内をスクロールするのと同じ狙い）。
    // **利用者の操作中は動かさない** — 通し再生が index を進めたときだけ走る。
    // **クリップの index をそのまま行番号に使わないこと。** 一覧はカード類のぶんだけ行が
    // 多いので、`factId` で引き当てる（[indexOfFact]）。
    val playingIndex = content.scenes.indexOfFact(playbackState.playingFactId)
    LaunchedEffect(playingIndex) {
        playingIndex?.let { listState.animateScrollToItem(it + SCENE_ITEM_OFFSET) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 見出し。**動画の上に置く** — 何を見ているのかは映像より先に分かるべきなので
        //    （web デモの `renderHighlightHeading` と同じ位置）。
        HighlightHeading(title = content.title, subtitle = content.subtitle)

        // 2. 動画枠。**リストの中ではなく上に固定する**（理由は MatchDetailScreen と同じ）。
        if (source != null && player != null) {
            YouTubePlayerFrame(controller = player, videoSource = source)
        }

        // 3. 「すべて再生」。**ハイライトの体験本体はこれ**で、行タップの単発シークでは
        //    代替できない（間を飛ばして名場面だけを繋いで見るのがハイライト）。
        //    動画が用意できるまで押せない。
        if (content.clips.isNotEmpty()) {
            PlayAllButton(
                clipCount = content.clips.size,
                isPlaying = playbackState.isPlaying,
                currentNumber = playbackState.index + 1,
                enabled = player != null && playerState.readiness.isLoaded && !playerState.hasError,
                onClick = playback::toggle,
            )
        }

        HighlightBody(
            content = content,
            listState = listState,
            playingFactId = playbackState.playingFactId,
            onSceneTap = { scene ->
                val seconds = scene.videoSeconds ?: return@HighlightBody
                // **通し再生中の行タップは「そのシーンから再開」**（押した 1 本で終わりではなく、
                // そこから後続の名場面へ繋がるのがハイライトの見方）。止まっているときは
                // 試合詳細と同じ単発シーク。
                //
                // **web デモとは意図的に違う。** あちらは行タップが常に通し再生の入口だが、
                // このアプリは「1 シーンだけ確かめる」を単発シークで残している
                // （3 秒手前 = PlaybackOffsets.SEEK_OFFSET_SECONDS。通し再生の lead-in 4 秒とは別物）。
                //
                // **通し再生の対象外（カード類）は再生中でも単発シークになる** — クリップ列に
                // 居ないので index が -1 に落ち、下の else へ流れる。ここに専用の分岐を足さない
                // こと（判定が 2 か所に増える）。
                val index = content.clips.indexOfFirst { it.factId == scene.factId }
                if (playbackState.isPlaying && index >= 0) {
                    playback.start(index)
                } else {
                    onSeek(PlaybackOffsets.seekTarget(seconds))
                }
            },
        )
    }
}

/**
 * このアプリで再生できる動画ソースだけを通す。
 *
 * ハイライトは `MatchViewBuilder.buildHighlight` が YouTube 以外を弾いているので、
 * ここに来る時点で YouTube のはず。**それでも確かめるのは、判定を 1 か所に頼らないため**
 * （試合詳細の同名関数と同じ規則）。
 */
private fun playableVideoSource(source: VideoSource?): VideoSource? =
    source?.takeIf { it.provider == VideoProvider.YOUTUBE }

/** 見出し（ハイライト名 + `{home} vs {away}・日付`）。 */
@Composable
private fun HighlightHeading(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 「すべて再生（N シーン）」／再生中は「停止（n / N）」。
 *
 * **N はクリップ数であって、シーン一覧の行数ではない。** 通し再生に載らないカード類は
 * 数えない（`ClipProgression.isPlaybackTarget`）。一方 **重なりでも N は減らない**
 * （クリップ列をマージしないので、対象の行とは 1:1 のまま）。
 */
@Composable
private fun PlayAllButton(
    clipCount: Int,
    isPlaying: Boolean,
    currentNumber: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (isPlaying) {
                "停止（$currentNumber / $clipCount）"
            } else {
                "すべて再生（$clipCount シーン）"
            },
        )
    }
}

@Composable
private fun HighlightBody(
    content: HighlightDetailContent,
    listState: LazyListState,
    playingFactId: FactId?,
    onSceneTap: (HighlightScene) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        // ここの item 数を変えたら SCENE_ITEM_OFFSET も直すこと。
        item { SectionHeader(title = "シーン", note = "時刻は動画時間") }
        if (content.scenes.isEmpty()) {
            item { HighlightEmptyNote("記録なし") }
        }
        items(
            count = content.scenes.size,
            key = { index -> "scene-${content.scenes[index].factId}" },
        ) { index ->
            val scene = content.scenes[index]
            SceneRow(
                scene = scene,
                isPlaying = scene.factId == playingFactId,
                onTap = { onSceneTap(scene) },
            )
        }

        item { SectionHeader(title = "このハイライトの記録", note = null) }
        if (content.playerStats.isEmpty()) {
            item { HighlightEmptyNote("記録なし") }
        } else {
            item { HighlightPlayerStatsTable(content.playerStats) }
        }
    }
}

/**
 * シーン一覧の 1 行（1 列）。`[種別チップ] [#N 名前] [動画時刻] [▶]`。
 *
 * 動画位置を持たない fact も**行としては出す**（記録された事実は必ず 1 行にする）。
 * その行は押せず、時刻と ▶ の欄が空になる。
 *
 * **▶ は「通し再生に入る行」の印**（[HighlightScene.isPlaybackTarget]）。カード類は
 * 動画位置を持っていても ▶ を出さない — 押せば単発でそこへ飛ぶが、「すべて再生」では
 * 流れないので、印を出すと嘘になる。**押せるかどうか（`clickable`）とは別の条件**。
 */
@Composable
private fun SceneRow(scene: HighlightScene, isPlaying: Boolean, onTap: () -> Unit) {
    val seekable = scene.videoSeconds != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 再生中の行を塗って強調する（通し再生が今どこを流しているか）。
            //
            // **`Modifier.then(...)` を使わないこと。** AGP 8.11.1 に同梱の lint が
            // `SuspiciousModifierThenDetector` の中で `NoClassDefFoundError` を起こして
            // `lintDebug` ごと落ちる（2026-09-01 実測。lint 側のバグで、こちらのコードは
            // 正しい）。検査を disable して回避するより、既に試合詳細（`TimelineRow`）で
            // 使っている `let` の形へ揃えるほうが安い。
            .let { if (isPlaying) it.background(MaterialTheme.colorScheme.secondaryContainer) else it }
            .let { if (seekable) it.clickable(onClick = onTap) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SceneKindChip(scene.kind)
        Text(
            text = scene.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = scene.videoSeconds?.let { ClockFormat.mmss(it) } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 通し再生に入る行の目印。印の無い行にも空欄を置いて時刻の列を揃える（web デモと同じ）。
        Text(
            text = if (scene.isPlaybackTarget) "▶" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider()
}

/** 種別のしるし。得点だけ塗る（試合詳細の `KindChip` と同じ規則）。 */
@Composable
private fun SceneKindChip(kind: PlayEventKind) {
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

/** 見出し（右に注記を添えられる）。 */
@Composable
private fun SectionHeader(title: String, note: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HighlightEmptyNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 選手 / 得点 / シュートミス / 成功率。**シュートミスを列に出すのは試合詳細（試投）との違い。** */
@Composable
private fun HighlightPlayerStatsTable(stats: List<HighlightPlayerStat>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        HighlightStatRow("選手", "得点", "シュートミス", "成功率", header = true)
        stats.forEach { stat ->
            HighlightStatRow(
                name = stat.name,
                goals = "${stat.line.goals}",
                misses = "${stat.line.shotMisses}",
                rate = RateFormat.percent(stat.line.scoringRate),
            )
        }
    }
}

@Composable
private fun HighlightStatRow(
    name: String,
    goals: String,
    misses: String,
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
        Text(text = misses, style = style, color = color, modifier = Modifier.weight(1.4f))
        Text(text = rate, style = style, color = color, modifier = Modifier.weight(1f))
    }
}

/**
 * シーン行の前に置いてある item の数（「シーン」の見出し 1 つ）。
 *
 * 再生中の行へスクロールするときの補正に使う。**[HighlightBody] の item 構成を変えたら
 * ここも直すこと**（ずれても落ちはしないが、寄せる位置が 1 行ぶん狂う）。
 */
private const val SCENE_ITEM_OFFSET = 1
