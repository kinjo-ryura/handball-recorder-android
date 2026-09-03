package com.handplus.handballrecorder.ui.video

import android.content.pm.ActivityInfo

/**
 * 全画面の出入りの状態。
 *
 * **view も Activity も持たない**ので JVM 単体テストで固定できる（実際の view 操作・
 * 向きの固定・システムバーの制御は [WebViewFullscreenHost] が持つ）。ここが預かるのは
 * 「今 全画面か」と「**抜けたときに戻す向き**」の 2 つだけ。
 *
 * @param isActive 全画面中か
 * @param restoreOrientation 全画面へ入る**直前**の `Activity.requestedOrientation`。
 *   抜けるときはこの値へ戻す（`UNSPECIFIED` へ決め打ちで戻すと、将来どこかの画面が
 *   向きを固定したときに黙って解除してしまう）
 */
data class FullscreenState(
    val isActive: Boolean = false,
    val restoreOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
)

/**
 * 全画面の状態遷移。**副作用を持たない**ので単体テストで固定できる。
 *
 * ここで固定したい規則は 3 つで、どれも `WebChromeClient` の契約か実害に直結する:
 *
 * 1. **二重に入れない。** `onShowCustomView` は全画面中にもう一度呼ばれうる。そのとき
 *    先の view を捨てて後の view を載せると、`onCustomViewHidden` を呼ぶ相手が入れ替わり
 *    Chromium 側と食い違う。後から来たほうを断る。
 * 2. **抜けるのは冪等。** 戻るキーで抜けるとき、こちらから `onCustomViewHidden()` を呼ぶと
 *    Chromium が続けて `onHideCustomView()` を返してくる。2 度目が素通りしないと
 *    向きの復帰が二重に走る。
 * 3. **向きは入る前の値へ戻す。** [FullscreenState.restoreOrientation] 参照。
 */
object FullscreenTransitions {

    /**
     * 全画面中に使う向き。
     *
     * `SENSOR_LANDSCAPE` は**左右どちらの横向きにも追従する**（`LANDSCAPE` だと片側に
     * 固定され、端末を逆さに持った利用者に上下逆の映像を見せる）。試合動画は 16:9 なので
     * 横向きが最大になる。
     */
    const val FULLSCREEN_ORIENTATION: Int = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    /**
     * 全画面へ入る。
     *
     * @param currentOrientation 入る直前の `Activity.requestedOrientation`
     * @return 次の状態。**すでに全画面なら null**（呼び出し側は新しい callback を
     *   即座に `onCustomViewHidden()` して捨てる）
     */
    fun enter(state: FullscreenState, currentOrientation: Int): FullscreenState? {
        if (state.isActive) return null
        return FullscreenState(isActive = true, restoreOrientation = currentOrientation)
    }

    /**
     * 全画面を抜ける。**戻す向きは呼び出し側が [FullscreenState.restoreOrientation] から
     * 先に読んでおくこと**（この関数は初期状態を返すので、戻り値には残らない）。
     *
     * @return 次の状態。**全画面でなければ null**（呼び出し側は何もしない）
     */
    fun exit(state: FullscreenState): FullscreenState? {
        if (!state.isActive) return null
        return FullscreenState()
    }
}
