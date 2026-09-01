package com.handplus.handballrecorder.ui.video

/**
 * `evaluateJavascript` へ渡す式を組み立てるためのリテラル化。
 *
 * **値を文字列連結で式に埋めない。** videoId は配信データ由来で、こちらが中身を決めていない。
 * 素直に `"initPlayer('" + videoId + "')"` と書くと、`'` を含む値が入った瞬間に任意の JS が
 * 走る形になる（iOS の `YouTubePlaybackClient.jsStringLiteral` と同じ理由でここを分けてある）。
 *
 * `org.json` を使わないのは、**JVM 単体テストで `org.json` がスタブ**になり検証できないため
 * （[YouTubeBridge.decode] が [FlatJson] を自前で持っているのと同じ事情）。
 */
object JsLiterals {

    /**
     * 任意の文字列を JS の文字列リテラル（二重引用符）にする。
     *
     * `"` / `\` / 制御文字に加えて **U+2028 / U+2029 も必ずエスケープする**
     * （JSON では素通しできるが、JS のソースに直接置くと行終端子として解釈されうる。
     * ここは「JSON を作る」のではなく「JS のソースに埋める」用途なので厳しい側に倒す）。
     * `/` はエスケープしない（URL を読みやすく保つ。iOS と同じ選択）。
     */
    fun string(value: String): String = buildString(value.length + 2) {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' || c == '\u007F' || c == '\u2028' || c == '\u2029' -> appendUnicode(c)
                else -> append(c)
            }
        }
        append('"')
    }

    /**
     * 秒数などの数値を JS の数値リテラルにする。
     *
     * **非有限（NaN / ±∞）は null を返す**。呼び出し側はそのまま何もしない
     * （`player.seekTo(NaN, true)` は JS としては通ってしまい、動画が無言で先頭へ飛ぶ）。
     *
     * [Double.toString] はロケールに依存しない（小数点は常に `.`）ので、
     * `String.format` を通さない。指数表記（`1.0E-5`）になっても JS の数値リテラルとして妥当。
     */
    fun number(value: Double): String? = if (value.isFinite()) value.toString() else null

    private fun StringBuilder.appendUnicode(c: Char) {
        append("\\u")
        append(c.code.toString(16).padStart(4, '0'))
    }
}
