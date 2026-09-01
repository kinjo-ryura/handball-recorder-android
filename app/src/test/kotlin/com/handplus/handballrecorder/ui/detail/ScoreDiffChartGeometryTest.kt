package com.handplus.handballrecorder.ui.detail

import io.github.kinjoryura.handballtoolkit.ScoreProgressionPhaseSpan
import io.github.kinjoryura.handballtoolkit.ScoreProgressionPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * 得点差チャートの幾何。**iOS `ScoreDiffChartV2` から写した規則**を固定する。
 *
 * 描画そのもの（`Canvas`）は単体テストで触れないので、**点列 → 座標**の変換だけを
 * 純関数として切り出してここで押さえる。空・1 点・全部同値・最大差 0 の 4 つは
 * 「線が消える」「軸が潰れる」「0 除算で `NaN` が `Path` に流れる」のいずれも
 * 画面上では黙って起きるため、必ず含める。
 */
class ScoreDiffChartGeometryTest {

    private val width = 200f
    private val height = 100f

    // ── 横軸の端 ──

    @Test
    fun `横軸の端は最大リードだが 2 で下から押さえる`() {
        assertEquals(7, ScoreDiffChartGeometry.axisLimit(7L))
        assertEquals(2, ScoreDiffChartGeometry.axisLimit(2L))
        // 1 点差しか付かなかった試合でも軸を潰さない（iOS `max(2, …)`）。
        assertEquals(2, ScoreDiffChartGeometry.axisLimit(1L))
    }

    @Test
    fun `最大差が 0 でも軸は 2 で成立する`() {
        // コアは最低 1 を返す約束だが、0 が来ても図が潰れないことを固定しておく。
        assertEquals(2, ScoreDiffChartGeometry.axisLimit(0L))
    }

    // ── 横軸の目盛り ──

    @Test
    fun `横軸の目盛りは 4 分割を目安に対称へ並ぶ`() {
        // iOS: step = max(1, ceil(limit / 4)) を -limit から limit まで。
        assertEquals(listOf(-2, -1, 0, 1, 2), ScoreDiffChartGeometry.xAxisValues(2))
        assertEquals(listOf(-4, -3, -2, -1, 0, 1, 2, 3, 4), ScoreDiffChartGeometry.xAxisValues(4))
        assertEquals(listOf(-7, -5, -3, -1, 1, 3, 5, 7), ScoreDiffChartGeometry.xAxisValues(7))
    }

    @Test
    fun `横軸の目盛りに 0 が現れない刻みがある`() {
        // limit が奇数だと刻みが 0 を跨ぐ。**だから描画側はゼロ線を目盛りとは別に引く**
        // （[ScoreDiffChart]）。ここで「必ず 0 が入る」と思い込むと基準線が黙って消える。
        assertTrue(ScoreDiffChartGeometry.xAxisValues(4).contains(0))
        assertFalse(ScoreDiffChartGeometry.xAxisValues(7).contains(0))
    }

    // ── 縦軸の目盛り ──

    @Test
    fun `縦軸の目盛りは 5 分刻みで終端を含むまで並ぶ`() {
        assertEquals(listOf(0.0, 300.0, 600.0, 900.0), ScoreDiffChartGeometry.yTickSeconds(900.0))
        // 端数のある試合は次の刻みを跨がない（1200 は出さない）。
        assertEquals(listOf(0.0, 300.0, 600.0, 900.0), ScoreDiffChartGeometry.yTickSeconds(1000.0))
    }

    @Test
    fun `phase を渡すと目盛りは phase ごとに 0 から刻む`() {
        // 前半が 1800 秒ちょうどで終わらない実データの形。通しで刻むと後半の目盛りが
        // phase 内 299.4 秒（= 04:59 表示）に落ちるので、phase 起点で作り直す。
        val spans = listOf(
            span(regularIndex = 0, start = 0.0, end = 1790.0),
            span(regularIndex = 1, start = 1800.6, end = 3000.0),
        )
        val ticks = ScoreDiffChartGeometry.yTickSeconds(3000.0, spans)
        assertEquals(
            // 末尾は totalSeconds を跨がない（3000.6 は 3000.0 の外なので出さない）。
            listOf(0.0, 300.0, 600.0, 900.0, 1200.0, 1500.0, 1800.6, 2100.6, 2400.6, 2700.6),
            ticks,
        )
        // 目盛りのラベルは phase 内の切りのよい時刻になる。
        assertEquals("後半 05:00", ScoreDiffChartGeometry.timeLabel(2100.6, spans))
    }

    // ── 座標変換 ──

    @Test
    fun `得点差 0 は中央 負がホーム側の左 正がアウェイ側の右`() {
        assertEquals(100f, ScoreDiffChartGeometry.x(0L, 2, width), 0.001f)
        // diff = away - home なので、負 = ホームリード = 左。
        assertEquals(0f, ScoreDiffChartGeometry.x(-2L, 2, width), 0.001f)
        assertEquals(200f, ScoreDiffChartGeometry.x(2L, 2, width), 0.001f)
        assertTrue(ScoreDiffChartGeometry.x(-1L, 2, width) < 100f)
        assertTrue(ScoreDiffChartGeometry.x(1L, 2, width) > 100f)
    }

