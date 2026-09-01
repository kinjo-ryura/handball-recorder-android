package com.handplus.handballrecorder.ui.labels

import io.github.kinjoryura.handballtoolkit.PlayEventKind
import io.github.kinjoryura.handballtoolkit.PlayerStatLine
import io.github.kinjoryura.handballtoolkit.TeamSummaryLine
import io.github.kinjoryura.handballtoolkit.scoringRate
import io.github.kinjoryura.handballtoolkit.shotAttempts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** `mm:ss` と成功率の整形、および記録種別ラベル。 */
class FormatTest {

    @Test
    fun `mmss は分をゼロ埋めして端数を切り捨てる`() {
        assertEquals("00:00", ClockFormat.mmss(0.0))
        assertEquals("00:07", ClockFormat.mmss(7.0))
        assertEquals("00:07", ClockFormat.mmss(7.9))
        assertEquals("01:00", ClockFormat.mmss(60.0))
        assertEquals("05:03", ClockFormat.mmss(303.4))
        assertEquals("30:00", ClockFormat.mmss(1800.0))
    }

    @Test
    fun `mmss は負値を 0 に倒す`() {
        assertEquals("00:00", ClockFormat.mmss(-0.5))
        assertEquals("00:00", ClockFormat.mmss(-120.0))
    }

    @Test
    fun `mmss は 60 分を超えても分が伸びるだけ`() {
        assertEquals("101:07", ClockFormat.mmss(101 * 60.0 + 7))
    }

    @Test
    fun `成功率は パーセント 得点 試投 の形`() {
        assertEquals("82% (41/50)", RateFormat.withFraction(41.0 / 50.0, goals = 41, attempts = 50))
        assertEquals("100% (3/3)", RateFormat.withFraction(1.0, goals = 3, attempts = 3))
        assertEquals("0% (0/4)", RateFormat.withFraction(0.0, goals = 0, attempts = 4))
    }

    @Test
    fun `試投 0 なら成功率は出さない`() {
        assertEquals("--", RateFormat.withFraction(null, goals = 0, attempts = 0))
        // 率が来ていても試投 0 なら出さない（割れていない）。
        assertEquals("--", RateFormat.withFraction(1.0, goals = 0, attempts = 0))
        assertEquals("-", RateFormat.percent(null))
    }

    @Test
    fun `試投 0 のチーム行はシムの scoringRate が null になり 表記も出ない`() {
        val line = TeamSummaryLine(`teamId` = UUID.randomUUID(), `goals` = 0, `shotMisses` = 0)
        // 試投数・成功率は自作せず .aar のシムから読む。
        assertEquals(0L, line.shotAttempts)
        assertEquals(null, line.scoringRate)
        assertEquals("--", RateFormat.withFraction(line))
    }

    @Test
    fun `選手行の成功率はシムの値をそのまま整形する`() {
        val line = PlayerStatLine(`playerId` = UUID.randomUUID(), `goals` = 41, `shotMisses` = 9)
        assertEquals(50L, line.shotAttempts)
        assertEquals("82% (41/50)", RateFormat.withFraction(line))
        assertEquals("82%", RateFormat.percent(line.scoringRate))
    }

    @Test
    fun `記録種別の日本語は 6 値すべて埋まっている`() {
        assertEquals("得点", PlayEventKind.GOAL.label)
        assertEquals("シュートミス", PlayEventKind.SHOT_MISSED.label)
        assertEquals("メモ", PlayEventKind.FREE_NOTE.label)
        assertEquals("イエローカード", PlayEventKind.YELLOW_CARD.label)
        assertEquals("2分間退場", PlayEventKind.TWO_MINUTE_SUSPENSION.label)
        assertEquals("レッドカード", PlayEventKind.RED_CARD.label)
        // enum が増えたらここも増やす（本体の when はコンパイラが網羅を見る）。
        assertEquals(6, PlayEventKind.entries.size)
    }
}
