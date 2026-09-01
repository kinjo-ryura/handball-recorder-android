package com.handplus.handballrecorder.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ホスト HTML が `JSON.stringify` で送ってくる 4 種のイベントを読めること、
 * **想定外の形は黙って捨てる**ことを固定する。
 *
 * `addJavascriptInterface` で注入したオブジェクトはページ内の全フレームから見えるので、
 * ここは「自分が作った文字列」ではなく**素性の分からない入力**として扱う。
 */
class YouTubeBridgeTest {

    @Test
    fun `HTML と綴りが一致していること`() {
        // 片方だけ変えるとイベントが 1 件も届かず、しかも例外も出ない。
        assertEquals("AndroidYouTube", YouTubeBridge.NAME)
    }

    @Test
    fun `ready を読む`() {
        assertEquals(YouTubeBridgeEvent.Ready, YouTubeBridge.decode("""{"type":"ready"}"""))
    }

    @Test
    fun `error はコードつきで読む`() {
        assertEquals(
            YouTubeBridgeEvent.Error(150),
            YouTubeBridge.decode("""{"type":"error","code":150}"""),
        )
    }

    @Test
    fun `error のコードが無ければ null のまま通す`() {
        // 「再生できない」という事実だけは失わない（コードは UI に出さないので必須ではない）。
        assertEquals(YouTubeBridgeEvent.Error(null), YouTubeBridge.decode("""{"type":"error"}"""))
        assertEquals(
            YouTubeBridgeEvent.Error(null),
            YouTubeBridge.decode("""{"type":"error","code":null}"""),
        )
    }

    @Test
    fun `stateChange を読む`() {
        assertEquals(
            YouTubeBridgeEvent.StateChange(1),
            YouTubeBridge.decode("""{"type":"stateChange","state":1}"""),
        )
        assertEquals(
            YouTubeBridgeEvent.StateChange(-1),
            YouTubeBridge.decode("""{"type":"stateChange","state":-1}"""),
        )
    }

    @Test
    fun `stateChange の state が無ければ捨てる`() {
        // 状態が分からない stateChange は何も決められない。
        assertNull(YouTubeBridge.decode("""{"type":"stateChange"}"""))
        assertNull(YouTubeBridge.decode("""{"type":"stateChange","state":"1"}"""))
    }

    @Test
    fun `playbackRateChange は小数で読む`() {
        assertEquals(
            YouTubeBridgeEvent.PlaybackRateChange(1.5),
            YouTubeBridge.decode("""{"type":"playbackRateChange","rate":1.5}"""),
        )
        // JS が整数で寄越しても同じ（JSON に整数型は無い）。
        assertEquals(
            YouTubeBridgeEvent.PlaybackRateChange(1.0),
            YouTubeBridge.decode("""{"type":"playbackRateChange","rate":1}"""),
        )
    }

    @Test
    fun `未対応の type は捨てる`() {
        assertNull(YouTubeBridge.decode("""{"type":"onApiChange"}"""))
        assertNull(YouTubeBridge.decode("""{"state":1}"""))
    }

    @Test
    fun `壊れた JSON は捨てる`() {
        assertNull(YouTubeBridge.decode(""))
        assertNull(YouTubeBridge.decode("ready"))
        // 閉じ括弧が無い（raw string だと末尾の `"` が終端と紛れるので通常の文字列で書く）。
        assertNull(YouTubeBridge.decode("{\"type\":\"ready\""))
        assertNull(YouTubeBridge.decode("""{"type":ready}"""))
        assertNull(YouTubeBridge.decode("""["ready"]"""))
    }

    @Test
    fun `末尾にゴミが付いた入力は捨てる`() {
        // 部分一致で通すと、後ろに何を足しても素通りする形になる。
        assertNull(YouTubeBridge.decode("""{"type":"ready"} trailing"""))
        assertNull(YouTubeBridge.decode("""{"type":"ready"}{"type":"error"}"""))
    }

    @Test
    fun `入れ子は受け付けない`() {
        // ホスト HTML が送るのは 1 階層だけ。想定外の形は解釈せず捨てる。
        assertNull(YouTubeBridge.decode("""{"type":"error","code":{"a":1}}"""))
        assertNull(YouTubeBridge.decode("""{"type":"error","code":[150]}"""))
    }

    @Test
    fun `空白と改行は無視する`() {
        assertEquals(
            YouTubeBridgeEvent.StateChange(3),
            YouTubeBridge.decode(" {\n \"type\" : \"stateChange\" ,\t\"state\" : 3 \n} "),
        )
    }

    @Test
    fun `エスケープを含む文字列を読む`() {
        // type は既知の値としか一致しないので、エスケープが崩れれば捨てられる側に倒れる。
        assertEquals(
            YouTubeBridgeEvent.Ready,
            YouTubeBridge.decode("""{"note":"a\"b\\c\nあ","type":"ready"}"""),
        )
    }

    @Test
    fun `非有限の数値は値として扱わない`() {
        // JSON に NaN / Infinity は無い。読めない数値は捨てる。
        assertNull(YouTubeBridge.decode("""{"type":"stateChange","state":NaN}"""))
        assertNull(YouTubeBridge.decode("""{"type":"stateChange","state":1e400}"""))
    }
}
