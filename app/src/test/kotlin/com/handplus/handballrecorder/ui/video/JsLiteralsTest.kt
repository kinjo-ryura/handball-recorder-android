package com.handplus.handballrecorder.ui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JS へ渡す値のリテラル化。**文字列連結で式を組まない**ことの担保。
 *
 * videoId は配信データ由来で中身をこちらが決めていないので、
 * 「引用符を含む値が来ても式の構造が変わらない」ことをここで固定する。
 */
class JsLiteralsTest {

    @Test
    fun `素直な文字列は引用符で包むだけ`() {
        assertEquals("\"dQw4w9WgXcQ\"", JsLiterals.string("dQw4w9WgXcQ"))
    }

    @Test
    fun `二重引用符とバックスラッシュを逃がす`() {
        assertEquals("\"a\\\"b\"", JsLiterals.string("a\"b"))
        assertEquals("\"a\\\\b\"", JsLiterals.string("a\\b"))
    }

    @Test
    fun `単引用符は逃がさない`() {
        // 二重引用符で包んでいるので不要（URL や ID を読みやすく保つ）。
        assertEquals("\"it's\"", JsLiterals.string("it's"))
    }

    @Test
    fun `注入を試す値でも式の構造が変わらない`() {
        // 連結で組んでいたら `initPlayer("x"); alert(1); //", ...)` になる形。
        val literal = JsLiterals.string("x\"); alert(1); //")
        assertEquals("\"x\\\"); alert(1); //\"", literal)
    }

    @Test
    fun `改行とタブと制御文字を逃がす`() {
        assertEquals("\"a\\nb\"", JsLiterals.string("a\nb"))
        assertEquals("\"a\\rb\"", JsLiterals.string("a\rb"))
        assertEquals("\"a\\tb\"", JsLiterals.string("a\tb"))
        assertEquals("\"a\\u0000b\"", JsLiterals.string("a\u0000b"))
    }

    @Test
    fun `行区切り文字も逃がす`() {
        // U+2028 / U+2029 は JSON では素通しできるが、JS のソースに直接置くと
        // 行終端子として解釈されうる。ここは「JS に埋める」用途なので逃がす。
        assertEquals("\"a\\u2028b\"", JsLiterals.string("a\u2028b"))
        assertEquals("\"a\\u2029b\"", JsLiterals.string("a\u2029b"))
    }

    @Test
    fun `日本語はそのまま通す`() {
        assertEquals("\"前半\"", JsLiterals.string("前半"))
    }

    @Test
    fun `数値はロケールに依らず小数点で書く`() {
        assertEquals("97.0", JsLiterals.number(97.0))
        assertEquals("0.0", JsLiterals.number(0.0))
        assertEquals("1.5", JsLiterals.number(1.5))
    }

    @Test
    fun `非有限は null`() {
        // `player.seekTo(NaN, true)` は JS としては通ってしまい、動画が無言で先頭へ飛ぶ。
        assertNull(JsLiterals.number(Double.NaN))
        assertNull(JsLiterals.number(Double.POSITIVE_INFINITY))
        assertNull(JsLiterals.number(Double.NEGATIVE_INFINITY))
    }
}
