package com.handplus.handballrecorder.domain

import io.github.kinjoryura.handballtoolkit.ResolvedFact

/**
 * `TimelineProjection.resolvedFacts` を時系列に並べ替える。
 *
 * **コアは時系列で返さない。** `resolvedFacts` は型ごとにまとまっており、phase 開始が
 * 途中に現れる。並べ替えは表示の都合なのでシェルの責務（web デモの `demo.js` も同じ形の
 * 比較を持っている）。
 *
 * **比較子はここ 1 本だけにする。** 試合詳細とハイライト詳細が別々に `sortedBy` を書くと、
 * 同じ fact 列が画面ごとに違う順で並ぶ（iOS がラベルで 3 回踏んだのと同じ型の事故）。
 */
object TimelineOrdering {

    /**
     * 並べ替えキー。`resolvedMatchClock` → `resolvedVideoClock` → 「時刻なし」の順に落ちる。
     *
     * どちらの時計も解決できない fact（動画・試合どちらの時間軸にも置けない）は
     * `+∞` で末尾へ送る。**捨てない** — 記録された事実は必ず 1 行として出す。
     */
    fun sortKey(fact: ResolvedFact): Double =
        fact.resolvedMatchClock?.elapsedSeconds
            ?: fact.resolvedVideoClock?.elapsedSeconds
            ?: Double.POSITIVE_INFINITY

    /**
     * 昇順。**同値は元の index で安定化する。**
     *
     * 明示的に index を第 2 キーに置いてあるのは、時刻なしの fact が全て `+∞` で同値に
     * なるため（並べ替え実装の安定性に頼ると、実装が変わった日に順が入れ替わる）。
     */
    val comparator: Comparator<IndexedValue<ResolvedFact>> =
        compareBy({ sortKey(it.value) }, { it.index })

    /** [comparator] で並べ替えた新しいリストを返す（入力は変更しない）。 */
    fun sorted(facts: List<ResolvedFact>): List<ResolvedFact> =
        facts.withIndex().sortedWith(comparator).map { it.value }
}
