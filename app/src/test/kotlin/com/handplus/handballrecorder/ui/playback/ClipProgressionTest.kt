package com.handplus.handballrecorder.ui.playback

import io.github.kinjoryura.handballtoolkit.ControlFact
import io.github.kinjoryura.handballtoolkit.FactAnchor
import io.github.kinjoryura.handballtoolkit.MatchClock
import io.github.kinjoryura.handballtoolkit.MatchFact
import io.github.kinjoryura.handballtoolkit.MatchFactPayload
import io.github.kinjoryura.handballtoolkit.PhaseKind
import io.github.kinjoryura.handballtoolkit.PhaseStartPayload
import io.github.kinjoryura.handballtoolkit.PlayEventKind
import io.github.kinjoryura.handballtoolkit.PlayFact
import io.github.kinjoryura.handballtoolkit.ResolvedFact
import io.github.kinjoryura.handballtoolkit.VideoClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * 通し再生の進行判定。**iOS `PlayerShotsPlaybackControllerV2` / web デモ `playAllTick` と
 * 同じ答えになることを固定する。**
 *
 * ここが緩むと、3 者で「同じハイライトを再生したのに違うシーンが流れる」が起きる。
 * 特に注意して押さえてあるのは 3 つ:
 *
 * - **境界ちょうど**（進行は `>=` / 読み飛ばしは `>` の非対称）
 * - **重なり**（シークせず index だけ進める。親リポ #237）
 * - **手動で先へ飛ばされた場合**（終わっていない最初の後続まで一度に送る）
 */
class ClipProgressionTest {

    // ── クリップの作り方 ──

    @Test
    fun `クリップは記録時刻の 4 秒手前から 2 秒後まで`() {
        val clips = ClipProgression.clips(listOf(play(video = 100.0)))
        assertEquals(1, clips.size)
        assertEquals(96.0, clips[0].startSeconds, 0.0)
        assertEquals(102.0, clips[0].endSeconds, 0.0)
        // lead-in + tail = 6 秒。重なり判定の前提になる長さ。
        assertEquals(6.0, clips[0].endSeconds - clips[0].startSeconds, 1e-9)
    }

    @Test
    fun `開始 4 秒以内のシーンは 0 でクランプする`() {
        val clips = ClipProgression.clips(listOf(play(video = 1.5)))
        assertEquals(0.0, clips[0].startSeconds, 0.0)
        // 末尾はクランプしない（記録時刻 + 2 秒のまま）。
        assertEquals(3.5, clips[0].endSeconds, 0.0)
    }

    @Test
    fun `動画位置を持たない fact はクリップにしない`() {
        // シーン一覧には行として出るが、飛び先が無いので通し再生には載せない。
        assertEquals(emptyList<Clip>(), ClipProgression.clips(listOf(play(video = null))))
    }

    @Test
    fun `play 以外の fact はクリップにしない`() {
        assertEquals(emptyList<Clip>(), ClipProgression.clips(listOf(phaseStart(video = 10.0))))
    }

    @Test
    fun `得点以外の play fact も対象にする`() {
        // ハイライトは記録の過半が freeNote の回もある。得点に絞るとシーンの大半が消える。
        val facts = listOf(
            play(video = 10.0, kind = PlayEventKind.GOAL),
            play(video = 20.0, kind = PlayEventKind.SHOT_MISSED),
            play(video = 30.0, kind = PlayEventKind.FREE_NOTE),
            play(video = 40.0, kind = PlayEventKind.TWO_MINUTE_SUSPENSION),
        )
        assertEquals(4, ClipProgression.clips(facts).size)
    }

    @Test
    fun `クリップは開始位置の昇順に並ぶ`() {
        val facts = listOf(play(video = 300.0), play(video = 100.0), play(video = 200.0))
        assertEquals(
            listOf(96.0, 196.0, 296.0),
            ClipProgression.clips(facts).map { it.startSeconds },
        )
    }

    @Test
    fun `重なっていてもマージしない`() {
        // 「n / N」の N を減らさず、シーン一覧の行と 1:1 で対応させ続けるため。
        val facts = listOf(play(video = 100.0), play(video = 102.6))
        assertEquals(2, ClipProgression.clips(facts).size)
    }

