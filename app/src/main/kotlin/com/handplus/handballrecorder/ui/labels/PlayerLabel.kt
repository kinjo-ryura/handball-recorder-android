package com.handplus.handballrecorder.ui.labels

import io.github.kinjoryura.handballtoolkit.Player
import java.text.Collator
import java.util.Locale

/**
 * 選手の**表示**に関する規約（表示名と並び順）。iOS の `RecorderUIShared/PlayerDisplay.swift` の移植。
 *
 * **文言と並び順はシェル所有**（toolkit ADR 0002）なのでコアには置かない。
 */

/**
 * 一覧・ピッカー・イベント行で使う表示名。背番号があれば `#N 名前`、無ければ名前だけ。
 * 名前が背番号ラベルそのもの（`7番` 等）なら `#N` だけにする。
 */
val Player.displayName: String
    get() {
        val jersey = jerseyNumber ?: return name
        val beside = nameBesideJersey ?: return "#$jersey"
        return "#$jersey $beside"
    }

/**
 * 背番号を別に描く画面（バッジ + 名前）で、名前の欄に出す文字列。
 * 名前が背番号ラベルそのものなら null —— バッジと二重になるので出さない。
 */
val Player.nameBesideJersey: String?
    get() = if (isJerseyLabelName) null else name

/**
 * 名前が仮名化ラベル（`7番`）そのものか。
 *
 * 選手名が非公開の試合（アマチュアの公式ランニングスコア由来）は、配信時に
 * 親リポ `tools/promote-sample-matches` が名前を `{背番号}番` へ置き換える（`PLAYER_LABEL`）。
 * 素直に前置すると `#7 7番` と二重に読める。
 *
 * **判定は置き換え規約と同じ厳密一致にする。** 同じ規則が web デモ（`demo.js` の
 * `playerLabel`）・試合ページ生成（`tools/generate-match-pages` の `_player_label`）・
 * iOS（`PlayerDisplay.swift`）にあり、ここを緩めると 4 者で見え方がずれる。実在の名前
 * （`7` だけの登録名など）を勝手に隠さない狙いもある。
 */
val Player.isJerseyLabelName: Boolean
    get() {
        val jersey = jerseyNumber ?: return false
        // `"$jersey番"` と書くと `jersey番` という識別子として読まれる（Kotlin は日本語の
        // 識別子を許す）。波括弧を省かないこと。
        return name == "${jersey}番"
    }

/**
 * 背番号順（未設定は末尾）。背番号が同じ / どちらも未設定なら名前順。
 *
 * 名前は [Collator] で比べる — 日本語名を Unicode コード順で並べると読み順と合わず、
 * 同姓の並びが画面ごとに違って見える（Swift の `localizedStandardCompare` に相当）。
 */
fun List<Player>.sortedByJerseyNumber(): List<Player> = sortedWith(PlayerOrdering.byJerseyNumber)

/** 選手の並び順。比較子をここ 1 本にしておく（iOS では 8 箇所に写しがあり既にズレていた）。 */
object PlayerOrdering {

    private val collator: Collator = Collator.getInstance(Locale.JAPANESE)

    val byJerseyNumber: Comparator<Player> = Comparator { lhs, rhs ->
        val l = lhs.jerseyNumber
        val r = rhs.jerseyNumber
        when {
            l != null && r != null && l != r -> l.compareTo(r)
            // 背番号ありが先、無しが末尾。
            l != null && r == null -> -1
            l == null && r != null -> 1
            else -> collator.compare(lhs.name, rhs.name)
        }
    }
}
