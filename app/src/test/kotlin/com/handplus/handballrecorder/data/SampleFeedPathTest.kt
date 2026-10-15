package com.handplus.handballrecorder.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections

/**
 * 配信のどのパスを取りに行くか。
 *
 * パスを書き間違えても画面は「見つかりません」になるだけで、ほかのテストは赤くならない。
 * 手元に立てた HTTP サーバーへ向け、届いたパスを見る。応答はすべて 404 にするので、
 * JSON の parse（コアの native ライブラリ）までは進まない。
 */
class SampleFeedPathTest {

    private lateinit var server: HttpServer
    private val requested: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            requested += exchange.requestURI.path
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun feed() = SampleFeed(baseUrl = "http://127.0.0.1:${server.address.port}/data")

    @Test
    fun `試合の一覧と本体は matches の下から取る`() = runBlocking {
        assertTrue(feed().fetchMatchIndex() is FeedResult.NotFound)
        assertTrue(feed().fetchMatch("2025-12-20-a") is FeedResult.NotFound)
        assertEquals(listOf("/data/matches/index.json", "/data/matches/2025-12-20-a.json"), requested.toList())
    }

    @Test
    fun `ハイライトの一覧と本体は highlights の下から取る`() = runBlocking {
        assertTrue(feed().fetchHighlightIndex() is FeedResult.NotFound)
        assertTrue(feed().fetchHighlight("2026-04-10-a") is FeedResult.NotFound)
        assertEquals(listOf("/data/highlights/index.json", "/data/highlights/2026-04-10-a.json"), requested.toList())
    }
}
