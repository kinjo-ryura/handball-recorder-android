package com.handplus.handballrecorder.ui.detail

import com.handplus.handballrecorder.ui.labels.ClockFormat
import com.handplus.handballrecorder.ui.labels.PhaseLabel
import io.github.kinjoryura.handballtoolkit.ScoreProgressionPhaseSpan
import io.github.kinjoryura.handballtoolkit.ScoreProgressionPoint
import io.github.kinjoryura.handballtoolkit.diff
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round

/**
 * 描画平面上の 1 点（px・左上原点）。
 *
 * **Compose の `Offset` を使わない。** [ScoreDiffChartGeometry] を Compose にも Android にも
 * 依存しない純粋な計算として保ち、`:app:testDebugUnitTest`（JVM）でそのまま固定できるようにするため。
 * 描画側が `Offset` へ移すのは 1 行で済む。
 */
data class ChartPoint(val x: Float, val y: Float)

/**
 * 得点差チャートの**幾何だけ**を持つ純関数群。iOS `MatchSummaryViewV2.ScoreDiffChartV2` の移植。
 *
 * iOS は Swift Charts（`Chart` / `LineMark` / `RuleMark` / `chartXScale`）に軸とスケールを任せて
 * いるが、Android 側は**グラフライブラリを足さない**方針なので Compose の `Canvas` で自前で描く。
 * そのぶん「点列 → 座標」の変換がこちらの責務になるので、**描画（[ScoreDiffChart]）から
 * 切り離してここに置き、単体テストで固定する**。
 *
 * ## iOS から写した規則（変えないこと）
 *
 * - **横軸 = 得点差**。中央が 0、**左がホームリード / 右がアウェイリード**。
 *   コアの `diff` は `awayScore - homeScore`（`.aar` のシム `ProjectionsDerived.kt`）なので、
 *   負が左・正が右という素直な向きでそのまま置ける
 * - **縦軸 = 試合の経過時間で、上が開始・下が終了**。iOS は `y = -cumulativeSeconds` を
 *   `-totalSeconds...0` の domain に載せて（Swift Charts の y は上向き）同じ向きを作っている。
 *   こちらは px 座標が下向きなので符号を反転せず [y] がそのまま単調増加する
 * - **step は描かない — すでに点列が階段になっている**。コアの `points` は step-doubling 済みで
 *   1 得点につき「得点前」「得点後」の 2 点を持つ（`ScoreProgressionProjection` の doc）。
 *   だから折れ線で素直に繋ぐと階段になる（iOS も `.interpolationMethod(.linear)`）。
 *   **描画側で階段を組み立て直さないこと**
 * - **phase の区切りは 2 本目以降の regular phase の開始に破線を引く**（[phaseBoundarySeconds]）。
 *   1 本目（前半の開始）は図の上端そのものなので引かない
 * - **ゼロ線は縦線として常に引く**（得点差 0 の位置）。他のグリッドより濃くする
 *
 * ## 意図的な差分
 *
 * iOS のズームスライダ（1〜4 倍で縦方向にスクロールする）は入れていない。図全体を 1 画面に
 * 収める描き方にしてあり、縦スクロールするリストの中に**入れ子のスクロール領域を作らない**ため。
 */
object ScoreDiffChartGeometry {

    /**
     * 横軸の端（絶対値）の下限。
     *
     * 1 点差しか付かなかった試合でも軸が潰れないようにする（iOS `axisLimit` の `max(2, …)`）。
     */
    const val MIN_AXIS_LIMIT: Int = 2

    /** 縦軸のグリッド間隔（秒）。iOS と同じ 5 分。 */
    const val Y_TICK_SECONDS: Double = 300.0

    /** 秒の比較に使う許容誤差（iOS の `1e-6`）。 */
    private const val EPSILON: Double = 1e-6

    /** 横軸の端。`maxAbsDiff`（コアが出す最大リード）を [MIN_AXIS_LIMIT] で下から押さえる。 */
    fun axisLimit(maxAbsDiff: Long): Int = max(MIN_AXIS_LIMIT, maxAbsDiff.toInt())

