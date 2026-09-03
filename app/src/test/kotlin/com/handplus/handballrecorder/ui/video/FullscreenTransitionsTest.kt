package com.handplus.handballrecorder.ui.video

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全画面の出入りの規則。**view も Activity も出てこない**ので JVM で回る。
 *
 * 固定したいのは `WebChromeClient` の契約と実害に直結する 3 点:
 * 二重に入らないこと・抜けるのが冪等であること・向きを入る前の値へ戻すこと。
 */
class FullscreenTransitionsTest {

    private val portrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private val unspecified = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @Test
    fun `初期状態は全画面ではない`() {
        val state = FullscreenState()
        assertFalse(state.isActive)
        assertEquals(unspecified, state.restoreOrientation)
    }

    @Test
    fun `入ると全画面になり 入る前の向きを覚える`() {
        val next = FullscreenTransitions.enter(FullscreenState(), portrait)
        assertNotNull(next)
        assertTrue(next!!.isActive)
        assertEquals(portrait, next.restoreOrientation)
    }

    @Test
    fun `全画面中の二重の enter は断る`() {
        val first = FullscreenTransitions.enter(FullscreenState(), portrait)!!
        // 後から来た onShowCustomView。**先の view を捨てない**（捨てると
        // onCustomViewHidden を呼ぶ相手が入れ替わり Chromium と食い違う）。
        assertNull(FullscreenTransitions.enter(first, unspecified))
    }

    @Test
    fun `抜けると全画面でなくなる`() {
        val entered = FullscreenTransitions.enter(FullscreenState(), portrait)!!
        val exited = FullscreenTransitions.exit(entered)
        assertNotNull(exited)
        assertFalse(exited!!.isActive)
    }

    @Test
    fun `全画面でないときの exit は何もしない`() {
        // 戻るキーで抜けると onCustomViewHidden() が Chromium 側の fullscreen を解き、
        // 続けて onHideCustomView が返ってくる。**2 度目が素通りしないと向きの復帰が
        // 二重に走る。**
        assertNull(FullscreenTransitions.exit(FullscreenState()))
    }

    @Test
    fun `抜けたら入る前の向きへ戻せる`() {
        val entered = FullscreenTransitions.enter(FullscreenState(), portrait)!!
        // 呼び出し側は exit() の前に restoreOrientation を読む決まり。
        assertEquals(portrait, entered.restoreOrientation)
        assertFalse(FullscreenTransitions.exit(entered)!!.isActive)
    }

    @Test
    fun `全画面の向きは左右どちらの横向きにも追従する`() {
        // LANDSCAPE だと片側に固定され、端末を逆さに持つと上下逆の映像になる。
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            FullscreenTransitions.FULLSCREEN_ORIENTATION,
        )
    }

    @Test
    fun `入って抜けてもう一度入れる`() {
        val entered = FullscreenTransitions.enter(FullscreenState(), portrait)!!
        val exited = FullscreenTransitions.exit(entered)!!
        val again = FullscreenTransitions.enter(exited, unspecified)
        assertNotNull(again)
        assertTrue(again!!.isActive)
        assertEquals(unspecified, again.restoreOrientation)
    }
}
