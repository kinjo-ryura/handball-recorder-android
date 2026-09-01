package com.handplus.handballrecorder.ui.video

/**
 * ホスト HTML（`assets/youtube_player.html`）から `@JavascriptInterface` 経由で届くイベント。
 *
 * 種類は iOS の `YouTubeBridgeEvent` と同じ 4 つ。**HTML 側の `JSON.stringify` の形も
 * iOS と同じ**なので、片方を変えるならもう片方も変える。
 */
sealed interface YouTubeBridgeEvent {

    /** `onReady`。プレイヤーが出来た（位置はまだ着地していない）。 */
    data object Ready : YouTubeBridgeEvent

    /** `onError`。@param code YouTube のエラーコード（2 / 5 / 100 / 101 / 150）。 */
    data class Error(val code: Int?) : YouTubeBridgeEvent

    /** `onStateChange`。@param state `YT.PlayerState`（-1 / 0 / 1 / 2 / 3 / 5）。 */
    data class StateChange(val state: Int) : YouTubeBridgeEvent

    /** `onPlaybackRateChange`。このアプリは速度を変えないが、iOS と口を揃えて受けておく。 */
    data class PlaybackRateChange(val rate: Double) : YouTubeBridgeEvent
}

/**
 * JS → ネイティブの受け口。
 *
 * **[NAME] は HTML の `window.AndroidYouTube` と綴りが一致していること。**
 * 片方だけ変えるとイベントが 1 件も届かなくなり、しかも例外も出ない
 * （HTML 側は `undefined.postMessage` で静かに落ちる）。
 */
object YouTubeBridge {

    /** `addJavascriptInterface` に渡す名前 = HTML 側の `window.AndroidYouTube`。 */
    const val NAME: String = "AndroidYouTube"

    /**
     * 受信した JSON 文字列を [YouTubeBridgeEvent] へ変換する**純関数**。
     *
     * 不正 JSON / 未対応 type / 必須フィールド欠落は null（＝黙って捨てる）。
     * `addJavascriptInterface` で注入したオブジェクトは**ページ内の全フレームから見える**ので、
     * 埋め込んだ YouTube の iframe が呼ぶことも原理的にはありうる。届くのが再生状態だけで
     * 実害は無いが、**素性の分からない入力として扱う**（型が合わないものは通さない）。
     *
     * JSON の解釈を `org.json` に任せていないのは、**JVM 単体テストでは `org.json` が
     * スタブ**（呼ぶと既定値を返すだけ）で、この関数を CI で検証できなくなるため。
     * 依存を増やさずに検証可能にする、が [FlatJson] を自前で持っている理由。
     */
    fun decode(message: String): YouTubeBridgeEvent? {
        val json = FlatJson.parseObject(message) ?: return null
        return when (json["type"] as? String) {
            "ready" -> YouTubeBridgeEvent.Ready
            "error" -> YouTubeBridgeEvent.Error(json.intOrNull("code"))
            "stateChange" -> json.intOrNull("state")?.let { YouTubeBridgeEvent.StateChange(it) }
            "playbackRateChange" ->
                (json["rate"] as? Double)?.takeIf { it.isFinite() }
                    ?.let { YouTubeBridgeEvent.PlaybackRateChange(it) }

            else -> null
        }
    }

    private fun Map<String, Any>.intOrNull(key: String): Int? =
        (this[key] as? Double)?.takeIf { it.isFinite() }?.toInt()
}

/**
 * 平坦な JSON オブジェクト（値は文字列 / 数値 / 真偽値 / null のみ）を読む最小の parser。
 *
 * **入れ子（`{` / `[`）は受け付けない。** ホスト HTML が送るのは 1 階層のオブジェクトだけで、
 * それ以上を解釈できることに用が無い。受け付けないほうが「想定外の形が来たら捨てる」を
 * 素直に書ける。数値はすべて [Double] で持つ（JSON に整数型は無い）。
 *
 * 末尾にゴミが付いている入力は**失敗**にする（部分一致で通さない）。
 */
internal object FlatJson {

    fun parseObject(text: String): Map<String, Any>? {
        val scanner = Scanner(text)
        scanner.skipWhitespace()
        if (!scanner.expect('{')) return null
        val out = LinkedHashMap<String, Any>()
        scanner.skipWhitespace()
        if (scanner.expect('}')) return out.takeIf { scanner.isAtEnd() }
        while (true) {
            scanner.skipWhitespace()
            val key = scanner.readString() ?: return null
            scanner.skipWhitespace()
            if (!scanner.expect(':')) return null
            scanner.skipWhitespace()
            when (val parsed = scanner.readValue()) {
                null -> return null
                // null は「そのキーが無い」と同じ扱いにする（呼び出し側の分岐を増やさない）。
                Parsed.NullLiteral -> Unit
                is Parsed.Value -> out[key] = parsed.value
            }
            scanner.skipWhitespace()
            if (scanner.expect(',')) continue
            if (scanner.expect('}')) return out.takeIf { scanner.isAtEnd() }
            return null
        }
    }

    private sealed interface Parsed {
        data object NullLiteral : Parsed
        data class Value(val value: Any) : Parsed
    }

    private class Scanner(private val text: String) {

        private var pos = 0

        fun isAtEnd(): Boolean {
            skipWhitespace()
            return pos >= text.length
        }

        fun skipWhitespace() {
            while (pos < text.length && isWhitespace(text[pos])) pos++
        }

        fun expect(c: Char): Boolean {
            if (pos >= text.length || text[pos] != c) return false
            pos++
            return true
        }

        fun readString(): String? {
            if (!expect('"')) return null
            val sb = StringBuilder()
            while (pos < text.length) {
                when (val c = text[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> if (!readEscape(sb)) return null
                    // 生の制御文字は JSON の文字列に入れられない。
                    else -> if (c < ' ') return null else sb.append(c)
                }
            }
            return null
        }

        private fun readEscape(sb: StringBuilder): Boolean {
            if (pos >= text.length) return false
            when (text[pos++]) {
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                '/' -> sb.append('/')
                'b' -> sb.append('\u0008')
                'f' -> sb.append('\u000C')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'u' -> {
                    if (pos + 4 > text.length) return false
                    val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: return false
                    sb.append(code.toChar())
                    pos += 4
                }

                else -> return false
            }
            return true
        }

        fun readValue(): Parsed? {
            val c = text.getOrNull(pos) ?: return null
            return when {
                c == '"' -> readString()?.let { Parsed.Value(it) }
                // 入れ子は受け付けない（ホスト HTML は 1 階層しか送らない）。
                c == '{' || c == '[' -> null
                text.startsWith("true", pos) -> { pos += 4; Parsed.Value(true) }
                text.startsWith("false", pos) -> { pos += 5; Parsed.Value(false) }
                text.startsWith("null", pos) -> { pos += 4; Parsed.NullLiteral }
                else -> readNumber()
            }
        }

        private fun readNumber(): Parsed? {
            val start = pos
            while (pos < text.length && isNumberChar(text[pos])) pos++
            if (pos == start) return null
            // `toDoubleOrNull` は "1d" や "0x1p3" のような Java 固有の記法も通すので、
            // 文字集合をここで JSON の数値に絞ってから渡す。
            val value = text.substring(start, pos).toDoubleOrNull() ?: return null
            return if (value.isFinite()) Parsed.Value(value) else null
        }

        private fun isWhitespace(c: Char): Boolean =
            c == ' ' || c == '\t' || c == '\n' || c == '\r'

        private fun isNumberChar(c: Char): Boolean =
            c in '0'..'9' || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E'
    }
}
