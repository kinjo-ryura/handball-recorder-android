package com.handplus.handballrecorder.ui.detail

import com.handplus.handballrecorder.ui.labels.PlayerOrdering
import io.github.kinjoryura.handballtoolkit.Player
import io.github.kinjoryura.handballtoolkit.PlayerId
import io.github.kinjoryura.handballtoolkit.PlayerStatLine
import io.github.kinjoryura.handballtoolkit.TeamId
import io.github.kinjoryura.handballtoolkit.shotAttempts

/**
 * 選手別スタッツの並び順とチーム分け。**画面から切り出した純関数**。
 *
 * iOS `MatchSummaryViewV2.sortedLines` の移植で、**得点降順 → 試投降順 → 名前順**。
 * 得点上位を先頭に出すのが目的で、名前順は同点同試投のときの安定化。
 *
 * **`shotAttempts` は `.aar` のシムの拡張プロパティ**（`ProjectionsDerived.kt`）。
 * `goals + shotMisses` を画面側で書き直さないこと。
 */
object PlayerStatsOrdering {

    /**
     * 選手が見つからない行の名前。並べ替えのキーにだけ使い、表示は
     * `ControlLabel.UNKNOWN_PLAYER` が担う。
     */
    private const val MISSING_NAME = ""

    /**
     * あるチームに属する行だけを取り出す。
     *
     * **チーム判定は `Player.teamId`**（`PlayerStatLine` はチームを持たない）。
     * 選手が見つからない行は落とす — どちらのチームに出しても嘘になるため
     * （iOS も同じく `guard let player … else { return false }`）。
     */
    fun linesForTeam(
        lines: List<PlayerStatLine>,
        playersById: Map<PlayerId, Player>,
        teamId: TeamId,
    ): List<PlayerStatLine> = lines.filter { playersById[it.playerId]?.teamId == teamId }

    /** 得点降順 → 試投降順 → 名前順。入力は変更しない。 */
    fun sorted(
        lines: List<PlayerStatLine>,
        playersById: Map<PlayerId, Player>,
    ): List<PlayerStatLine> = lines.sortedWith(comparator(playersById))

    /**
     * 比較子。名前の比較は [PlayerOrdering.byName]（`Collator`）に委譲する —
     * Unicode のコード順だと日本語名が読み順と合わない。
     */
    fun comparator(playersById: Map<PlayerId, Player>): Comparator<PlayerStatLine> =
        compareByDescending<PlayerStatLine> { it.goals }
            .thenByDescending { it.shotAttempts }
            .thenBy(PlayerOrdering.byName) { playersById[it.playerId]?.name ?: MISSING_NAME }
}
