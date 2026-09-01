package com.handplus.handballrecorder.ui.video

/**
 * プレイヤーの準備状態。**前進のみ**で進み、後退するのは新しい動画を読み直すときだけ。
 *
 * `Ready` と `Positioned` を分けてあるのが肝で、これは iOS #98 の事故を防ぐための区別:
 * cued / unstarted の `getCurrentTime()` は **0 を返す**が、それは「動画の 0 秒地点に居る」
 * ではなく「まだ再生ヘッドがどこにも着地していない」の意味。`Ready` のまま位置を読ませると
 * 0 秒が本物の観測値として記録経路や通し再生へ流れる。だから位置を返してよいのは
 * [Positioned]（実際に再生 / バッファ / シークが起きた後）だけにする。
 *
 * iOS の `PlaybackReadiness` と同じ 5 状態（`unloaded` / `loading` / `ready` / `positioned` /
 * `error`）。
 */
sealed interface PlayerReadiness {

    /** まだ何も読み込んでいない。 */
    data object Unloaded : PlayerReadiness

    /** ホスト HTML / プレイヤーの用意中。 */
    data object Loading : PlayerReadiness

    /** `onReady` を受けた（再生できるが**位置は未着地**）。 */
    data object Ready : PlayerReadiness

    /** 再生 / バッファ / シークで再生ヘッドが着地した。**位置を読んでよいのはここだけ。** */
    data object Positioned : PlayerReadiness

    /**
     * 再生できない。
     *
     * @param code `onError` の [YouTube のエラーコード](https://developers.google.com/youtube/iframe_api_reference#onError)
     *   （2 / 5 / 100 / 101 / 150）。ホスト HTML 自体を用意できなかった場合は null。
     *   **UI に数字を出さない**（利用者にとって意味が無く、対処も変わらない）。
     */
    data class Error(val code: Int?) : PlayerReadiness
}

/**
 * 前進判定に使う順位。[PlayerReadiness.Error] は前進の枠外（sticky）なので -1 を返す。
 */
val PlayerReadiness.progressRank: Int
    get() = when (this) {
        PlayerReadiness.Unloaded -> 0
        PlayerReadiness.Loading -> 1
        PlayerReadiness.Ready -> 2
        PlayerReadiness.Positioned -> 3
        is PlayerReadiness.Error -> -1
    }

/** 現在位置（`getCurrentTime()`）を読んでよいか。**[PlayerReadiness.Positioned] だけが true。** */
val PlayerReadiness.allowsPositionReads: Boolean
    get() = this == PlayerReadiness.Positioned

/** プレイヤーが出来上がっているか（`seekTo` / `cueVideoById` を投げてよいか）。 */
val PlayerReadiness.isLoaded: Boolean
    get() = this == PlayerReadiness.Ready || this == PlayerReadiness.Positioned

/**
 * 画面が購読するプレイヤーの状態。
 *
 * @param readiness 準備状態（[PlayerReadiness]）
 * @param isPlaying 再生中か（`onStateChange` の `1 = playing`）
 */
data class YouTubePlayerState(
    val readiness: PlayerReadiness = PlayerReadiness.Unloaded,
    val isPlaying: Boolean = false,
) {

    /** 再生できない状態か（画面が「この動画は再生できません」を出す条件）。 */
    val hasError: Boolean get() = readiness is PlayerReadiness.Error
}

/**
 * 状態遷移。**副作用を持たない**ので単体テストで固定できる
 * （WebView そのものはテストしない）。
 */
object YouTubePlayerReducer {

    /**
     * JS から届いた 1 件のイベントを適用する。
     *
     * - `ready` → [PlayerReadiness.Ready] へ**前進**（`positioned` 到達後の遅れた `ready` で
     *   後退させない）
     * - `error` → [PlayerReadiness.Error] へ（**後退を許す唯一のイベント**。再生できない事実は
     *   途中の前進より強い）
     * - `stateChange` → `1 = playing` / `3 = buffering` だけ [PlayerReadiness.Positioned] へ前進。
     *   `2 = paused` / `5 = cued` / `-1 = unstarted` / `0 = ended` は据え置き
     *   （cued の 0 秒を位置として読ませないため）。`isPlaying` は `1` かどうかで更新する
     * - `playbackRateChange` → 状態は変えない（このアプリは再生速度を変えない。
     *   イベントは iOS と揃えて受けるだけ）
     */
    fun reduce(state: YouTubePlayerState, event: YouTubeBridgeEvent): YouTubePlayerState =
        when (event) {
            is YouTubeBridgeEvent.Ready -> advanced(state, PlayerReadiness.Ready)

            is YouTubeBridgeEvent.Error ->
                state.copy(readiness = PlayerReadiness.Error(event.code), isPlaying = false)

            is YouTubeBridgeEvent.StateChange -> {
                val playing = event.state == PLAYER_STATE_PLAYING
                val landed = event.state == PLAYER_STATE_PLAYING || event.state == PLAYER_STATE_BUFFERING
                val next = if (landed) advanced(state, PlayerReadiness.Positioned) else state
                next.copy(isPlaying = playing)
            }

            is YouTubeBridgeEvent.PlaybackRateChange -> state
        }

    /**
     * 前進のみの更新。すでに [PlayerReadiness.Error] なら**動かさない**（sticky。
     * 解除できるのは [reset]、つまり動画を読み直したときだけ）。
     */
    fun advanced(state: YouTubePlayerState, target: PlayerReadiness): YouTubePlayerState {
        if (state.readiness is PlayerReadiness.Error) return state
        if (target.progressRank <= state.readiness.progressRank) return state
        return state.copy(readiness = target)
    }

    /**
     * 動画を読み直すときの後退リセット。**エラーを解除できる唯一の経路。**
     */
    fun reset(state: YouTubePlayerState, target: PlayerReadiness): YouTubePlayerState =
        state.copy(readiness = target, isPlaying = false)

    /** `YT.PlayerState.PLAYING`。 */
    const val PLAYER_STATE_PLAYING: Int = 1

    /** `YT.PlayerState.BUFFERING`。 */
    const val PLAYER_STATE_BUFFERING: Int = 3
}
