package com.handplus.handballrecorder.ui.labels

import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

/**
 * 秒 → `mm:ss`。イベント行・シーン一覧・動画の位置表示など、時刻の書式をここに一本化する。
 *
 * iOS の `RecorderApplication/ClockFormatter.swift` と同じ規則（切り捨て・0 でクランプ・
 * 分は 2 桁ゼロ埋め）。web デモの `formatClock` は分をゼロ埋めしない（`5:03`）が、
 * **アプリ側の既存表記に揃える**（`05:03`）。
 */
object ClockFormat {

    /**
     * 秒を `mm:ss` へ。負値は `00:00`、端数は切り捨て。
     * 60 分を超える動画では分が 3 桁以上に伸びる（`101:07`）。
     */
    fun mmss(seconds: Double): String {
        val total = max(0.0, floor(seconds)).toLong()
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }
}
