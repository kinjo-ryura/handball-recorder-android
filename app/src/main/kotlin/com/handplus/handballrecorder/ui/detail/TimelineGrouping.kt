package com.handplus.handballrecorder.ui.detail

import com.handplus.handballrecorder.ui.labels.PhaseLabel
import io.github.kinjoryura.handballtoolkit.ControlFact
import io.github.kinjoryura.handballtoolkit.FactId
import io.github.kinjoryura.handballtoolkit.MatchFactPayload
import io.github.kinjoryura.handballtoolkit.Phase
import io.github.kinjoryura.handballtoolkit.PhaseKind
import io.github.kinjoryura.handballtoolkit.ResolvedFact
import io.github.kinjoryura.handballtoolkit.TeamId

/**
 * タイムラインの 1 グループ。**phase が見出し、その phase に属する fact が行**。
 *
 * @property phaseFactId この見出しに対応する `PhaseStart` fact の ID。phase を持たない
 *   （まだ phase が始まっていない / 動画時刻しか解決できない）fact のグループでは null
 * @property label 見出しの文言。null のグループは見出しを描かない
 * @property facts このグループに並ぶ行。**`PhaseStart` は含まれない**（見出しになるので）
 */
data class TimelineGroup(
    val phaseFactId: FactId?,
    val label: String?,
    val facts: List<ResolvedFact>,
)

/**
 * `orderedFacts` を phase ごとのグループへ畳む。**画面から切り出した純関数**。
 *
 * 規則は iOS（Mac の `PhaseGroupingCacheMac.buildPhaseGroups` / iOS の `buildPhaseLabels`）の移植:
 *
 * - **possession は行にしない**（親リポ #217）。CV 出力を取り込むと 1 試合に 110〜200 件入り、
 *   一覧の 57〜72% を占めて得点やカードが埋もれる。畳んでも 83〜88% にしかならないので、
 *   列そのものから外す
 * - **`PhaseStart` は行ではなく見出し**。iOS は行として出しているが、あちらは記録画面で
 *   phase 開始そのものを選んで編集する必要がある。見る専用のこの画面ではその操作が無いので、
 *   見出しに畳んで縦を節約する（**意図的な差分**）
 * - **stoppage（タイムアウト / 中断）は行**。試合の流れを説明する事実なので残す
 * - phase の逆引きは `resolver.phaseForMatchElapsed`。**画面側で `when (kind)` を書かない**
 */
object TimelineGrouping {

    /**
     * `PhaseStart` fact の ID → 見出し文言。
     *
     * **regular の出現順は `allPhases()` を頭から数える**（コアは phase に名前を持たない）。
     * iOS の `buildPhaseLabels` と同じで、**index は 0 始まり**なので加算は代入の後に行う
     * （先に上げると 1 phase ずつ後ろへずれる）。
     */
    fun phaseLabels(phases: List<Phase>): Map<FactId, String> {
        var regularIndex = 0
        val result = LinkedHashMap<FactId, String>(phases.size)
        for (phase in phases) {
            result[phase.factId] = PhaseLabel.name(phase.kind, regularIndex)
            if (phase.kind == PhaseKind.REGULAR) regularIndex += 1
        }
        return result
    }

    /**
     * fact 列 → グループ列。
     *
     * @param orderedFacts `MatchView.orderedFacts`（時系列に並べ替え済み）。
     *   **ここで並べ替え直さない** — 順序の規則は `TimelineOrdering` 1 本
     * @param phaseLabelByFactId [phaseLabels] の結果
     * @param phaseFactIdAt 試合経過秒 → その時刻が属する `PhaseStart` fact の ID。
     *   実体は `resolver.phaseForMatchElapsed(seconds)?.factId`。
     *   **関数で受けるのはテストのため**（`SegmentResolver` はネイティブのハンドル）
     */
    fun groups(
        orderedFacts: List<ResolvedFact>,
        phaseLabelByFactId: Map<FactId, String>,
        phaseFactIdAt: (Double) -> FactId?,
    ): List<TimelineGroup> {
        val groups = mutableListOf<TimelineGroup>()
        var currentKey: FactId? = null
        var started = false
        var bucket = mutableListOf<ResolvedFact>()

        fun flush() {
            // 見出しの無い空グループだけ捨てる。**phase の見出しは行が 0 件でも残す** —
            // 記録の無い phase を丸ごと消すと「後半が無い試合」に見えてしまう。
            if (!started) return
            if (currentKey == null && bucket.isEmpty()) return
            groups += TimelineGroup(
                phaseFactId = currentKey,
                label = currentKey?.let { phaseLabelByFactId[it] },
                facts = bucket,
            )
        }

        for (resolved in orderedFacts) {
            val payload = resolved.fact.payload
            // possession は行にしない（#217）。
            if (payload is MatchFactPayload.Possession) continue

            val isPhaseStart = payload is MatchFactPayload.Control &&
                payload.v1 is ControlFact.PhaseStart
            val key = if (isPhaseStart) {
                resolved.fact.id
            } else {
                resolved.resolvedMatchClock?.let { phaseFactIdAt(it.elapsedSeconds) }
            }

            if (!started || key != currentKey) {
                flush()
                currentKey = key
                started = true
                bucket = mutableListOf()
            }
            // phaseStart 自身は見出しなので行に積まない。
            if (!isPhaseStart) bucket += resolved
        }
        flush()
        return groups
    }

    /**
     * この play fact を行の**左**セルへ出すか。
     *
     * **チーム帰属は左右の位置で表す**（色や記号ではない）。`isHomeOnLeft` はコートの実配置に
     * 合わせて試合ごとに切り替わる記録なので、`Match` の値をそのまま尊重する
     * （iOS `EventRowView.shouldShow` と同じ）。
     *
     * チームが分からない fact（`freeNote` など）は左に出す。
     */
    fun isOnLeft(teamId: TeamId?, homeTeamId: TeamId, isHomeOnLeft: Boolean): Boolean {
        if (teamId == null) return true
        return (teamId == homeTeamId) == isHomeOnLeft
    }
}
