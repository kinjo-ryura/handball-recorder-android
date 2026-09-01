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
import androidx.compose.material3.OutlinedButton
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
            ListPlaceholderScreen(
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
            DetailPlaceholderScreen(
                kind = entry.arguments?.getString(Routes.ARG_KIND).orEmpty(),
                slug = entry.arguments?.getString(Routes.ARG_SLUG).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * 一覧のプレースホルダ。中身（配信 index の取得・タブ・動画フィルタ）は次のチャンクで入れる。
 * ここに置いてあるダミー行は、遷移と戻るが通っていることを目で確かめるためだけのもの。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPlaceholderScreen(onOpen: (kind: String, slug: String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("ハンド記録") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("一覧（未実装）", style = MaterialTheme.typography.titleMedium)
            Text(
                "配信中のサンプル試合 / ハイライトの取得はまだ入っていない。",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { onOpen(Routes.KIND_MATCH, "dummy-match") }) {
                Text("ダミー行を開く（試合）")
            }
            OutlinedButton(onClick = { onOpen(Routes.KIND_HIGHLIGHT, "dummy-highlight") }) {
                Text("ダミー行を開く（ハイライト）")
            }
        }
    }
}

/**
 * 詳細のプレースホルダ。受け取った引数をそのまま出すだけで、動画枠・タイムライン・
 * スタッツは次のチャンクで入れる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPlaceholderScreen(kind: String, slug: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("詳細（未実装）") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("戻る") }
                },
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
            Text("kind: $kind", style = MaterialTheme.typography.bodyLarge)
            Text("slug: $slug", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListPlaceholderPreview() {
    HandballRecorderTheme {
        ListPlaceholderScreen(onOpen = { _, _ -> })
    }
}

@Preview(showBackground = true)
@Composable
private fun DetailPlaceholderPreview() {
    HandballRecorderTheme {
        DetailPlaceholderScreen(kind = Routes.KIND_MATCH, slug = "dummy-match", onBack = {})
    }
}
