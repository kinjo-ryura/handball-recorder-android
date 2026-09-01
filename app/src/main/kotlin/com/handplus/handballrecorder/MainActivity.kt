package com.handplus.handballrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.handplus.handballrecorder.ui.detail.HighlightDetailScreen
import com.handplus.handballrecorder.ui.detail.MatchDetailScreen
import com.handplus.handballrecorder.ui.detail.MatchDetailViewModel
import com.handplus.handballrecorder.ui.detail.MatchSummaryScreen
import com.handplus.handballrecorder.ui.list.MatchListScreen
import com.handplus.handballrecorder.ui.theme.HandballRecorderTheme
import com.handplus.handballrecorder.ui.video.rememberYouTubePlayerController

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

    /**
     * サマリ（試合のスタッツ）。**試合詳細の右上からしか開かない**。
     *
     * `kind` を持たないのは、ハイライトにサマリが無いから（iOS もあちらの右上は
     * 「すべて再生」で、サマリの概念が当てはまらない）。
     */
    const val SUMMARY = "summary/{slug}"

    const val ARG_KIND = "kind"
    const val ARG_SLUG = "slug"

    const val KIND_MATCH = "match"
    const val KIND_HIGHLIGHT = "highlight"

    fun detail(kind: String, slug: String): String = "detail/$kind/$slug"

    fun summary(slug: String): String = "summary/$slug"
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
            // **プレイヤーの寿命はこの行き先に紐づく。** ここで remember しておけば、
            // 画面が破棄されるときに `WebView` も一緒に destroy される
            // （`rememberYouTubePlayerController` の `DisposableEffect`）。
            // `WebView` の生成自体は動画を読むまで遅延するので、動画なしの試合を
            // 開いても Chromium の初期化も YouTube への通信も起きない。
            //
            // **試合とハイライトで同じ渡し方にしてある。** 片方だけ別の作り方にすると、
            // 破棄の責任がどちらにあるのかが行き先ごとに変わる。
            val player = rememberYouTubePlayerController()
            when (kind) {
                Routes.KIND_HIGHLIGHT -> HighlightDetailScreen(
                    slug = slug,
                    onBack = onBack,
                    // 行タップ（通し再生が止まっているとき）→ 3 秒手前へ飛んで再生する。
                    // 通し再生中の行タップはそのシーンからの再開になり、こちらは通らない。
                    onSeek = player::seek,
                    player = player,
                )

                // 未知の kind も試合として開く。slug の形が配信の規約に合わなければ
                // `SampleFeed` が取りに行く前に弾き、「見つかりません」を出す。
                else -> MatchDetailScreen(
                    slug = slug,
                    onBack = onBack,
                    onOpenSummary = { navController.navigate(Routes.summary(slug)) },
                    // 行タップ → 記録された動画位置の 3 秒手前へ飛んで再生する。
                    onSeek = player::seek,
                    player = player,
                )
            }
        }
        composable(
            route = Routes.SUMMARY,
            arguments = listOf(navArgument(Routes.ARG_SLUG) { type = NavType.StringType }),
        ) { entry ->
            val slug = entry.arguments?.getString(Routes.ARG_SLUG).orEmpty()
            // **`MatchView` の所有者を増やさない。** サマリは詳細と同じ [MatchDetailViewModel]
            // を読む — 詳細の `NavBackStackEntry` を `viewModelStoreOwner` に渡すと、
            // 同じ store から同じインスタンスが返る。だから
            //
            //   - 取得も変換も 1 回だけ（サマリを開くたびに取り直さない）
            //   - `resolver` を閉じるのは最後まで詳細の `onCleared` 1 か所（二重 close が無い）
            //   - サマリが先に読み込みを終えることも、状態が食い違うことも無い
            //
            // 引き当ては**経路のパターン**で行う（`NavDestination.route` との等値比較になる）。
            // サマリは詳細からしか開かないので、詳細は必ずスタックに載っている。
            val detailEntry = remember(entry) { navController.getBackStackEntry(Routes.DETAIL) }
            MatchSummaryScreen(
                onBack = { navController.popBackStack(); Unit },
                viewModel = viewModel(
                    viewModelStoreOwner = detailEntry,
                    factory = MatchDetailViewModel.factory(slug),
                ),
            )
        }
    }
}
