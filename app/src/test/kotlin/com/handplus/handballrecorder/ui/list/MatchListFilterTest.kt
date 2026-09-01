package com.handplus.handballrecorder.ui.list

import io.github.kinjoryura.handballtoolkit.SampleMatchSummaryV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

/**
 * 一覧の動画フィルタ。**判定は index summary の `hasVideo` を正とする**（本体 JSON を見ない）。
 */
class MatchListFilterTest {

    @Test
    fun `既定は動画ありに絞る`() {
        // 配信 46 件の大半が PDF 由来で動画なし。絞らないと動画つきが埋もれる（iOS #105）。
        assertEquals(VideoFilter.WITH_VIDEO, MatchListUiState().videoFilter)
    }

    @Test
    fun `動画ありだけを残す`() {
        val items = listOf(summary("a", hasVideo = true), summary("b", hasVideo = false))
        assertEquals(listOf("a"), items.filteredBy(VideoFilter.WITH_VIDEO).map { it.slug })
    }

    @Test
    fun `動画なしだけを残す`() {
        val items = listOf(summary("a", hasVideo = true), summary("b", hasVideo = false))
        assertEquals(listOf("b"), items.filteredBy(VideoFilter.WITHOUT_VIDEO).map { it.slug })
    }

    @Test
    fun `すべては何も落とさない`() {
        val items = listOf(summary("a", hasVideo = true), summary("b", hasVideo = false))
        assertEquals(listOf("a", "b"), items.filteredBy(VideoFilter.ALL).map { it.slug })
    }

    @Test
    fun `配信 index の並びを変えない`() {
        // 配信側で日付降順に確定済みなので、絞った後も配列順のまま出す。
        val items = listOf(
            summary("newest", hasVideo = false),
            summary("middle", hasVideo = true),
            summary("oldest", hasVideo = true),
        )
        assertEquals(
            listOf("newest", "middle", "oldest"),
            items.filteredBy(VideoFilter.ALL).map { it.slug },
        )
        assertEquals(
            listOf("middle", "oldest"),
            items.filteredBy(VideoFilter.WITH_VIDEO).map { it.slug },
        )
    }

    @Test
    fun `すべては同じリストをそのまま返す`() {
        val items = listOf(summary("a", hasVideo = true))
        assertSame(items, items.filteredBy(VideoFilter.ALL))
    }

    @Test
    fun `該当が無ければ空になる`() {
        val items = listOf(summary("a", hasVideo = false))
        assertEquals(emptyList<String>(), items.filteredBy(VideoFilter.WITH_VIDEO).map { it.slug })
    }

    @Test
    fun `visibleMatches は取得中と失敗では空になる`() {
        assertEquals(emptyList<SampleMatchSummaryV2>(), MatchListUiState().visibleMatches)
    }

    @Test
    fun `visibleMatches は成功時にフィルタを適用する`() {
        val state = MatchListUiState(
            matches = FeedUiState.Success(
                listOf(summary("a", hasVideo = true), summary("b", hasVideo = false)),
            ),
        )
        assertEquals(listOf("a"), state.visibleMatches.map { it.slug })
        assertEquals(
            listOf("a", "b"),
            state.copy(videoFilter = VideoFilter.ALL).visibleMatches.map { it.slug },
        )
    }

    private fun summary(slug: String, hasVideo: Boolean) = SampleMatchSummaryV2(
        `slug` = slug,
        `displayName` = slug,
        `description` = null,
        `date` = Instant.EPOCH,
        `homeScore` = 0,
        `awayScore` = 0,
        `hasVideo` = hasVideo,
    )
}
