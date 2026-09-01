package com.handplus.handballrecorder.domain

import com.handplus.handballrecorder.data.FeedResult
import com.handplus.handballrecorder.data.SampleFeed
import io.github.kinjoryura.handballtoolkit.MatchConfiguration
import io.github.kinjoryura.handballtoolkit.SampleDtoException
import io.github.kinjoryura.handballtoolkit.SampleMatchConfigurationDtoV2
import io.github.kinjoryura.handballtoolkit.SampleMatchDtoV2
import io.github.kinjoryura.handballtoolkit.VideoProvider
import io.github.kinjoryura.handballtoolkit.VideoSource
import io.github.kinjoryura.handballtoolkit.buildSummaryWithTimeline
import io.github.kinjoryura.handballtoolkit.buildTimeline
import io.github.kinjoryura.handballtoolkit.convertSampleMatch
import io.github.kinjoryura.handballtoolkit.decodeSampleConfiguration
import io.github.kinjoryura.handballtoolkit.sampleMatchRequiredIdCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 配信 DTO から [MatchView] を組み立てる。
 *
 * web デモの `buildMatchView` は wasm crate 固有で `.aar` には無いので、同じ 4 手を並べる:
 *
 * ```
 * ids      = List(sampleMatchRequiredIdCount(dto)) { UUID.randomUUID() }
 * conv     = convertSampleMatch(slug, dto, configurationOverride, ids)
 * timeline = buildTimeline(conv.match, conv.facts)
 * summary  = buildSummaryWithTimeline(conv.match, timeline)
 * ```
 *
 * **ID の発行はシェルの責務**（コアは UUID も現在時刻も生成しない = 設計不変条件）。
 * ただし**必要な個数はコアに数えさせる** — 足りなければ
 * `SampleDtoException.InsufficientNewIds` になるし、勝手な式で数えると DTO の構造が
 * 変わった日に静かにずれる。
 */
object MatchViewBuilder {

    /**
     * 試合経路。configuration は DTO のものをそのまま使う（timer / video の両方が来る）。
     *
     * @throws SampleDtoException コアの変換に失敗した（parse 済み DTO の内容が不正）。
     */
    fun buildMatch(slug: String, dto: SampleMatchDtoV2): MatchView =
        build(slug, dto, configurationOverride = null)

    /**
     * ハイライト経路。configuration を `videoHighlight` で固定する。
     *
     * **一括取得と単独取得の変換経路をこの 1 本に揃えてある**（iOS
     * `HighlightStoreV2.convertHighlight` と同じ理由 — override の付け忘れで片方だけ
     * `video` として開く事故を防ぐ）。
     *
     * @throws SampleDtoException コアの decode / 変換に失敗した。
     * @throws HighlightRouteException 動画ソースがハイライトとして成立しない
     *   （timer だった / provider が youtube でない）。その 1 件を落とす合図。
     */
    fun buildHighlight(slug: String, dto: SampleMatchDtoV2): MatchView {
        val source = highlightVideoSource(dto.match.configuration)
        return build(slug, dto, MatchConfiguration.VideoHighlight(source))
    }

    private fun build(
        slug: String,
        dto: SampleMatchDtoV2,
        configurationOverride: MatchConfiguration?,
    ): MatchView {
        val ids = List(sampleMatchRequiredIdCount(dto)) { UUID.randomUUID() }
        val conversion = convertSampleMatch(slug, dto, configurationOverride, ids)
        val timeline = buildTimeline(conversion.match, conversion.facts)
        return try {
            MatchView(
                slug = slug,
                conversion = conversion,
                timeline = timeline,
                // **`buildSummary` ではなくこちら。** 前者は `phaseSummaries` が空のままで、
                // 前後半別のスタッツが作れない。
                summary = buildSummaryWithTimeline(conversion.match, timeline),
                orderedFacts = TimelineOrdering.sorted(timeline.resolvedFacts),
                playersById = conversion.players.associateBy { it.id },
                teamsById = listOf(conversion.homeTeam, conversion.awayTeam).associateBy { it.id },
            )
        } catch (t: Throwable) {
            // ここから先で投げると resolver の所有者が居なくなる（MatchView が出来ていない）。
            timeline.destroy()
            throw t
        }
    }

