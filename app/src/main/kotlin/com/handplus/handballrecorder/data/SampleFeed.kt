package com.handplus.handballrecorder.data

import io.github.kinjoryura.handballtoolkit.SampleDtoException
import io.github.kinjoryura.handballtoolkit.SampleHighlightSummaryV2
import io.github.kinjoryura.handballtoolkit.SampleMatchDtoV2
import io.github.kinjoryura.handballtoolkit.SampleMatchSummaryV2
import io.github.kinjoryura.handballtoolkit.parseSampleHighlightIndex
import io.github.kinjoryura.handballtoolkit.parseSampleIndex
import io.github.kinjoryura.handballtoolkit.parseSampleMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 配信（`handball-sample-matches` の `/v2/`）から試合 / ハイライトを取得する。
 *
 * **JSON → DTO の parse はコアの責務**（`parseSampleIndex` / `parseSampleMatch` /
 * `parseSampleHighlightIndex`）。この層が持つのは HTTP と、失敗の分類だけ。
 *
 * HTTP は `java.net.HttpURLConnection` で引く。**OkHttp 等を足さない** — 引くのは JSON 4 種
 * だけで、依存が少ないほうが fork 先で通る（README「ライセンスと fork について」）。
 *
 * **自動リトライはしない。** 再試行の口は呼び出し側（pull-to-refresh / 「再試行」ボタン）が持つ。
 */
class SampleFeed(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {

    /** `/v2/index.json` の `matches[]`（配信側で日付降順に確定済みなので並べ替えない）。 */
    suspend fun fetchMatchIndex(): FeedResult<List<SampleMatchSummaryV2>> {
        val json = when (val fetched = fetchText("$baseUrl/index.json")) {
            is FeedResult.Success -> fetched.value
            is FeedResult.Failure -> return fetched
        }
        val dto = try {
            parseSampleIndex(json)
        } catch (e: SampleDtoException) {
            return FeedResult.Malformed(e.toString())
        }
        if (dto.schemaVersion != SCHEMA_VERSION) return FeedResult.UnsupportedSchema(dto.schemaVersion)
        return FeedResult.Success(dto.matches)
    }

    /** `/v2/matches/{slug}.json`。 */
    suspend fun fetchMatch(slug: String): FeedResult<SampleMatchDtoV2> =
        fetchMatchBody(slug, "$baseUrl/matches")

    /** `/v2/highlights/index.json` の `highlights[]`。 */
    suspend fun fetchHighlightIndex(): FeedResult<List<SampleHighlightSummaryV2>> {
        val json = when (val fetched = fetchText("$baseUrl/highlights/index.json")) {
            is FeedResult.Success -> fetched.value
            is FeedResult.Failure -> return fetched
        }
        val dto = try {
            parseSampleHighlightIndex(json)
        } catch (e: SampleDtoException) {
            return FeedResult.Malformed(e.toString())
        }
        if (dto.schemaVersion != SCHEMA_VERSION) return FeedResult.UnsupportedSchema(dto.schemaVersion)
        return FeedResult.Success(dto.highlights)
    }

    /** `/v2/highlights/{slug}.json`（本体は試合と同じ `SampleMatchDtoV2`）。 */
    suspend fun fetchHighlight(slug: String): FeedResult<SampleMatchDtoV2> =
        fetchMatchBody(slug, "$baseUrl/highlights")

    /**
     * 本体 JSON 1 件の取得。
     *
     * **本体の `schemaVersion` は検査しない** — 検査するのは index 側 1 回だけにしてある
     * （iOS `SampleMatchStoreV2` と同じ判断）。本体側の不一致はコアの decode が
     * `SampleDtoException.Decode` で落とすので、素通りはしない。
     */
    private suspend fun fetchMatchBody(slug: String, directoryUrl: String): FeedResult<SampleMatchDtoV2> {
        // slug は URL のパスへそのまま埋まるので、取りに行く前に形を確かめる。
        if (!SampleSlug.isValid(slug)) return FeedResult.InvalidSlug
        val json = when (val fetched = fetchText("$directoryUrl/$slug.json")) {
            is FeedResult.Success -> fetched.value
            is FeedResult.Failure -> return fetched
        }
        return try {
            FeedResult.Success(parseSampleMatch(json))
        } catch (e: SampleDtoException) {
            FeedResult.Malformed(e.toString())
        }
    }

    /**
     * 1 リクエスト分の GET。
     *
     * **404 だけを [FeedResult.NotFound] に倒す。** 通信断・5xx をここへ混ぜると、
     * 正しい URL に「見つかりません」が出る（親リポ #211 で実際に発生した）。
     */
    private suspend fun fetchText(url: String): FeedResult<String> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // 接続・読み取りとも明示する。既定は「無制限」で、電波の悪い場所で
                // 画面が永久にローディングのまま止まる。
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                // web デモ（`demo.js` の `fetch(url, { cache: 'no-cache' })`）と同じ。
                // 条件付きリクエストは投げる（= 更新が無ければ 304 で軽い）が、
                // 検証せずにキャッシュを返すことはしない。
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/json")
            }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    FeedResult.Success(
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() },
                    )

                HttpURLConnection.HTTP_NOT_FOUND -> {
                    connection.errorStream?.close()
                    FeedResult.NotFound
                }

                else -> {
                    connection.errorStream?.close()
                    FeedResult.HttpStatus(code)
                }
            }
        } catch (e: IOException) {
            // 接続そのものが成立しなかった（機内モード / DNS / タイムアウト / 不正な URL）。
            FeedResult.Unreachable(e.toString())
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        /** 配信のベース URL（`handball-sample-matches` の `main` ブランチ）。 */
        const val DEFAULT_BASE_URL: String =
            "https://raw.githubusercontent.com/kinjo-ryura/handball-sample-matches/main/v2"

        /** このアプリが読める配信スキーマ。index の値がこれと違えば全体を失敗にする。 */
        const val SCHEMA_VERSION: Long = 2L

        /** 接続・読み取りの既定タイムアウト（ミリ秒）。 */
        const val DEFAULT_TIMEOUT_MILLIS: Int = 15_000
    }
}

