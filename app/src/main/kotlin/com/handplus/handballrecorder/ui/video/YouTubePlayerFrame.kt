package com.handplus.handballrecorder.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kinjoryura.handballtoolkit.VideoSource

/**
 * 画面のライフサイクルに紐づく [YouTubePlayerController] を用意する。
 *
 * **`WebView` の破棄はここが責任を持つ。** `onDispose` で [YouTubePlayerController.close] を
 * 呼ぶ（閉じ忘れると Chromium のプロセスと再生が残る）。
 */
@Composable
fun rememberYouTubePlayerController(): YouTubePlayerController {
    val context = LocalContext.current
    val controller = remember(context) { YouTubePlayerController(context) }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    return controller
}

/**
 * YouTube プレイヤーの枠（16:9）と、再生できないときの注記。
 *
 * **本文の描画をこの composable の成否に依存させないこと。** 呼び出し側は先にタイムラインと
 * スタッツを描き、プレイヤーは後から用意される（動画を持たない試合ではそもそも出さない）。
 *
 * ## 画面の中で動かさない（`LazyColumn` の item にしない）
 *
 * `WebView` の実体は [controller] が持つ。`LazyColumn` の item に置くと画面外へスクロールした
 * ときに item ごと破棄され、戻ってきたときに**同じ `WebView` を別の親へ付け直す**ことになる
 * （`The specified child already has a parent`）。加えて、下のほうの行をタップして
 * シークしても飛び先が画面外になる。だから呼び出し側は**リストの外**に置いて固定する。
 *
 * ## RMF
 *
 * この枠の上に**何も重ねない**。YouTube の UI・attribution を覆うオーバーレイは規約違反で、
 * エラー時の注記も枠の**下**に出す。詳細は README「YouTube 連携（RMF）」。
 */
@Composable
fun YouTubePlayerFrame(
    controller: YouTubePlayerController,
    videoSource: VideoSource,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    LaunchedEffect(controller, videoSource.externalId) {
        controller.load(videoSource.externalId)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .aspectRatio(WIDESCREEN_ASPECT),
        ) {
            AndroidView(
                factory = { controller.webView },
                modifier = Modifier.fillMaxSize(),
                // **ここで destroy しない。** `WebView` の所有者は controller で、寿命は
                // 画面（rememberYouTubePlayerController の DisposableEffect）に従う。
                // ここでやるのは親から外すことだけ — 外さないと、同じ `WebView` を
                // 載せ直すときに `already has a parent` で落ちる。
                onRelease = { view -> (view.parent as? android.view.ViewGroup)?.removeView(view) },
            )
        }

        if (state.hasError) {
            // 削除 / 非公開 / 埋め込み無効。**タイムラインとスタッツは残す**ので、
            // 画面全体をエラーにはしない。コード番号は出さない（利用者にできることが変わらない）。
            Text(
                text = "この動画は再生できません（削除・非公開・埋め込み無効のいずれか）。" +
                    "タイムラインとスタッツはそのまま見られます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** 動画の縦横比。ホスト HTML のレターボックス CSS と同じ 16:9。 */
private const val WIDESCREEN_ASPECT = 16f / 9f
