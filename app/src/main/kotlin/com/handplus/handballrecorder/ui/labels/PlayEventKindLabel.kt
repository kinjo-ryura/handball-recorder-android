package com.handplus.handballrecorder.ui.labels

import io.github.kinjoryura.handballtoolkit.PlayEventKind

/**
 * 記録種別（`PlayEventKind`）の日本語名。iOS の `RecorderUIShared/PlayEventKindLabel.swift` と
 * web デモ（`demo.js` の `PLAY_KIND_LABELS`）と同じ語。
 *
 * **文言はシェル所有**（toolkit ADR 0002）なのでコアには持たせない。**実装はここ 1 本**で、
 * **新しい呼び出し側で `when` やリテラルを書き直さないこと**（iOS では Picker だけ
 * 「ゴール / シュート失敗」で、同じ fact が記録ボタンでは「得点」と表示されていた。親リポ #176）。
 *
 * `when` はコアの enum に対して網羅なので、種別が増えればコンパイルが落ちて気付ける。
 */
val PlayEventKind.label: String
    get() = when (this) {
        PlayEventKind.GOAL -> "得点"
        PlayEventKind.SHOT_MISSED -> "シュートミス"
        PlayEventKind.FREE_NOTE -> "メモ"
        PlayEventKind.YELLOW_CARD -> "イエローカード"
        PlayEventKind.TWO_MINUTE_SUSPENSION -> "2分間退場"
        PlayEventKind.RED_CARD -> "レッドカード"
    }