/**
 * 配信 URL のパスに埋めてよい slug かどうか。
 *
 * ディープリンク（`handballrecorder://…` / Universal Links）から来た文字列がそのまま
 * パスへ入るので、取得前にここで形を確かめる。`..` や `/` を弾くのが主目的。
 */
object SampleSlug {

    /** 先頭は英数字、以降は英数字とハイフン。全体で最大 64 文字。 */
    private val PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9-]{0,63}$")

    fun isValid(slug: String): Boolean = PATTERN.matches(slug)
}

/**
 * 取得 1 回分の結果。
 *
 * **失敗を 1 種類にまとめない。** 「見つかりません」を出してよいのは 404 を実際に受け取った
 * ときだけで、通信断・5xx・parse 失敗と同じ箱に入れると区別できなくなる。
 * [Failure] のサブ型が増えても画面側の `when` が非網羅になって気付ける。
 */
sealed interface FeedResult<out T> {

    data class Success<out T>(val value: T) : FeedResult<T>

    /** 何らかの理由で値を得られなかった。文言は `ui.labels.FeedErrorLabel` が決める。 */
    sealed interface Failure : FeedResult<Nothing>

    /** HTTP 404。その slug は配信に無い（タイポ / 配信終了）。**ここだけが「見つかりません」**。 */
    data object NotFound : Failure

    /**
     * slug が [SampleSlug] の形を満たさず、取得しにいかなかった。
     *
     * 配信の URL になり得ない文字列なので、ユーザーには 404 と同じ「見つかりません」を見せる
     * （[FeedErrorLabel][com.handplus.handballrecorder.ui.labels.FeedErrorLabel] が写像を持つ）。
     * 型として分けてあるのは、ログで「弾いた」と「配信に無かった」を区別するため。
     */
    data object InvalidSlug : Failure

    /**
     * 接続そのものが成立しなかった（機内モード / DNS / タイムアウト）。
     *
     * [diagnostic] は**開発者向け**。UI に出さないこと（toolkit ADR 0002 決定 5）。
     */
    data class Unreachable(val diagnostic: String) : Failure

    /** HTTP は返ってきたが 404 以外の異常ステータス（5xx / 403 など）。 */
    data class HttpStatus(val code: Int) : Failure

    /**
     * JSON が v2 スキーマとして読めない、または domain へ変換できない。
     *
     * [diagnostic] は**開発者向け**。UI に出さないこと。
     */
    data class Malformed(val diagnostic: String) : Failure

    /** index の `schemaVersion` がこのアプリの対応版と違う。 */
    data class UnsupportedSchema(val found: Long) : Failure
}