    // ── ガード窓 ──

    @Test
    fun `シーク直後 1 秒より手前の値は無視する`() {
        // 96 へシークした直後、まだ 90 を返している = 未反映。ここで進めると誤遷移する。
        assertEquals(ClipStep.Hold, ClipProgression.step(twoClips(), index = 0, currentSeconds = 90.0, lastSeekSeconds = 96.0))
    }

    @Test
    fun `ガード窓ちょうどはポーリング値を使う`() {
        // 96 - 1 = 95。`<` なので 95.0 は窓の外（＝使う）。ただし末尾 102 に届いていないので Hold。
        assertEquals(ClipStep.Hold, ClipProgression.step(twoClips(), index = 0, currentSeconds = 95.0, lastSeekSeconds = 96.0))
        // 使われていることは、窓の中なら進まない位置で確かめる。
        val advanced = ClipProgression.step(twoClips(), index = 0, currentSeconds = 102.0, lastSeekSeconds = 96.0)
        assertEquals(ClipStep.Advance(index = 1, seekSeconds = 196.0), advanced)
    }

    // ── 現在クリップの末尾 ──

    @Test
    fun `末尾に届いていなければ何もしない`() {
        assertEquals(ClipStep.Hold, ClipProgression.step(twoClips(), index = 0, currentSeconds = 101.9, lastSeekSeconds = null))
    }

    @Test
    fun `末尾ちょうどで次のクリップへ進む`() {
        // 進行の判定は `>=`（iOS `guard videoTime >= clips[currentIndex].endVideoTime`）。
        assertEquals(
            ClipStep.Advance(index = 1, seekSeconds = 196.0),
            ClipProgression.step(twoClips(), index = 0, currentSeconds = 102.0, lastSeekSeconds = null),
        )
    }

    // ── 重なり（親リポ #237）──

    @Test
    fun `重なっているクリップにはシークしない`() {
        // 2.6 秒差の 2 件（配信中の `2026-05-09-ohrid-vs-vardar` に実在する間隔）。
        // clip0 = [96, 102] / clip1 = [98.6, 104.6]。102 は既に clip1 の中なので、
        // 素直に 98.6 へ飛ぶと巻き戻って同じ映像を二度流す。
        val clips = ClipProgression.clips(listOf(play(video = 100.0), play(video = 102.6)))
        assertEquals(
            ClipStep.Advance(index = 1, seekSeconds = null),
            ClipProgression.step(clips, index = 0, currentSeconds = 102.0, lastSeekSeconds = 96.0),
        )
    }

    @Test
    fun `次のクリップの頭ちょうども重なり扱いでシークしない`() {
        // 現在位置 == 次の start。飛んでも同じ位置なので、シークを投げる意味が無い（`>=`）。
        val clips = listOf(clip(0.0, 10.0), clip(10.0, 20.0))
        assertEquals(
            ClipStep.Advance(index = 1, seekSeconds = null),
            ClipProgression.step(clips, index = 0, currentSeconds = 10.0, lastSeekSeconds = null),
        )
    }

    @Test
    fun `重なっていなければ次のクリップの頭へシークする`() {
        val clips = listOf(clip(0.0, 10.0), clip(50.0, 60.0))
        assertEquals(
            ClipStep.Advance(index = 1, seekSeconds = 50.0),
            ClipProgression.step(clips, index = 0, currentSeconds = 10.0, lastSeekSeconds = null),
        )
    }

    // ── 手動で先へ飛ばされた場合 ──

    @Test
    fun `終わっていない最初の後続まで一度に送る`() {
        // 利用者がシークバーで 250 秒へ飛ばした。250ms ずつ辿らずに一度で追いつく。
        val clips = listOf(clip(0.0, 10.0), clip(50.0, 60.0), clip(100.0, 110.0), clip(240.0, 250.0), clip(300.0, 310.0))
        assertEquals(
            ClipStep.Advance(index = 4, seekSeconds = 300.0),
            ClipProgression.step(clips, index = 0, currentSeconds = 260.0, lastSeekSeconds = null),
        )
    }