    /**
     * 横軸の目盛り値（得点差）。`-limit` から `limit` まで、**4 分割を目安にした刻み**で並べる。
     *
     * iOS の `stride(from: -limit, through: limit, by: step)` と同じ列になる。
     * 表示は絶対値（左右どちらも「何点リードか」を読む軸なので符号は出さない）。
     */
    fun xAxisValues(axisLimit: Int): List<Int> {
        val limit = max(MIN_AXIS_LIMIT, axisLimit)
        val step = max(1, ceil(limit / 4.0).toInt())
        return (-limit..limit step step).toList()
    }

    /**
     * 縦軸の目盛り（試合の累積経過秒）。0 秒から [Y_TICK_SECONDS] 刻みで、`totalSeconds` を含むまで。
     *
     * iOS は負値で返す（Swift Charts の y が上向きのため）が、こちらは px 座標に合わせて正で返す。
     *
     * [minSeparationSeconds] は**隣り合うラベルが重ならないために要る最小の間隔**（秒）。
     * 描画側がラベルの実測高から換算して渡す（→ [ScoreDiffChart]）。0 なら間引かない。
     */
    fun yTickSeconds(
        totalSeconds: Double,
        spans: List<ScoreProgressionPhaseSpan> = emptyList(),
        minSeparationSeconds: Double = 0.0,
    ): List<Double> {
        if (spans.isEmpty()) {
            val ticks = mutableListOf<Double>()
            var seconds = 0.0
            while (seconds <= totalSeconds + 0.5) {
                ticks += seconds
                seconds += Y_TICK_SECONDS
            }
            return ticks
        }
        // **phase ごとに 0 から刻む。** 累積秒を通しで刻むと、phase の境目が 5 分の倍数から
        // ずれている試合（前半が 1800 秒ちょうどで終わらない = 実際の配信データの大半）で
        // 後半の目盛りが `04:59` のような半端な位置に落ちる。目盛りは「phase 内の 5 分」を
        // 指したいので、位置そのものを phase 起点で作る。
        val ticks = mutableListOf<Double>()
        for ((index, span) in spans.withIndex()) {
            val phaseTicks = mutableListOf<Double>()
            var within = 0.0
            while (span.startSeconds + within <= span.endSeconds + 0.5 &&
                span.startSeconds + within <= totalSeconds + 0.5
            ) {
                phaseTicks += span.startSeconds + within
                within += Y_TICK_SECONDS
            }
            // **phase の末尾の目盛りは、次の phase の開始に近すぎれば落とす。**
            // 刻みは phase 起点なので、前半が 5 分の倍数で終わらない試合（= 配信データの大半）
            // では最後の目盛りが境界の直前に落ちる。鹿児島 vs 富山（前半 1501 秒 / 通算 3000 秒）
            // だと最後の刻み 1500 秒と境界が **1 秒 = 0.1dp** しか離れず、行高 16dp のラベルが
            // ほぼ完全に重なったうえ、境界の破線がその潰れた帯を貫く（親リポ #278）。
            // **落とすのは手前側**（次の phase の 0 分は破線の位置そのもので、図の意味の
            // 区切りを担うため）。phase の先頭は phase 名を出す唯一の目盛りなので残す。
            val nextStart = spans.getOrNull(index + 1)?.startSeconds
            if (nextStart != null && minSeparationSeconds > 0.0) {
                while (phaseTicks.size > 1 && nextStart - phaseTicks.last() < minSeparationSeconds) {
                    phaseTicks.removeAt(phaseTicks.lastIndex)
                }
            }
            ticks += phaseTicks
        }
        return ticks
    }

    /**
     * 得点差 → 横位置（px）。中央が 0、左がホームリード。
     *
     * 軸の外にはみ出す値は端でクランプする（`maxAbsDiff` から作った [axisLimit] を使う限り
     * 起きないが、呼び出し側が別の軸を渡したときに描画が枠外へ出ないように）。
     */
    fun x(diff: Long, axisLimit: Int, width: Float): Float {
        val limit = max(MIN_AXIS_LIMIT, axisLimit)
        val ratio = (diff.toDouble() + limit) / (2.0 * limit)
        return (ratio.coerceIn(0.0, 1.0) * width).toFloat()
    }

