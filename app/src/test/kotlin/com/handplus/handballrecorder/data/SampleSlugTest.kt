package com.handplus.handballrecorder.data

import com.handplus.handballrecorder.ui.labels.FeedErrorLabel
import com.handplus.handballrecorder.ui.labels.FeedSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * slug の検証。取得 URL のパスへそのまま埋まるので、形が合わないものは取りに行かせない。
 */
class SampleSlugTest {

    @Test
    fun `配信中の slug と同じ形は通る`() {
        assertTrue(SampleSlug.isValid("jhl-2025-04-12-toyota-vs-daido"))
        assertTrue(SampleSlug.isValid("a"))
        assertTrue(SampleSlug.isValid("0"))
        assertTrue(SampleSlug.isValid("abc123"))
        assertTrue(SampleSlug.isValid("A-1"))
    }

    @Test
    fun `空文字は通らない`() {
        assertFalse(SampleSlug.isValid(""))
    }

    @Test
    fun `パスを遡る文字列は通らない`() {
        assertFalse(SampleSlug.isValid("../index"))
        assertFalse(SampleSlug.isValid("a/../b"))
        assertFalse(SampleSlug.isValid("a/b"))
        assertFalse(SampleSlug.isValid(".."))
    }

    @Test
    fun `先頭がハイフンや記号なら通らない`() {
        assertFalse(SampleSlug.isValid("-abc"))
        assertFalse(SampleSlug.isValid("_abc"))
        assertFalse(SampleSlug.isValid(".abc"))
    }

    @Test
    fun `途中に空白や記号があれば通らない`() {
        assertFalse(SampleSlug.isValid("a b"))
        assertFalse(SampleSlug.isValid("a_b"))
        assertFalse(SampleSlug.isValid("a.json"))
        assertFalse(SampleSlug.isValid("a?b=1"))
        assertFalse(SampleSlug.isValid("a%2Fb"))
        assertFalse(SampleSlug.isValid("試合"))
    }

    @Test
    fun `改行を混ぜても通らない`() {
        assertFalse(SampleSlug.isValid("abc\n"))
        assertFalse(SampleSlug.isValid("abc\n../x"))
    }

    @Test
    fun `64 文字までは通り 65 文字は通らない`() {
        assertTrue(SampleSlug.isValid("a".repeat(64)))
        assertFalse(SampleSlug.isValid("a".repeat(65)))
    }

    @Test
    fun `形の合わない slug は 見つかりません と同じ文言になる`() {
        // 配信の URL になり得ない文字列なので、ユーザーには 404 と同じことを言う。
        assertEquals(
            FeedErrorLabel.message(FeedResult.NotFound, FeedSubject.MATCH),
            FeedErrorLabel.message(FeedResult.InvalidSlug, FeedSubject.MATCH),
        )
    }

    @Test
    fun `通信の失敗は 見つかりません に倒さない`() {
        // ここを一緒くたにすると「正しい URL に見つかりませんが出る」（親リポ #211）。
        val notFound = FeedErrorLabel.message(FeedResult.NotFound, FeedSubject.MATCH)
        val unreachable = FeedErrorLabel.message(FeedResult.Unreachable("timeout"), FeedSubject.MATCH)
        val serverError = FeedErrorLabel.message(FeedResult.HttpStatus(503), FeedSubject.MATCH)
        val malformed = FeedErrorLabel.message(FeedResult.Malformed("bad json"), FeedSubject.MATCH)
        assertEquals(FeedErrorLabel.NETWORK, unreachable)
        assertEquals(FeedErrorLabel.MALFORMED, malformed)
        assertTrue(serverError.contains("503"))
        assertFalse(notFound == unreachable)
        assertFalse(notFound == serverError)
        assertFalse(notFound == malformed)
    }

    @Test
    fun `見つかりません の主語は試合とハイライトで変わる`() {
        assertTrue(FeedErrorLabel.notFound(FeedSubject.MATCH).startsWith("この試合"))
        assertTrue(FeedErrorLabel.notFound(FeedSubject.HIGHLIGHT).startsWith("このハイライト"))
    }

    @Test
    fun `診断文字列は文言に混ざらない`() {
        // 例外の message / detail は開発者向け（ADR 0002 決定 5）。
        val message = FeedErrorLabel.message(
            FeedResult.Malformed("SampleDtoException\$InvalidJson: detail=expected value at line 1"),
            FeedSubject.MATCH,
        )
        assertFalse(message.contains("SampleDtoException"))
        assertFalse(message.contains("line 1"))
    }
}