    /**
     * ハイライト DTO から動画ソースを取り出す。
     *
     * **source は `decodeSampleConfiguration` の結果から取る**（DTO のフィールドを直接
     * 読まない）。`video` / `videoHighlight` のどちらで配信されていても同じ経路になり、
     * tagged union の読み替えを自前で持たずに済む。
     */
    private fun highlightVideoSource(dto: SampleMatchConfigurationDtoV2): VideoSource =
        when (val configuration = decodeSampleConfiguration(dto)) {
            is MatchConfiguration.Video -> requireYouTube(configuration.v1)
            is MatchConfiguration.VideoHighlight -> requireYouTube(configuration.v1)
            // 動画を持たない試合が index に混ざった場合。ハイライトとしては成立しない。
            is MatchConfiguration.Timer -> throw HighlightRouteException.TimerConfiguration()
        }

    /**
     * 再生できるのは YouTube だけ（`local` は端末内の PHAsset を指すので Android では開けない）。
     */
    private fun requireYouTube(source: VideoSource): VideoSource =
        if (source.provider == VideoProvider.YOUTUBE) {
            source
        } else {
            throw HighlightRouteException.NonYouTubeSource(source.provider)
        }
}

/**
 * ハイライト経路として成立しない DTO。
 *
 * メッセージは**開発者向けの診断**。UI に出さないこと（toolkit ADR 0002 決定 5）。
 */
sealed class HighlightRouteException(message: String) : Exception(message) {

    /** configuration が `timer`（動画を持たない）。 */
    class TimerConfiguration : HighlightRouteException("configuration=timer")

    /** provider が `youtube` でない。 */
    class NonYouTubeSource(provider: VideoProvider) :
        HighlightRouteException("provider=$provider")
}

/**
 * 配信から試合を取得して [MatchView] まで組み立てる。
 *
 * 取得の失敗（404 / 通信断 / 5xx）と変換の失敗を同じ [FeedResult] に載せるので、
 * 呼び出し側は結果の `when` を 1 回書けばよい。
 *
 * **成功したら [MatchView] の所有権は呼び出し側に移る。** 不要になったら `close()` すること
 * （ViewModel の `onCleared`）。
 *
 * **組み立ては [Dispatchers.Default] で回す。** 取得（IO）と違い CPU の仕事だが、
 * `convertSampleMatch` と `buildTimeline` は fact 数に比例して伸びる FFI 呼び出しで、
 * `viewModelScope`（= Main）で直に回すと最初のフレームが遅れる。
 */
suspend fun SampleFeed.loadMatchView(slug: String): FeedResult<MatchView> =
    when (val fetched = fetchMatch(slug)) {
        is FeedResult.Success -> withContext(Dispatchers.Default) {
            buildCatching { MatchViewBuilder.buildMatch(slug, fetched.value) }
        }
        is FeedResult.Failure -> fetched
    }

/** 配信からハイライトを取得して [MatchView] まで組み立てる（[loadMatchView] のハイライト版）。 */
suspend fun SampleFeed.loadHighlightView(slug: String): FeedResult<MatchView> =
    when (val fetched = fetchHighlight(slug)) {
        is FeedResult.Success -> withContext(Dispatchers.Default) {
            buildCatching { MatchViewBuilder.buildHighlight(slug, fetched.value) }
        }
        is FeedResult.Failure -> fetched
    }

/**
 * 組み立ての例外を [FeedResult.Malformed] に畳む。
 *
 * 例外そのものは診断としてだけ持ち回る（[FeedResult.Malformed.diagnostic]）。
 */
private inline fun buildCatching(build: () -> MatchView): FeedResult<MatchView> =
    try {
        FeedResult.Success(build())
    } catch (e: SampleDtoException) {
        FeedResult.Malformed(e.toString())
    } catch (e: HighlightRouteException) {
        FeedResult.Malformed(e.toString())
    }
