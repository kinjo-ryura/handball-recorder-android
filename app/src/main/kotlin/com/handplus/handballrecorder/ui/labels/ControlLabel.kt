package com.handplus.handballrecorder.ui.labels

import io.github.kinjoryura.handballtoolkit.StoppagePayload
import io.github.kinjoryura.handballtoolkit.StoppageKind

/**
 * control fact（進行の事実）の日本語名。iOS の `EventRowView.controlLabel` と同じ語。
 *
 * phase 開始のラベルは [PhaseLabel] が持つ（出現順から導出するため、fact 単体では決まらない）。
 * ここにあるのは stoppage（タイムアウト / 中断）だけ。
 *
 * **`when` を呼び出し側で書き直さないこと**（[PlayEventKindLabel][label] と同じ理由）。
 */
object ControlLabel {

    /** 選手やチームを伴わない行の既定文言。 */
    const val UNKNOWN_PLAYER: String = "不明"

    /**
     * 中断 1 件の表示名。
     *
     * `pause` は記録者が理由（怪我 / VAR など）を `note` に書ける。書かれていれば
     * それを見出しにし、無ければ「中断」に落とす（iOS と同じ）。
     */
    fun stoppage(payload: StoppagePayload): String = when (payload.kind) {
        StoppageKind.TIMEOUT -> "タイムアウト"
        StoppageKind.PAUSE -> payload.note?.takeIf { it.isNotBlank() } ?: "中断"
    }
}
