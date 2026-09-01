package com.handplus.handballrecorder.ui.labels

import com.handplus.handballrecorder.data.FeedResult

/**
 * 取得・変換の失敗をユーザー向けの日本語にする。
 *
 * **自前で書くのはここだけ。** コア由来の validation エラー文言（48 コード）は `.aar` の
 * `values-ja` が持っていて `DomainValidationIssue.userMessage(context)` で引ける。
 * このアプリが自分で書く必要があるのは **parse 失敗と通信エラー**だけ。
 *
 * **例外の `message` / `detail` を UI に出さないこと**（toolkit ADR 0002 決定 5）。
 * [FeedResult.Unreachable.diagnostic] / [FeedResult.Malformed.diagnostic] は診断用で、
 * ここでは読まない。
 *
 * 文言は web デモ（`demo.js` の `ERROR_MESSAGES` / `NETWORK_MESSAGE` / `NOT_FOUND_MESSAGES`）
 * に揃えてある。同じデータを同じ言い方で説明するため。
 */
object FeedErrorLabel {

    const val NETWORK: String =
        "データの取得に失敗しました。ネットワーク接続を確認して、もう一度お試しください。"

    const val MALFORMED: String =
        "試合データの形式を読み取れませんでした（データが壊れている可能性があります）。"

    const val UNSUPPORTED_SCHEMA: String =
        "このバージョンのアプリでは開けない形式のデータでした。アプリを更新してください。"

    /**
     * 失敗 → 文言。
     *
     * [subject] は「見つかりません」の主語を切り替えるためだけに要る（試合 / ハイライト）。
     */
    fun message(failure: FeedResult.Failure, subject: FeedSubject): String = when (failure) {
        // 実際に 404 を受け取った場合と、配信 URL になり得ない slug を弾いた場合。
        // どちらも「その名前のものは配信に無い」なので、ユーザーには同じことを言う。
        FeedResult.NotFound, FeedResult.InvalidSlug -> notFound(subject)
        is FeedResult.Unreachable -> NETWORK
        is FeedResult.HttpStatus ->
            "サーバーから応答がありませんでした（${failure.code}）。しばらくしてからもう一度お試しください。"
        is FeedResult.Malformed -> MALFORMED
        is FeedResult.UnsupportedSchema -> UNSUPPORTED_SCHEMA
    }

    fun notFound(subject: FeedSubject): String = when (subject) {
        FeedSubject.MATCH ->
            "この試合は見つかりませんでした。URL が正しくないか、配信が終了した可能性があります。"
        FeedSubject.HIGHLIGHT ->
            "このハイライトは見つかりませんでした。URL が正しくないか、配信が終了した可能性があります。"
    }
}

/** 「見つかりません」の主語。 */
enum class FeedSubject { MATCH, HIGHLIGHT }
