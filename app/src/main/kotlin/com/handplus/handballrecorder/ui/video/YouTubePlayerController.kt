package com.handplus.handballrecorder.ui.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * `WebView` 上で YouTube IFrame Player API（`YT.Player`）を駆動する。
 *
 * iOS の `YouTubePlaybackClient` の移植。設計もそちらに合わせてある:
 *
 * - **`WebView` を所有するのはこのクラス**で、Compose 側（[YouTubePlayerFrame]）は
 *   `AndroidView` で載せるだけ。view 階層の組み直しで再生位置を失わないため。
 * - HTML は `assets/youtube_player.html` を読み、`loadDataWithBaseURL` の baseUrl に
 *   **app-origin**（[origin]）を与える。
 * - JS からのイベントは `@JavascriptInterface`（[YouTubeBridge.NAME]）で受け、
 *   [YouTubePlayerReducer] で [YouTubePlayerState] に正規化して [state] で配る。
 *
 * ## 触ってよい面（RMF）
 *
 * 使うのは **`YT.Player` の公式メソッドとイベントだけ**（`initPlayer` /
 * `cueVideoById` / `seekTo` / `playVideo` / `pauseVideo` / `getCurrentTime` と 4 つのイベント）。
 * `document.querySelector('video')` のような内部 DOM アクセス、生の `postMessage`、
 * コントロールを隠す `playerVars` は使わない。詳細は README「YouTube 連携（RMF）」。
 *
 * ## スレッド
 *
 * 公開メソッドは**どのスレッドから呼んでもよい**（内部で main へ寄せる）。
 * `@JavascriptInterface` のコールバックは WebView の JavaBridge スレッドで来るので、
 * 状態は [MutableStateFlow]、main を要る処理は [runOnMain] を通す。
 */
class YouTubePlayerController(context: Context) : AutoCloseable {

    private val context: Context = context
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * IFrame API に渡す app-origin（`https://com.handplus.handballrecorder`）。
     *
     * **ここを `http://127.0.0.1` や `file://` にすると、公開 URL では再生できる動画が
     * `onError` 150 で弾かれる**（2026-08-22 に実測。1 か月ものあいだ「投稿者が埋め込みを
     * 無効化している」と誤診した原因がこれ）。applicationId から組むこの形は iOS の
     * `loadHTMLString(_:baseURL:)` と同じで、**RMF が案内している実装でもある**
     * （baseUrl が `Referer` 相当の client identity を与える）。
     */
    val origin: String = "https://" + context.packageName.lowercase(Locale.ROOT)

    private val _state = MutableStateFlow(YouTubePlayerState())

    /** 画面が購読する状態（`ready` / `error` / 再生中か）。 */
    val state: StateFlow<YouTubePlayerState> = _state.asStateFlow()

    /**
     * `WebView` は**遅延生成**する。
     *
     * 配信 46 件のうち 43 件は動画を持たない試合で、それらの詳細画面では
     * [YouTubePlayerFrame] 自体が出ない。生成を [load] / mount まで遅らせておけば、
     * 動画なしの試合を開いても WebView（Chromium）の初期化も YouTube への通信も起きない。
     */
    private val webViewLazy = lazy { createWebView() }

    /** mount 用。**触れるのは main スレッドだけ。** */
    val webView: WebView get() = webViewLazy.value

    // ↓ すべて main スレッドからのみ触る（bridge から来るものは runOnMain を通す）。
    private var isDestroyed = false
    private var hostingLoadStarted = false
    private var isHtmlLoaded = false
    private var pendingVideoId: String? = null
    private var requestedVideoId: String? = null
    private var pendingSeekSeconds: Double? = null

    /** `onReady` を 1 度でも受けたか（= `player` が JS 側に存在するか）。bridge スレッドが書く。 */
    @Volatile
    private var hasPlayer = false

    private val bridge = YouTubeJsBridge { message ->
        YouTubeBridge.decode(message)?.let(::handleBridgeEvent)
    }