    /**
     * 累積経過秒 → 縦位置（px）。**上が開始・下が終了**。
     *
     * `totalSeconds` が 0 以下（記録が 1 点だけ / 時間が解決できない）のときは全点を上端に置く。
     * ここで 0 除算すると `NaN` が `Path` へ流れ、**図全体が黙って消える**。
     */
    fun y(seconds: Double, totalSeconds: Double, height: Float): Float {
        if (totalSeconds <= 0.0) return 0f
        val ratio = (seconds / totalSeconds).coerceIn(0.0, 1.0)
        return (ratio * height).toFloat()
    }

    /**
     * 点列 → 折れ線の座標列。**並びも点数も入力のまま**（間引きも階段の再構成もしない）。
     */
    fun polyline(
        points: List<ScoreProgressionPoint>,
        axisLimit: Int,
        totalSeconds: Double,
        width: Float,
        height: Float,
    ): List<ChartPoint> = points.map { point ->
        ChartPoint(
            x = x(point.diff, axisLimit, width),
            y = y(point.cumulativeSeconds, totalSeconds, height),
        )
    }

    /**
     * 破線を引く位置（累積秒）。**2 本目以降の regular phase の開始**。
     *
     * 先頭を落とすのは、1 本目の開始が図の上端そのもので線を引く意味が無いため（iOS `dropFirst()`）。
     */
    fun phaseBoundarySeconds(spans: List<ScoreProgressionPhaseSpan>): List<Double> =
        spans.drop(1).map { it.startSeconds }

    /**
     * その目盛りが phase 境界（= [phaseBoundarySeconds] が破線を引く位置）かどうか。
     *
     * 描画側は**ここで実線のグリッドを引かない**。同じ位置に実線と破線を重ねると破線の隙間が
     * 実線で埋まり、phase の区切りという意味が図から消えるため（親リポ #278）。
     */
    fun isPhaseBoundary(seconds: Double, spans: List<ScoreProgressionPhaseSpan>): Boolean =
        phaseBoundarySeconds(spans).any { abs(it - seconds) <= EPSILON }

    /**
     * 累積経過秒 → 縦軸のラベル（`前半 05:00`）。
     *
     * その秒が属する phase を見つけ、**phase 内の経過**を `mm:ss` にする（試合通算ではない）。
     * 最後の phase の終端を越えた目盛りは、iOS と同じくその phase の終端に丸める。
     *
     * **名前も書式もここで作らない** — phase 名は [PhaseLabel]、時刻は [ClockFormat] が持つ
     * （画面ごとに `when` を書くと同じ phase が別名になる。親リポ #165 / #175 / #216）。
     */
    fun timeLabel(cumulativeSeconds: Double, spans: List<ScoreProgressionPhaseSpan>): String {
        val span = spans.firstOrNull { cumulativeSeconds <= it.endSeconds + EPSILON }
        if (span != null) {
            return label(span.regularIndex, cumulativeSeconds - span.startSeconds)
        }
        val last = spans.lastOrNull() ?: return ClockFormat.mmss(cumulativeSeconds)
        return label(last.regularIndex, last.endSeconds - last.startSeconds)
    }

    private fun label(regularIndex: Int, withinSeconds: Double): String =
        // 目盛りは累積秒の 5 分刻みで置くが、phase の境目は 5 分の倍数とは限らない
        // （前半が 1799.6 秒で終われば後半の目盛りは phase 内 299.6 秒になる）。
        // ClockFormat は切り捨てなので、丸めずに渡すと `後半 04:59` と出る。
        // **表示だけを最近傍へ丸める**（目盛りの位置そのものは動かさない）。
        "${PhaseLabel.regularName(regularIndex)} ${ClockFormat.mmss(round(withinSeconds))}"
}