    @Test
    fun `後続の末尾ちょうどは飛ばさずそのクリップを見せる`() {
        // 読み飛ばしの判定は `>`（進行の `>=` と**意図的に非対称**）。同時刻の記録で境界が
        // 一致する場合に、そのクリップも一度は現在クリップとして見せるため。
        val clips = listOf(clip(0.0, 10.0), clip(5.0, 15.0), clip(100.0, 110.0))
        assertEquals(
            // 15.0 は clips[1] の末尾ちょうど = まだ終わっていない側。start 5.0 は過ぎている
            // ので重なり扱い（シークしない）。
            ClipStep.Advance(index = 1, seekSeconds = null),
            ClipProgression.step(clips, index = 0, currentSeconds = 15.0, lastSeekSeconds = null),
        )
    }

    // ── 終わり ──

    @Test
    fun `最後のクリップの末尾を過ぎたら終わる`() {
        assertEquals(
            ClipStep.Finish,
            ClipProgression.step(twoClips(), index = 1, currentSeconds = 202.1, lastSeekSeconds = null),
        )
    }

    @Test
    fun `最後のクリップの末尾ちょうどでも終わる`() {
        // 現在クリップの末尾判定は `>=` なので、後続が無ければそのまま終了。
        assertEquals(
            ClipStep.Finish,
            ClipProgression.step(twoClips(), index = 1, currentSeconds = 202.0, lastSeekSeconds = null),
        )
    }

    @Test
    fun `全クリップを過ぎた位置へ飛ばされても終わる`() {
        assertEquals(
            ClipStep.Finish,
            ClipProgression.step(twoClips(), index = 0, currentSeconds = 9999.0, lastSeekSeconds = null),
        )
    }

    // ── 壊れた入力 ──

    @Test
    fun `クリップが無ければ何もしない`() {
        assertEquals(ClipStep.Hold, ClipProgression.step(emptyList(), index = 0, currentSeconds = 10.0, lastSeekSeconds = null))
    }

    @Test
    fun `index が範囲外なら何もしない`() {
        assertEquals(ClipStep.Hold, ClipProgression.step(twoClips(), index = 5, currentSeconds = 10.0, lastSeekSeconds = null))
        assertEquals(ClipStep.Hold, ClipProgression.step(twoClips(), index = -1, currentSeconds = 10.0, lastSeekSeconds = null))
    }

    @Test
    fun `ガード窓は index が範囲外でも落ちない`() {
        assertTrue(
            ClipProgression.step(emptyList(), index = 0, currentSeconds = 0.0, lastSeekSeconds = 100.0) is ClipStep.Hold,
        )
    }

    // ── ヘルパ ──

    /** clip0 = [96, 102]（記録 100 秒）/ clip1 = [196, 202]（記録 200 秒）。重なりなし。 */
    private fun twoClips(): List<Clip> = ClipProgression.clips(listOf(play(video = 100.0), play(video = 200.0)))

    private fun clip(start: Double, end: Double) = Clip(UUID.randomUUID(), start, end)

    private fun play(video: Double?, kind: PlayEventKind = PlayEventKind.GOAL) = ResolvedFact(
        `fact` = MatchFact(
            `id` = UUID.randomUUID(),
            `recordedAt` = Instant.EPOCH,
            `payload` = MatchFactPayload.Play(
                PlayFact(
                    `kind` = kind,
                    `anchor` = FactAnchor.VideoClock(VideoClock(video ?: 0.0)),
                ),
            ),
        ),
        // ハイライトは phase を持たないので試合時計は常に null。
        `resolvedMatchClock` = null,
        `resolvedVideoClock` = video?.let { VideoClock(it) },
    )

    private fun phaseStart(video: Double) = ResolvedFact(
        `fact` = MatchFact(
            `id` = UUID.randomUUID(),
            `recordedAt` = Instant.EPOCH,
            `payload` = MatchFactPayload.Control(
                ControlFact.PhaseStart(
                    PhaseStartPayload(
                        `kind` = PhaseKind.REGULAR,
                        `startAnchor` = FactAnchor.VideoClock(VideoClock(video)),
                        `endAnchor` = FactAnchor.VideoClock(VideoClock(video + 1800.0)),
                    ),
                ),
            ),
        ),
        `resolvedMatchClock` = MatchClock(0.0),
        `resolvedVideoClock` = VideoClock(video),
    )
}
