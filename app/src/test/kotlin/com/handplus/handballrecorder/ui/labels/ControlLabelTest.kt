package com.handplus.handballrecorder.ui.labels

import io.github.kinjoryura.handballtoolkit.FactAnchor
import io.github.kinjoryura.handballtoolkit.MatchClock
import io.github.kinjoryura.handballtoolkit.StoppageKind
import io.github.kinjoryura.handballtoolkit.StoppagePayload
import org.junit.Assert.assertEquals
import org.junit.Test

/** 中断行の文言。iOS `EventRowView.controlLabel` と同じ語であることを固定する。 */
class ControlLabelTest {

    @Test
    fun `タイムアウトは種別名をそのまま出す`() {
        assertEquals("タイムアウト", ControlLabel.stoppage(payload(StoppageKind.TIMEOUT)))
    }

    @Test
    fun `理由の無い中断は中断と出す`() {
        assertEquals("中断", ControlLabel.stoppage(payload(StoppageKind.PAUSE)))
    }

    @Test
    fun `理由が書かれていればそれを見出しにする`() {
        assertEquals("怪我の手当て", ControlLabel.stoppage(payload(StoppageKind.PAUSE, note = "怪我の手当て")))
    }

    @Test
    fun `空白だけの理由は中断に落とす`() {
        assertEquals("中断", ControlLabel.stoppage(payload(StoppageKind.PAUSE, note = "   ")))
    }

    @Test
    fun `タイムアウトの理由欄は読まない`() {
        // 種別そのものが文言になるので、note があっても上書きしない（iOS と同じ）。
        assertEquals("タイムアウト", ControlLabel.stoppage(payload(StoppageKind.TIMEOUT, note = "作戦")))
    }

    private fun payload(kind: StoppageKind, note: String? = null) = StoppagePayload(
        `kind` = kind,
        `startAnchor` = FactAnchor.MatchClock(MatchClock(0.0)),
        `endAnchor` = null,
        `note` = note,
    )
}