    /**
     * 動画を読み込む。**2 回目以降は `cueVideoById` で差し替える**（プレイヤーを作り直さない）。
     *
     * 初回はここでホスト HTML のロードが始まる。**それまで YouTube への通信は発生しない。**
     */
    fun load(videoId: String) {
        if (videoId.isBlank()) return
        runOnMain {
            if (isDestroyed) return@runOnMain
            if (requestedVideoId == videoId && hostingLoadStarted) return@runOnMain
            requestedVideoId = videoId
            pendingVideoId = videoId
            // 読み直しは後退リセット（error を解除できる唯一の経路）。
            _state.update { YouTubePlayerReducer.reset(it, PlayerReadiness.Loading) }
            when {
                !hostingLoadStarted -> loadHostingHtml()
                isHtmlLoaded -> initOrReplacePlayer(videoId)
                // HTML ロード中。onPageFinished が pendingVideoId を消化する。
                else -> Unit
            }
        }
    }

    /**
     * 指定位置へ飛んで再生する（web デモの `seekPlayer` と同じ `seekTo` + `playVideo`）。
     *
     * まだプレイヤーが出来ていなければ**予約**して `onReady` の後に適用する
     * （読み込み中に行をタップしても取りこぼさない）。渡す秒数はすでに
     * `PlaybackOffsets.seekTarget` を通っている前提で、ここでは補正しない。
     */
    fun seek(seconds: Double) {
        val literal = JsLiterals.number(seconds) ?: return
        runOnMain {
            if (isDestroyed) return@runOnMain
            when {
                _state.value.readiness is PlayerReadiness.Error -> Unit
                _state.value.readiness.isLoaded -> {
                    evaluate("player.seekTo($literal, true)")
                    evaluate("player.playVideo()")
                    // シーク完了も「再生ヘッドの着地」とみなす（iOS #98 案 2）。
                    // cued 中に seekTo した後どの PlayerState が来るかは環境依存なので、
                    // stateChange 経由の positioned 化に頼らず明示的に前進させる。
                    _state.update { YouTubePlayerReducer.advanced(it, PlayerReadiness.Positioned) }
                }

                else -> pendingSeekSeconds = seconds
            }
        }
    }

    /**
     * 一時停止する（`YT.Player.pauseVideo`）。
     *
     * 呼ぶのは**通し再生が最後のクリップまで見終わったとき**だけ
     * （[com.handplus.handballrecorder.ui.playback.ClipPlaybackController]）。通し再生の
     * 「停止」では呼ばない — 同じ画面にプレイヤーが残るので、そこで勝手に止めると
     * 行タップからの再開が二重操作になる（web デモの `stopPlayAll` / `finishPlayAll` と同じ分け方）。
     *
     * プレイヤーが出来ていなければ何もしない（**予約しない** — [seek] と違い、
     * 後から効かせて嬉しい操作ではない）。
     */
    fun pause() {
        runOnMain {
            if (isDestroyed) return@runOnMain
            if (!_state.value.readiness.isLoaded) return@runOnMain
            evaluate("player.pauseVideo()")
        }
    }

    /**
     * 現在の再生位置（秒）。**位置が着地していなければ null。**
     *
     * cued / unstarted の `getCurrentTime()` は 0 を返すが、それは「動画の 0 秒地点」ではない
     * （iOS #98）。[PlayerReadiness.Positioned] 未満で null を返すのはそのため。
     *
     * ハイライトの通し再生（[com.handplus.handballrecorder.ui.playback.ClipPlaybackController]）が
     * 250ms 間隔で呼ぶ。1 回の呼び出しは `evaluateJavascript` 1 往復で、結果は main スレッドで受ける。
     */
    suspend fun currentTimeSeconds(): Double? {
        if (!_state.value.readiness.allowsPositionReads) return null
        return evaluateNumber("player.getCurrentTime()")
    }

    /**
     * `WebView` を破棄する。**画面を離れたら必ず呼ぶこと**（Compose 側の `DisposableEffect`）。
     * 冪等。
     */
    override fun close() {
        runOnMain {
            if (isDestroyed) return@runOnMain
            isDestroyed = true
            if (!webViewLazy.isInitialized()) return@runOnMain
            val view = webViewLazy.value
            // destroy() は親に付いたままだと不正。先に外す。
            (view.parent as? ViewGroup)?.removeView(view)
            view.removeJavascriptInterface(YouTubeBridge.NAME)
            view.stopLoading()
            // 空ページへ飛ばしてから壊す。載っている iframe の再生を確実に止めるため。
            view.loadUrl("about:blank")
            view.removeAllViews()
            view.destroy()
        }
    }

    // ── private ──

