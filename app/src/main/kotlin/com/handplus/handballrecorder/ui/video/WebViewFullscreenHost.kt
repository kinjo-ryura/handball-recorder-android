package com.handplus.handballrecorder.ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `WebChromeClient` が渡してくる全画面 view を実際に画面へ載せる側。
 *
 * `YT.Player` の全画面ボタンは document fullscreen を要求するだけで、**応えるのは
 * ホストアプリの責任**。受け口（`onShowCustomView` / `onHideCustomView`）が無いと
 * ボタンは押しても何も起きない。ボタン自体を消すのは RMF の禁止事項なので、
 * 対応は「効くようにする」しかない（README「YouTube 連携（RMF）」）。
 *
 * ## 載せる先は decor view
 *
 * 渡された view は `window.decorView` へ直接 add する。**Compose のツリーには載せない** —
 * この view の所有者は Chromium で、寿命も付け外しの順序も `WebChromeClient` の契約が
 * 決めている。composition の都合で付け直されると
 * [YouTubePlayerFrame] が警告している `already has a parent` と同じ事故になる。
 *
 * ## RMF
 *
 * 全画面 view の上に**何も重ねない**。戻るための UI も足さない（戻るキーで抜ける。
 * プレーヤー自身の全画面ボタンも縮小に使える）。YouTube の UI を覆うオーバーレイは規約違反。
 *
 * ## スレッド
 *
 * `WebChromeClient` のコールバックは main スレッドで来る。**main からのみ触ること。**
 */
class WebViewFullscreenHost(private val activity: Activity) {

    private var state = FullscreenState()
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val _isActive = MutableStateFlow(false)

    /** 全画面中か。戻るキーの有効・無効を決めるのに画面が購読する。 */
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * 全画面へ入る（`WebChromeClient.onShowCustomView` から呼ぶ）。
     *
     * すでに全画面なら**新しい view は載せずに捨てる**（[FullscreenTransitions] の規則 1）。
     */
    fun show(view: View, callback: WebChromeClient.CustomViewCallback) {
        val next = FullscreenTransitions.enter(state, activity.requestedOrientation)
        if (next == null) {
            callback.onCustomViewHidden()
            return
        }
        state = next
        customView = view
        customViewCallback = callback
        decorView.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        activity.requestedOrientation = FullscreenTransitions.FULLSCREEN_ORIENTATION
        setSystemBarsVisible(false)
        _isActive.value = true
    }

    /**
     * 全画面を抜ける。冪等（[FullscreenTransitions] の規則 2）。
     *
     * 呼ばれ方は 3 通りあり、**どれも同じ経路を通す**:
     *
     * - プレーヤーの縮小ボタン → Chromium が `onHideCustomView` を呼ぶ
     * - 戻るキー → こちらから呼ぶ。`onCustomViewHidden()` が Chromium 側の document
     *   fullscreen を解き、その結果 `onHideCustomView` が返ってくる（2 度目は素通り）
     * - 画面を離れる → [YouTubePlayerController.close] から呼ぶ。**これが無いと
     *   一覧へ戻った後も横向き固定・システムバー非表示のままになる**
     */
    fun hide() {
        // 戻す向きは exit() が初期状態を返す前に読む。
        val restoreOrientation = state.restoreOrientation
        state = FullscreenTransitions.exit(state) ?: return
        customView?.let(decorView::removeView)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        activity.requestedOrientation = restoreOrientation
        setSystemBarsVisible(true)
        _isActive.value = false
    }

    // ── private ──

    private val decorView: FrameLayout get() = activity.window.decorView as FrameLayout

    /**
     * システムバーの出し入れ。
     *
     * 隠すときは `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` を立てて、**縁からのスワイプで
     * 一時的に戻せる**ようにする（黙って戻れなくなると、戻るキーを知らない利用者が
     * 全画面から出られない）。
     */
    private fun setSystemBarsVisible(visible: Boolean) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * `LocalContext` から Activity を辿る。
 *
 * Compose の `LocalContext.current` は Activity そのものとは限らず、`ContextWrapper` に
 * 包まれていることがある（`ContextThemeWrapper` など）。**見つからなければ null** で、
 * その場合は全画面に対応しない（ボタンは今までどおり無反応になるだけで、落ちはしない）。
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
