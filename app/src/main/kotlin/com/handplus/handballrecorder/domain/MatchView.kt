package com.handplus.handballrecorder.domain

import io.github.kinjoryura.handballtoolkit.Match
import io.github.kinjoryura.handballtoolkit.Player
import io.github.kinjoryura.handballtoolkit.PlayerId
import io.github.kinjoryura.handballtoolkit.ResolvedFact
import io.github.kinjoryura.handballtoolkit.SampleMatchConversionResult
import io.github.kinjoryura.handballtoolkit.SegmentResolver
import io.github.kinjoryura.handballtoolkit.SummaryProjection
import io.github.kinjoryura.handballtoolkit.Team
import io.github.kinjoryura.handballtoolkit.TeamId
import io.github.kinjoryura.handballtoolkit.TimelineProjection
import io.github.kinjoryura.handballtoolkit.VideoSource
import io.github.kinjoryura.handballtoolkit.videoSource

/**
 * 1 試合 / 1 ハイライトを画面へ出すのに必要なものを一式まとめた表示用モデル。
 *
 * wasm の `buildMatchView`（web デモが使う）は wasm crate 固有で `.aar` には無いので、
 * 同じものを [MatchViewBuilder] が組み立てる。
 *
 * **必ず [close] すること。** [timeline] の `resolver` は Rust 側にハンドルを持つ
 * `AutoCloseable` で、閉じないとネイティブのメモリが残る。ViewModel の `onCleared` で
 * 閉じる想定（保持する側が確実に閉じられるよう、このクラス自体を `AutoCloseable` にしてある）。
 */
class MatchView internal constructor(
    /** 取得に使った slug。表示には使わないが、ログとディープリンクの生成に要る。 */
    val slug: String,
    /** コアの変換結果（match / teams / players / facts）。 */
    val conversion: SampleMatchConversionResult,
    /** `buildTimeline` の結果。`resolver` の所有者はこの [MatchView]。 */
    val timeline: TimelineProjection,
    /** `buildSummaryWithTimeline` の結果（`phaseSummaries` が埋まっているほう）。 */
    val summary: SummaryProjection,
    /** [TimelineOrdering] で時系列に並べ替えた `timeline.resolvedFacts`。 */
    val orderedFacts: List<ResolvedFact>,
    /** 選手 ID → 選手。画面が行ごとに線形探索しないための索引。 */
    val playersById: Map<PlayerId, Player>,
    /** チーム ID → チーム（home / away の 2 件）。 */
    val teamsById: Map<TeamId, Team>,
) : AutoCloseable {

    val match: Match get() = conversion.match
    val homeTeam: Team get() = conversion.homeTeam
    val awayTeam: Team get() = conversion.awayTeam
    val players: List<Player> get() = conversion.players

    /** 動画時刻 ⇔ 試合時刻の変換と phase 逆引き。**寿命はこの [MatchView] に従う。** */
    val resolver: SegmentResolver get() = timeline.resolver

    /**
     * 動画ソース（タイマーモードの試合では null）。
     *
     * 判定は `.aar` のシム（`MatchConfiguration.videoSource`）に任せる。画面側で
     * `when (configuration)` を書き直さないこと。
     */
    val videoSource: VideoSource? get() = match.configuration.videoSource

    fun player(id: PlayerId?): Player? = id?.let { playersById[it] }

    fun team(id: TeamId?): Team? = id?.let { teamsById[it] }

    /**
     * `resolver` を含む projection のネイティブ資源を解放する。
     *
     * `TimelineProjection.destroy()` が `resolvedFacts` と `resolver` の両方を面倒みる
     * （生成コードの `Disposable.destroy(vararg)`）ので、ここで個別に閉じ分けない。
     * 二重呼び出しは無害（`SegmentResolver` 側が済み判定を持つ）。
     */
    override fun close() {
        timeline.destroy()
    }
}
