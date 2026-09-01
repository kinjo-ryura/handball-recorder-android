package com.handplus.handballrecorder.ui.labels

import io.github.kinjoryura.handballtoolkit.PhaseKind
import io.github.kinjoryura.handballtoolkit.PhaseSummaryLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/**
 * phase ラベルの導出。**iOS の `PhaseLabelTests` と同じ答えになること**を固定する。
 *
 * 画面ごとに `when` を書き直すと同じ phase が別名になる（親リポ #165 / #175 / #216）ので、
 * 分岐の全網羅をここで押さえておく。
 */
class PhaseLabelTest {

    @Test
    fun `regular は出現順で前半 後半 延長前半 延長後半`() {
        assertEquals("前半", PhaseLabel.regularName(0))
        assertEquals("後半", PhaseLabel.regularName(1))
        assertEquals("延長前半", PhaseLabel.regularName(2))
        assertEquals("延長後半", PhaseLabel.regularName(3))
    }

    @Test
    fun `5 期目以降は延長の通し番号になる`() {
        // index 4 は延長 3 本目（延長 1 本目が index 2）。
        assertEquals("延長3", PhaseLabel.regularName(4))
        assertEquals("延長4", PhaseLabel.regularName(5))
        assertEquals("延長9", PhaseLabel.regularName(10))
    }

    @Test
    fun `shootout は index を読まず 7mTC`() {
        assertEquals("7mTC", PhaseLabel.name(PhaseKind.SHOOTOUT, regularIndex = 0))
        assertEquals("7mTC", PhaseLabel.name(PhaseKind.SHOOTOUT, regularIndex = 3))
        assertEquals(PhaseLabel.SHOOTOUT_NAME, PhaseLabel.name(PhaseKind.SHOOTOUT, regularIndex = 99))
    }

    @Test
    fun `name は regular で regularName と一致する`() {
        for (index in 0..6) {
            assertEquals(PhaseLabel.regularName(index), PhaseLabel.name(PhaseKind.REGULAR, index))
        }
    }

    @Test
    fun `regular が 1 個だけの試合（前半のみ）`() {
        val lines = listOf(summaryLine(PhaseKind.REGULAR, 0))
        assertEquals(listOf("前半"), lines.map { PhaseLabel.label(it) })
    }

    @Test
    fun `regular が 4 個の試合は前半 後半 延長前半 延長後半`() {
        val lines = (0..3).map { summaryLine(PhaseKind.REGULAR, it) }
        assertEquals(
            listOf("前半", "後半", "延長前半", "延長後半"),
            lines.map { PhaseLabel.label(it) },
        )
    }

    @Test
    fun `regular 4 個以上と shootout が混在しても取り違えない`() {
        val lines = (0..4).map { summaryLine(PhaseKind.REGULAR, it) } +
            summaryLine(PhaseKind.SHOOTOUT, regularIndex = null)
        assertEquals(
            listOf("前半", "後半", "延長前半", "延長後半", "延長3", "7mTC"),
            lines.map { PhaseLabel.label(it) },
        )
    }

    @Test
    fun `regularIndex が null の regular は前半に倒す`() {
        // コアは regular に必ず index を付けるので実際には起きないが、
        // ここで落とすと前後半別の表がまるごと消える。
        assertEquals("前半", PhaseLabel.label(summaryLine(PhaseKind.REGULAR, regularIndex = null)))
    }

    private fun summaryLine(kind: PhaseKind, regularIndex: Int?) = PhaseSummaryLine(
        `phaseFactId` = UUID.randomUUID(),
        `kind` = kind,
        `regularIndex` = regularIndex,
    )
}
