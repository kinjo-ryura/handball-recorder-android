package com.handplus.handballrecorder.ui.labels

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** 試合日の表記。ゼロ埋めしない `2026年4月10日` 形式に固定する。 */
class DateLabelTest {

    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    @Test
    fun `年月日を日本語で出す`() {
        assertEquals(
            "2026年4月10日",
            DateLabel.ymd(Instant.parse("2026-04-10T03:00:00Z"), tokyo),
        )
    }

    @Test
    fun `月日はゼロ埋めしない`() {
        assertEquals(
            "2026年1月5日",
            DateLabel.ymd(Instant.parse("2026-01-05T03:00:00Z"), tokyo),
        )
    }

    @Test
    fun `タイムゾーンで日付が変わる`() {
        // 配信の date は UTC の瞬間。端末のタイムゾーンで日付に落とす。
        val instant = Instant.parse("2026-04-09T15:30:00Z")
        assertEquals("2026年4月10日", DateLabel.ymd(instant, tokyo))
        assertEquals("2026年4月9日", DateLabel.ymd(instant, ZoneId.of("UTC")))
    }
}
