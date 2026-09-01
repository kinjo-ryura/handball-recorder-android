package com.handplus.handballrecorder.ui.labels

import java.time.Instant
import java.time.ZoneId

/**
 * 試合日の表示。`2026年4月10日` 形式。
 *
 * 配信 index の `date`（`UtcDateTime` = `java.time.Instant`）は UTC の瞬間なので、
 * **端末のタイムゾーンで日付に落とす**（iOS の `.dateTime.year().month().day()` と
 * web デモの `new Date(iso)` がどちらも同じ扱い）。
 *
 * `DateTimeFormatter` のロケール依存書式（`FormatStyle.LONG` 等）を使わない理由は 2 つ。
 * 端末の言語が日本語以外でも**アプリの他の文言はすべて日本語**なので混ざると読みにくいこと、
 * `java.time` の書式が API レベルで揺れないことを保証したいこと（minSdk 24 は
 * `coreLibraryDesugaring` 越しに `java.time` を使う）。
 */
object DateLabel {

    /** `2026年4月10日`。月・日はゼロ埋めしない（web デモの `formatDate` と同じ）。 */
    fun ymd(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = instant.atZone(zone).toLocalDate()
        return "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}
