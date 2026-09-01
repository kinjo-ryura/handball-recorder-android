package com.handplus.handballrecorder.ui.labels

import io.github.kinjoryura.handballtoolkit.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * 選手の表示名。**`{背番号}番` と厳密一致するときだけ `#N` に落とす**規則を固定する。
 *
 * 同じ規則が web デモ（`demo.js`）・試合ページ生成（`tools/generate-match-pages`）・
 * iOS（`PlayerDisplay.swift`）にあり、緩めると 4 者で見え方がずれる。
 */
class PlayerLabelTest {

    @Test
    fun `名前が背番号ラベルそのものなら番号だけにする`() {
        val player = player(name = "7番", jersey = 7)
        assertTrue(player.isJerseyLabelName)
        assertEquals("#7", player.displayName)
        assertNull(player.nameBesideJersey)
    }

    @Test
    fun `2 桁の背番号でも同じ`() {
        assertEquals("#23", player(name = "23番", jersey = 23).displayName)
    }

    @Test
    fun `実名なら番号を前置する`() {
        val player = player(name = "山田太郎", jersey = 7)
        assertFalse(player.isJerseyLabelName)
        assertEquals("#7 山田太郎", player.displayName)
        assertEquals("山田太郎", player.nameBesideJersey)
    }

    @Test
    fun `背番号が名前の一部に含まれるだけでは落とさない`() {
        // 「7番」を含むが一致はしない。厳密一致にしてあるのは、実在の名前を
        // 勝手に隠さないため。
        assertEquals("#7 背番号7番の選手", player(name = "背番号7番の選手", jersey = 7).displayName)
        assertEquals("#7 7番目", player(name = "7番目", jersey = 7).displayName)
        assertEquals("#7 7", player(name = "7", jersey = 7).displayName)
        assertEquals("#7 07番", player(name = "07番", jersey = 7).displayName)
    }

    @Test
    fun `別の背番号のラベル名は落とさない`() {
        // 「8番」という名前の 7 番。仮名化なら番号は必ず一致するので、これは実名扱い。
        assertEquals("#7 8番", player(name = "8番", jersey = 7).displayName)
    }

    @Test
    fun `背番号が無ければ名前だけ`() {
        val player = player(name = "山田太郎", jersey = null)
        assertFalse(player.isJerseyLabelName)
        assertEquals("山田太郎", player.displayName)
        assertEquals("山田太郎", player.nameBesideJersey)
    }

    @Test
    fun `背番号が無ければ 7番 という名前でも落とさない`() {
        assertEquals("7番", player(name = "7番", jersey = null).displayName)
    }

    @Test
    fun `並び順は背番号昇順で未設定が末尾`() {
        val a = player(name = "あ", jersey = 10)
        val b = player(name = "い", jersey = 2)
        val c = player(name = "う", jersey = null)
        assertEquals(listOf(b, a, c), listOf(a, c, b).sortedByJerseyNumber())
    }

    @Test
    fun `背番号が同値なら名前順`() {
        val a = player(name = "さとう", jersey = 7)
        val b = player(name = "あおき", jersey = 7)
        assertEquals(listOf(b, a), listOf(a, b).sortedByJerseyNumber())
    }

    private fun player(name: String, jersey: Long?) = Player(
        `id` = UUID.randomUUID(),
        `teamId` = TEAM_ID,
        `name` = name,
        `jerseyNumber` = jersey,
    )

    private companion object {
        val TEAM_ID: UUID = UUID.randomUUID()
    }
}