    /**
     * `javaScriptEnabled` は IFrame Player API を使う以上必須。読み込むのは自前の
     * `assets/youtube_player.html` と YouTube の iframe だけで、任意の URL は開かない。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(Color.BLACK)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        // **これが無いとプログラムからの playVideo() / seekTo() が効かない**
        // （利用者のタップを伴わない再生開始が既定で禁止されているため）。
        settings.mediaPlaybackRequiresUserGesture = false
        settings.domStorageEnabled = true
        // 端末のファイルもコンテンツプロバイダも読ませない（HTML は assets から流し込む）。
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        // 素の WebChromeClient を置く。**全画面ボタンは押しても何も起きない**
        // （onShowCustomView を実装していない）が、RMF 上ボタン自体を消してはいけないので
        // `fs` は既定のまま残す。全画面対応は別途。
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                handleHostingHtmlLoaded()
            }
        }
        addJavascriptInterface(bridge, YouTubeBridge.NAME)
    }

    private fun loadHostingHtml() {
        hostingLoadStarted = true
        val html = runCatching {
            context.assets.open(HOSTING_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (html == null) {
            // アセットが無い＝ビルドの取りこぼし。利用者向けには「再生できない」で同じ。
            _state.update { YouTubePlayerReducer.reset(it, PlayerReadiness.Error(null)) }
            return
        }
        webView.loadDataWithBaseURL(origin, html, "text/html", "utf-8", null)
    }

    private fun handleHostingHtmlLoaded() {
        // iframe 側の遷移で呼ばれても 2 度目以降は何もしない。
        if (isHtmlLoaded || isDestroyed) return
        isHtmlLoaded = true
        pendingVideoId?.let(::initOrReplacePlayer)
    }

    private fun initOrReplacePlayer(videoId: String) {
        pendingVideoId = null
        if (hasPlayer) {
            // 2 回目以降。プレイヤーは作り直さず動画だけ差し替える。
            evaluate("player.cueVideoById(${JsLiterals.string(videoId)})")
            // cue では onReady が再発火しないので、ここで「用意はできたが位置は未着地」へ戻す。
            _state.update { YouTubePlayerReducer.reset(it, PlayerReadiness.Ready) }
            flushPendingSeek()
            return
        }
        // videoId は配信データ由来。**文字列連結ではなくリテラル化して埋める。**
        // minimalControls は **常に false**（コントロールを隠すのは RMF 違反）。
        evaluate(
            "initPlayer(${JsLiterals.string(videoId)}, ${JsLiterals.string(origin)}, " +
                "{ minimalControls: false });",
        )
    }

    private fun handleBridgeEvent(event: YouTubeBridgeEvent) {
        _state.update { YouTubePlayerReducer.reduce(it, event) }
        if (event is YouTubeBridgeEvent.Ready) {
            hasPlayer = true
            runOnMain { flushPendingSeek() }
        }
    }

    private fun flushPendingSeek() {
        val target = pendingSeekSeconds ?: return
        pendingSeekSeconds = null
        seek(target)
    }

    private fun evaluate(js: String) {
        runOnMain {
            if (isDestroyed || !webViewLazy.isInitialized()) return@runOnMain
            webViewLazy.value.evaluateJavascript(js, null)
        }
    }

    /**
     * 数値を返す JS を評価する。取れなければ null。
     *
     * **非有限（NaN / ±∞）は null に畳む。** `getCurrentTime()` は準備中に NaN を返すことがあり、
     * そのまま通すと通し再生の進行判定が壊れる（iOS も同じ防御をしている）。
     */
    private suspend fun evaluateNumber(js: String): Double? = withContext(Dispatchers.Main.immediate) {
        if (isDestroyed || !webViewLazy.isInitialized()) return@withContext null
        suspendCancellableCoroutine { continuation ->
            webViewLazy.value.evaluateJavascript(js) { result ->
                val value = result?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
                if (continuation.isActive) continuation.resume(value)
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private companion object {
        const val HOSTING_ASSET = "youtube_player.html"
    }
}

/**
 * JS → ネイティブの受け口の実体。
 *
 * **トップレベルの public クラスにしてある。** `addJavascriptInterface` はリフレクションで
 * `@JavascriptInterface` の付いたメソッドを探すので、private / internal なクラスに置くと
 * 呼べないことがある。
 *
 * `@JavascriptInterface` のメソッドは **WebView の JavaBridge スレッド**で呼ばれる。
 */
class YouTubeJsBridge(private val onMessage: (String) -> Unit) {

    @JavascriptInterface
    fun postMessage(message: String) {
        onMessage(message)
    }
}