    @Test
    fun `軸の外へ出る得点差は端でクランプする`() {
        assertEquals(0f, ScoreDiffChartGeometry.x(-9L, 2, width), 0.001f)
        assertEquals(200f, ScoreDiffChartGeometry.x(9L, 2, width), 0.001f)
    }

    @Test
    fun `時間は上が開始 下が終了`() {
        assertEquals(0f, ScoreDiffChartGeometry.y(0.0, 1000.0, height), 0.001f)
        assertEquals(50f, ScoreDiffChartGeometry.y(500.0, 1000.0, height), 0.001f)
        assertEquals(100f, ScoreDiffChartGeometry.y(1000.0, 1000.0, height), 0.001f)
    }

    @Test
    fun `試合時間が 0 でも NaN を作らない`() {
        // 0 除算を許すと NaN が Path へ流れ、**図が丸ごと消える**（例外も出ない）。
        assertEquals(0f, ScoreDiffChartGeometry.y(0.0, 0.0, height), 0.001f)
        assertEquals(0f, ScoreDiffChartGeometry.y(120.0, 0.0, height), 0.001f)
    }

    // ── 点列 → 折れ線 ──

    @Test
    fun `点が無ければ折れ線も空`() {
        assertEquals(
            emptyList<ChartPoint>(),
            ScoreDiffChartGeometry.polyline(emptyList(), 2, 1000.0, width, height),
        )
    }

    @Test
    fun `点が 1 つでも落とさない`() {
        val points = ScoreDiffChartGeometry.polyline(
            points = listOf(point(seconds = 500.0, home = 1, away = 0)),
            axisLimit = 2,
            totalSeconds = 1000.0,
            width = width,
            height = height,
        )
        assertEquals(1, points.size)
        // home = 1 / away = 0 → diff = -1 → 中央より左。
        assertTrue(points[0].x < 100f)
        assertEquals(50f, points[0].y, 0.001f)
    }

    @Test
    fun `得点差がずっと同じなら縦一直線になる`() {
        val points = ScoreDiffChartGeometry.polyline(
            points = listOf(
                point(seconds = 0.0, home = 3, away = 3),
                point(seconds = 500.0, home = 5, away = 5),
                point(seconds = 1000.0, home = 8, away = 8),
            ),
            axisLimit = 2,
            totalSeconds = 1000.0,
            width = width,
            height = height,
        )
        assertEquals(listOf(100f, 100f, 100f), points.map { it.x })
        assertEquals(listOf(0f, 50f, 100f), points.map { it.y })
    }

    @Test
    fun `点の並びも点数も入力のまま`() {
        // step-doubling 済みの点列（同じ時刻に 2 点）を間引いたり畳んだりしない。
        val points = ScoreDiffChartGeometry.polyline(
            points = listOf(
                point(seconds = 300.0, home = 0, away = 0),
                point(seconds = 300.0, home = 1, away = 0),
            ),
            axisLimit = 2,
            totalSeconds = 600.0,
            width = width,
            height = height,
        )
        assertEquals(2, points.size)
        assertEquals(points[0].y, points[1].y, 0.001f)
        assertTrue(points[1].x < points[0].x)
    }

    // ── phase の区切り ──

    @Test
    fun `破線は 2 本目以降の phase 開始にだけ引く`() {
        val spans = listOf(span(0, 0.0, 1800.0), span(1, 1800.0, 3600.0))
        // 1 本目の開始は図の上端そのものなので引かない（iOS `dropFirst()`）。
        assertEquals(listOf(1800.0), ScoreDiffChartGeometry.phaseBoundarySeconds(spans))
    }

    @Test
    fun `phase が 1 本だけなら区切りは無い`() {
        assertEquals(
            emptyList<Double>(),
            ScoreDiffChartGeometry.phaseBoundarySeconds(listOf(span(0, 0.0, 1800.0))),
        )
    }

    // ── 縦軸のラベル ──

    @Test
    fun `時間ラベルは phase 名と phase 内経過`() {
        val spans = listOf(span(0, 0.0, 1800.0), span(1, 1800.0, 3600.0))
        assertEquals("前半 00:00", ScoreDiffChartGeometry.timeLabel(0.0, spans))
        assertEquals("前半 05:00", ScoreDiffChartGeometry.timeLabel(300.0, spans))
        // 境界ちょうどは前の phase の終端（iOS の `<= endSeconds` と同じ）。
        assertEquals("前半 30:00", ScoreDiffChartGeometry.timeLabel(1800.0, spans))
        assertEquals("後半 05:00", ScoreDiffChartGeometry.timeLabel(2100.0, spans))
    }

    @Test
    fun `最後の phase を越えた目盛りは終端に丸める`() {
        val spans = listOf(span(0, 0.0, 1800.0))
        assertEquals("前半 30:00", ScoreDiffChartGeometry.timeLabel(1900.0, spans))
    }

    @Test
    fun `phase が 1 つも無ければ通算の時刻だけ出す`() {
        assertEquals("05:00", ScoreDiffChartGeometry.timeLabel(300.0, emptyList()))
    }

    private fun point(seconds: Double, home: Long, away: Long) =
        ScoreProgressionPoint(
            cumulativeSeconds = seconds,
            homeScore = home,
            awayScore = away,
        )

    private fun span(regularIndex: Int, start: Double, end: Double) =
        ScoreProgressionPhaseSpan(
            phaseFactId = UUID.randomUUID(),
            regularIndex = regularIndex,
            startSeconds = start,
            endSeconds = end,
        )
}
