package com.handplus.handballrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.handplus.handballrecorder.ui.detail.MatchDetailScreen
import com.handplus.handballrecorder.ui.list.MatchListScreen
import com.handplus.handballrecorder.ui.theme.HandballRecorderTheme

/**
 * 「見る専用」MVP の入口。画面はすべて Compose で、Activity は 1 枚だけ持つ
 * （行き先の出し分けは [HandballRecorderApp] の NavHost がやる）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HandballRecorderTheme {
                HandballRecorderApp()
            }
        }
    }
}

/**
 * 行き先の定義。**経路の文字列はここだけが持つ**（画面側で組み立てると
 * `detail/{kind}/{slug}` の綴りが二重管理になる）。
 */
object Routes {
    /** 一覧（試合 / ハイライト）。 */
    const val LIST = "list"

    /** 詳細。`kind` は [KIND_MATCH] / [KIND_HIGHLIGHT] のいずれか。 */
    const val DETAIL = "detail/{kind}/{slug}"

    const val ARG_KIND = "kind"
    const val ARG_SLUG = "slug"

    const val KIND_MATCH = "match"
    const val KIND_HIGHLIGHT = "highlight"

    fun detail(kind: String, slug: String): String = "detail/$kind/$slug"
}

@Composable
fun HandballRecorderApp(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            MatchListScreen(
                onOpen = { kind, slug -> navController.navigate(Routes.detail(kind, slug)) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_KIND) { type = NavType.StringType },
                navArgument(Routes.ARG_SLUG) { type = NavType.StringType },
            ),
        ) { entry ->
            val kind = entry.arguments?.getString(Routes.ARG_KIND).orEmpty()
            val slug = entry.arguments?.getString(Routes.ARG_SLUG).orEmpty()
            val onBack = { navController.popBackStack(); Unit }
            when (kind) {
                Routes.KIND_HIGHLIGHT -> HighlightPlaceholderScreen(slug = slug, onBack = onBack)
                // 未知の kind も試合として開く。slug の形が配信の規約に合わなければ
                // `SampleFeed` が取りに行く前に弾き、「見つかりません」を出す。
                else -> MatchDetailScreen(
                    slug = slug,
                    onBack = onBack,
                    // **シークの受け口だけ用意する。** 実体（YouTube の WebView）は次のチャンク。
                    // ここで何もしないラムダを渡しておけば、行タップが落ちることはない。
                    onSeek = {},
                )
            }
        }
    }
}

/**
 * ハイライト詳細のプレースホルダ。中身（動画枠 / シーン一覧 / 「すべて再生」）は次のチャンク。
 *
 * 一覧のハイライトタブからここへ遷移できることまでが、このチャンクの範囲。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightPlaceholderScreen(slug: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ハイライト詳細（未実装）") },
                navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "シーン一覧と「すべて再生」はこれから実装します。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("slug: $slug", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HighlightPlaceholderPreview() {
    HandballRecorderTheme {
        HighlightPlaceholderScreen(slug = "sample-highlight", onBack = {})
    }
}
