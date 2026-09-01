package com.handplus.handballrecorder.ui.labels

import io.github.kinjoryura.handballtoolkit.Phase
import io.github.kinjoryura.handballtoolkit.PhaseKind
import io.github.kinjoryura.handballtoolkit.PhaseSummaryLine

/**
 * phase の日本語名。iOS の `RecorderUIShared/PhaseLabel.swift` の移植で、**分岐は同じ結果**になる。
 *
 * コアは phase に名前を持たない（`PhaseStart` fact の出現順だけが情報）ので、導出は UI 層の責務。
 *
 * **実装をここ 1 本に寄せること。** iOS では同じ導出が 3 箇所にあり、1 箇所だけ 5 期目以降を
 * 「第N期」と呼んでいて、同じ試合の同じ phase が画面によって「延長3」「第5期」と表示された
 * （親リポ #165 / #175 / #216）。**新しい呼び出し側で `when` を書き直さないこと。**
 */
object PhaseLabel {

    /**
     * regular phase の 0 始まりの出現順 → 役割名。
     *
     * 5 期目以降（`index >= 4`）は「延長3」「延長4」… と延長の通し番号で呼ぶ。
     * `index` は延長 1 本目が 2 なので、延長番号は `index - 1` になる。
     */
    fun regularName(index: Int): String = when (index) {
        0 -> "前半"
        1 -> "後半"
        2 -> "延長前半"
        3 -> "延長後半"
        else -> "延長${index - 1}"
    }

    /**
     * shootout（7m スローイングコンテスト）の表示名。
     *
     * **呼び出し側でこの文字列を書かないこと** — regular 側だけ [regularName] に寄せても、
     * shootout のリテラルが各画面に散れば同じ drift が起きる（親リポ #175）。
     */
    const val SHOOTOUT_NAME: String = "7mTC"

    /**
     * phase の種別と regular の出現順 → 表示ラベル。
     *
     * fact 列からラベルを組む呼び出し側（`SegmentResolver.allPhases()` は
     * [PhaseSummaryLine] ではなく [Phase] を返す）はこれを使う。
     * [regularIndex] は shootout では読まれない。
     */
    fun name(kind: PhaseKind, regularIndex: Int): String = when (kind) {
        PhaseKind.REGULAR -> regularName(regularIndex)
        PhaseKind.SHOOTOUT -> SHOOTOUT_NAME
    }

    /**
     * サマリ 1 行 → 表示ラベル。
     *
     * `regularIndex` が null の regular 行は 0（前半）に倒す — コアは regular に必ず index を
     * 付けるので実際には起きないが、ここで落とすと表がまるごと消える。
     */
    fun label(line: PhaseSummaryLine): String = name(line.kind, line.regularIndex ?: 0)
}
