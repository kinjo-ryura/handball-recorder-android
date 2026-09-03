package com.handplus.handballrecorder.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.kinjoryura.handballtoolkit.ScoreProgressionProjection
import kotlin.math.abs

/**
 * 得点差の推移。**Compose の `Canvas` で自前で描く**（グラフライブラリを足さない方針）。
 *
 * 図の意味・軸の取り方・phase の区切り・ゼロ線は iOS `ScoreDiffChartV2` と同じで、
 * 規則と座標変換は [ScoreDiffChartGeometry] が持つ。**この関数は描くだけ**で、
 * 数の判断（軸の端 / 目盛り / 座標）を書き足さないこと。
 *
 * 呼び出し側は `progression` が null の試合（得点がまだ無い等）では**カードごと出さない**
 * （[MatchSummaryScreen]）。ここに「記録なし」の分岐は持たない。
 */
@Composable
internal fun ScoreDiffChart(
    progression: ScoreProgressionProjection,
    homeTeamName: String,
    awayTeamName: String,
    modifier: Modifier = Modifier,
) {
    // 既定のキャッシュは 8 件で、時間ラベルは 60 分の試合で 13 本出る（毎フレーム測り直しになる）。
    val measurer = rememberTextMeasurer(cacheSize = LABEL_CACHE_SIZE)
    // 色とテキストスタイルは `Canvas` の外（= Composable の文脈）でしか読めないので先に確定させる。
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroLineColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // どちら側がどちらのリードか。図そのものには符号を出さない（軸ラベルは絶対値）ので、
        // この 1 行が向きの唯一の手掛かりになる（iOS `axisHeader` と同じ）。
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "← $homeTeamName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$awayTeamName →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            val gutterLeft = Y_LABEL_GUTTER.toPx()
            val gutterTop = X_LABEL_GUTTER.toPx()
            val plotWidth = size.width - gutterLeft
            val plotHeight = size.height - gutterTop
            if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

            val axisLimit = ScoreDiffChartGeometry.axisLimit(progression.maxAbsDiff)
            val total = progression.totalSeconds

            // ── 縦のグリッド（横軸 = 得点差）──
            ScoreDiffChartGeometry.xAxisValues(axisLimit).forEach { value ->
                val px = gutterLeft + ScoreDiffChartGeometry.x(value.toLong(), axisLimit, plotWidth)
                drawLine(
                    color = gridColor,
                    start = Offset(px, gutterTop),
                    end = Offset(px, size.height),
                    strokeWidth = GRID_WIDTH.toPx(),
                )
                // 目盛りは絶対値（左右どちらも「何点リードか」を読む）。
                val layout = measurer.measure("${abs(value)}", labelStyle)
                val maxLabelLeft = (size.width - layout.size.width).coerceAtLeast(0f)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = (px - layout.size.width / 2f).coerceIn(0f, maxLabelLeft),
                        y = 0f,
                    ),
                )
            }

            // ── ゼロ線。**グリッドとは別に必ず引く**（iOS も `RuleMark(x: 0)` を
            // `AxisMarks` とは独立に置いている）。目盛りの刻みは `limit / 4` を丸めた値なので、
            // limit が奇数だと**目盛りに 0 が現れない**（limit = 7 → ±7, ±5, ±3, ±1）。
            // グリッドの `value == 0` で兼ねると、そういう試合で基準線が消える。
            val zeroX = gutterLeft + ScoreDiffChartGeometry.x(0L, axisLimit, plotWidth)
            drawLine(
                color = zeroLineColor,
                start = Offset(zeroX, gutterTop),
                end = Offset(zeroX, size.height),
                strokeWidth = ZERO_LINE_WIDTH.toPx(),
            )

            // ── 横のグリッドと時間ラベル（縦軸 = 経過時間。上が開始）──
            // ラベル 1 行が縦に占める秒数。目盛りは phase 起点で刻むので、前半が 5 分の倍数で
            // 終わらない試合では末尾の目盛りが境界の直前に落ち、ラベル同士が重なる（親リポ #278）。
            // **重なるかどうかの判定は幾何側**（[ScoreDiffChartGeometry.yTickSeconds]）に任せ、
            // ここは実測したラベル高を秒へ換算して渡すだけにする。
            val labelConstraints = Constraints(maxWidth = gutterLeft.toInt())
            val labelHeight = measurer.measure(
                text = ScoreDiffChartGeometry.timeLabel(0.0, progression.phaseSpans),
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = labelConstraints,
            ).size.height
            val minSeparationSeconds = if (plotHeight > 0f && total > 0.0) {
                total * (labelHeight + LABEL_MIN_GAP.toPx()) / plotHeight
            } else {
                0.0
            }

            ScoreDiffChartGeometry.yTickSeconds(
                totalSeconds = total,
                spans = progression.phaseSpans,
                minSeparationSeconds = minSeparationSeconds,
            ).forEach { seconds ->
                val py = gutterTop + ScoreDiffChartGeometry.y(seconds, total, plotHeight)
                // 境界の目盛りには下で破線を引く。ここで実線も引くと**破線が塗り潰されて
                // 実線に見える**ので、phase の区切りという意味が図から消える。
                if (!ScoreDiffChartGeometry.isPhaseBoundary(seconds, progression.phaseSpans)) {
                    drawLine(
                        color = gridColor,
                        start = Offset(gutterLeft, py),
                        end = Offset(size.width, py),
                        strokeWidth = GRID_WIDTH.toPx(),
                    )
                }
                val layout = measurer.measure(
                    text = ScoreDiffChartGeometry.timeLabel(seconds, progression.phaseSpans),
                    style = labelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = labelConstraints,
                )
                // 目盛りの中央に置くが、上下は図の中へ押し込める（0 秒のラベルが
                // 得点差ラベルの行へせり上がると重なって読めなくなる）。
                val maxLabelTop = (size.height - layout.size.height).coerceAtLeast(gutterTop)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = 0f,
                        y = (py - layout.size.height / 2f).coerceIn(gutterTop, maxLabelTop),
                    ),
                )
            }

            // ── phase の区切り（2 本目以降の regular phase 開始）──
            val dash = PathEffect.dashPathEffect(
                floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx()),
            )
            ScoreDiffChartGeometry.phaseBoundarySeconds(progression.phaseSpans).forEach { seconds ->
                val py = gutterTop + ScoreDiffChartGeometry.y(seconds, total, plotHeight)
                drawLine(
                    color = zeroLineColor,
                    start = Offset(gutterLeft, py),
                    end = Offset(size.width, py),
                    strokeWidth = ZERO_LINE_WIDTH.toPx(),
                    pathEffect = dash,
                )
            }

            // ── 折れ線。点列は step-doubling 済みなので素直に繋ぐと階段になる ──
            val points = ScoreDiffChartGeometry.polyline(
                points = progression.points,
                axisLimit = axisLimit,
                totalSeconds = total,
                width = plotWidth,
                height = plotHeight,
            )
            when (points.size) {
                0 -> Unit
                // 1 点だけの試合は `Path` に線分が無く何も描かれないので、点として置く。
                1 -> drawCircle(
                    color = lineColor,
                    radius = LINE_WIDTH.toPx(),
                    center = Offset(gutterLeft + points[0].x, gutterTop + points[0].y),
                )

                else -> {
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val px = gutterLeft + point.x
                        val py = gutterTop + point.y
                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }

        Text(
            text = "縦は試合時間（上が開始）、横は得点差。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 図の高さ。5 分ごとの時間ラベルが重ならない程度に取る（60 分の試合で 13 本）。 */
private val CHART_HEIGHT = 340.dp

/** 時間ラベル（`延長前半 15:00`）を置く左の余白。 */
private val Y_LABEL_GUTTER = 76.dp

/** 得点差ラベルを置く上の余白。 */
private val X_LABEL_GUTTER = 20.dp

/**
 * 隣り合う時間ラベルの間に最低限空ける余白。
 *
 * 0 だとラベルの箱がちょうど接する（＝重なりはしないが文字が詰まって読めない）ので、
 * 行の高さに上乗せして間引きの閾値にする。
 */
private val LABEL_MIN_GAP = 2.dp

private val GRID_WIDTH = 1.dp
private val ZERO_LINE_WIDTH = 1.dp
private val LINE_WIDTH = 2.dp
private val DASH_ON = 4.dp
private val DASH_OFF = 3.dp

/** 軸ラベルの測定結果を持ち回す数（時間ラベル 13 本 + 得点差ラベル 9 本を見込む）。 */
private const val LABEL_CACHE_SIZE = 32
