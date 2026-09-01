package com.handplus.handballrecorder.ui.labels

import io.github.kinjoryura.handballtoolkit.PhaseSummaryLine
import io.github.kinjoryura.handballtoolkit.PlayerStatLine
import io.github.kinjoryura.handballtoolkit.TeamSummaryLine
import io.github.kinjoryura.handballtoolkit.awayAttempts
import io.github.kinjoryura.handballtoolkit.awayRate
import io.github.kinjoryura.handballtoolkit.homeAttempts
import io.github.kinjoryura.handballtoolkit.homeRate
import io.github.kinjoryura.handballtoolkit.scoringRate
import io.github.kinjoryura.handballtoolkit.shotAttempts
import kotlin.math.roundToInt

/**
 * シュート成功率の表示。iOS の `MatchSummaryViewV2` と同じ 2 書式を持つ。
 *
 * **試投数と成功率を自分で計算しないこと。** `.aar` のシム（`ProjectionsDerived.kt`）が
 * `shotAttempts` / `scoringRate` を拡張プロパティで供給しており、0 除算のガードもそこにある。
 * 下のオーバーロードはその値を読むだけで、四則演算をこの層に持ち込まないための入口。
 */
object RateFormat {

    /** 試投 0（率が出せない）ときの表記。チーム別 / 前後半別のセル用。 */
    const val UNAVAILABLE_DETAIL: String = "--"

    /** 試投 0 のときの表記。選手別セル用（1 文字ぶん狭い）。 */
    const val UNAVAILABLE_PERCENT: String = "-"

    /**
     * `82% (41/50)` 形式。率が無い（試投 0）なら [UNAVAILABLE_DETAIL]。
     *
     * 端数は四捨五入（web デモの `Math.round` と同じ）。
     */
    fun withFraction(rate: Double?, goals: Long, attempts: Long): String {
        if (rate == null || attempts <= 0L) return UNAVAILABLE_DETAIL
        return "${percentValue(rate)}% ($goals/$attempts)"
    }

    /** `82%` 形式。率が無いなら [UNAVAILABLE_PERCENT]。 */
    fun percent(rate: Double?): String {
        if (rate == null) return UNAVAILABLE_PERCENT
        return "${percentValue(rate)}%"
    }

    /** チーム別セル。 */
    fun withFraction(line: TeamSummaryLine): String =
        withFraction(line.scoringRate, line.goals, line.shotAttempts)

    /** 選手別セル（詳細つき）。 */
    fun withFraction(line: PlayerStatLine): String =
        withFraction(line.scoringRate, line.goals, line.shotAttempts)

    /** 前後半別セルのホーム側。 */
    fun homeWithFraction(line: PhaseSummaryLine): String =
        withFraction(line.homeRate, line.homeGoals, line.homeAttempts)

    /** 前後半別セルのアウェイ側。 */
    fun awayWithFraction(line: PhaseSummaryLine): String =
        withFraction(line.awayRate, line.awayGoals, line.awayAttempts)

    private fun percentValue(rate: Double): Int = (rate * 100.0).roundToInt()
}
