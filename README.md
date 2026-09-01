# handball-recorder-android

ハンドボール試合記録アプリ **HandballRecorder** の Android 版。**MIT ライセンスの public repo** で、
Play ストアには出さず **APK を直接配布**する。

iOS 版（[App Store](https://apps.apple.com/jp/app/id6762165079)・private repo）と同じコアを共有し、
ドメインロジックは [handball-toolkit](https://github.com/kinjo-ryura/handball-toolkit) の prebuilt `.aar`
が持つ。このリポジトリが持つのは **UI（シェル）だけ**。

## 何ができるか（MVP のスコープ）

**最初に出すのは「見る」だけ。** 記録機能は含まない。

| 含む | 含まない |
|---|---|
| 配信中のサンプル試合 / ハイライトの取得 | **記録機能すべて** |
| タイムライン / スタッツ表示 | 端末への保存（開くたびに取得する） |
| YouTube 再生とシーク | ローカル動画 / 2 台同期録画 / Play ストア公開 |

試合データは [handball-sample-matches](https://github.com/kinjo-ryura/handball-sample-matches) が
配信する v2 スキーマの JSON を取得する。同じ内容は web でも見られる
（[hand-plus.com/handball-recorder/demo/](https://hand-plus.com/handball-recorder/demo/)）。

記録機能を足すかどうかは、配った相手から「記録したい」が出た時点で判断する。

## コアとシェルの境界

**検証・参照整合の判定・保存順序の計画・projection（スコア / タイムライン / スタッツ）・
動画時刻⇔試合時刻の変換は、すべてコア（`.aar`）が持つ。** このリポジトリが持つのは
DB ハンドルとトランザクション境界、素朴な CRUD、ID / 時刻の発行、ユーザー向け文言、そして画面。

境界の正典は handball-toolkit 側の
[ADR 0005](https://github.com/kinjo-ryura/handball-toolkit/blob/main/docs/adr/0005-core-write-orchestration.md)（write orchestration）と
[ADR 0002](https://github.com/kinjo-ryura/handball-toolkit/blob/main/docs/adr/0002-error-model.md)（エラー体系）。
シェルが実装する 15 メソッドの契約は
[`examples/android`](https://github.com/kinjo-ryura/handball-toolkit/tree/main/examples/android) が
最小の参照実装として示している（このリポジトリはそれをシードとして始めた）。

**コアはユーザー向け文言を一切持たない**（エラーはコードとパラメータのみ）。日本語の文言は
すべてこのリポジトリ側にある。

## 現状

**見る専用 MVP は一通り揃っています。** シード（handball-toolkit の
[`examples/android`](https://github.com/kinjo-ryura/handball-toolkit/tree/main/examples/android)）が
持っていたデモ UI — ボタン 7 個でシェル契約の write 経路を踏む画面 — は、見る専用 MVP とは
用途が逆なので捨てました。

| 画面 / 機能 | 実装 |
|---|---|
| 配信データの取得（試合 / ハイライトの index と本体） | `data/SampleFeed.kt` |
| 一覧（タブ 2 つ + 動画有無フィルタ） | `ui/list/` |
| 試合詳細（phase ごとのタイムライン、チーム別 / 前後半別 / 選手別スタッツ） | `ui/detail/MatchDetailScreen.kt` |
| ハイライト詳細（シーン一覧 + 「このハイライトの記録」） | `ui/detail/HighlightDetailScreen.kt` |
| **通し再生（「すべて再生」）** | `ui/playback/` |
| YouTube の再生・行タップからのシーク | `ui/video/` |

**ハイライトは試合詳細と描画を分けてあります**（web デモと同じ理由）。ハイライトは記録の
過半が「メモ」（ナイスパス等）の回もあるので得点タイムラインでは大半のシーンが消え、
片チームの選手だけを取り上げるのでアウェイ列も試合時計も常に空になり、
`summary.homeScore` は試合スコアではなく「そのハイライトに写っている得点数」になります。
だから **1 列のシーン一覧**（全 play fact に種別チップ。通し再生の対象は後述の 3 種）と
**選手別の記録だけ**を出し、
見出しも「スタッツ」ではなく「このハイライトの記録」にしています。時刻は動画時間です
（`resolvedMatchClock` は全 fact で null）。

**通し再生（`ui/playback/`）は iOS の `PlayerShotsPlaybackControllerV2` と web デモの
`playAllTick` の移植で、進行の規則を 1 つも変えていません。** 各シーンを
`videoClock − 4 秒 〜 videoClock + 2 秒` のクリップにし、再生位置を 250ms ごとに見て
末尾を過ぎたら次へ進みます。**重なっているクリップにはシークしません**（lead-in +
tail = 6 秒なので 6 秒未満の間隔は重なり、素直に飛ぶと巻き戻って同じ映像を二度流す。
親リポ #237）。進行の判定は副作用を持たない純関数（`ClipProgression.step`）に切り出して
あり、境界ちょうど・重なり・手動で先へ飛ばされた場合を単体テストで固定しています。
**3 者は「片方を変えたらもう片方も揃える」取り決めです。**

**通し再生に載るのは得点 / シュートミス / メモの 3 種だけ**で、カード類（イエロー /
2 分間退場 / レッド）は載せません。正典は iOS の `PlayerShotsPlaybackControllerV2` の
「試合全体のハイライト（goal / shotMissed / freeNote）を時系列順で連続再生する init」です
（**web デモの `buildClips` は全 play fact を対象にしていて、ここだけ 3 者が揃っていません**。
揃える先を iOS にするのは利用者判断）。

その結果、**シーン一覧の行数と「すべて再生（N シーン）」の N は一致しません**。対象外の行は:

- 一覧には**出す**（記録された事実を落とすと「取りこぼした」ように見えるため）
- **▶ を出さない**（押しても通し再生には入らないことを見た目で示す）
- N に**数えない**（N はクリップ数）
- タップすれば**単発シークはする**（3 秒手前。通し再生中でも同じ）
- 通し再生中に強調されることは無い

食い違うのが前提なので、**強調行とクリップ `index` の対応は必ず `factId` で取ります**
（`ui/detail` の `List<HighlightScene>.indexOfFact`）。行番号で引き当てるとカードの数だけずれます。

**行タップの 3 秒と通し再生の lead-in 4 秒は別の定数**です（`ui/playback/PlaybackOffsets.kt`）。
「そのシーンへ飛ぶ」と「名場面を繋いで見る」で必要な助走が違うので、iOS も web デモも
別々に持っています。混ぜないこと。なお**行タップの扱いは web デモと意図的に違い**、
止まっているときは単発シーク（3 秒手前）、通し再生中はそのシーンからの再開になります。

残っているのは配布まわり（署名鍵・APK 配布導線）と、下の「既知の制限」です。

既知の制限: プレーヤーの**全画面ボタンは押しても何も起きません**（`WebChromeClient` の
`onShowCustomView` を実装していないため）。ボタン自体は消しません — コントロールを隠すのは
RMF に触れるからで、対応は別途入れます。

**Room の DB 層（`db/`）と 15 メソッドの write repository（`RoomWriteRepositories.kt`）は
残してあります。** 見る専用 MVP は端末に保存しないのでどちらも通りませんが、記録機能を
足すときには必ず要るもので、しかも**公開できるシェル契約の参照実装はこれだけ**だからです
（handball-toolkit 側の `examples/android` と対になる）。

## YouTube 連携（RMF）

動画は **`WebView` にローカル HTML（`app/src/main/assets/youtube_player.html`）を読み込み、
公式の [IFrame Player API](https://developers.google.com/youtube/iframe_api_reference?hl=ja)
（`YT.Player`）で制御**する。iOS 版とまったく同じ方式で、これは
[RMF（Required Minimum Functionality）](https://developers.google.com/youtube/terms/required-minimum-functionality?hl=ja)
に適合させるための選択でもある（`WebView` は許可されたクライアントとして明記されており、
ローカル HTML に baseUrl を与えて読む形は RMF が案内する実装そのもの）。
YouTube の Android ライブラリは使わない。

### やってはいけないこと（変更時はここを読み直す）

1. **プレーヤーのコントロールを隠さない。** `initPlayer` の `minimalControls`
   （`controls: 0` / `disablekb` / `fs: 0` / `iv_load_policy` / `modestbranding` を一括で立てる）は
   iOS から形だけ持ち込んであるが、**ネイティブ側は常に `false` を渡す**。単独で有効にしない。
2. **YouTube の UI・attribution を覆うオーバーレイを乗せない。** 再生できないときの注記も、
   枠の上ではなく**下**に出している。
3. **広告やリンクをブロックしない。**
4. **音声だけを抜き出して再生しない。**
5. **`YT.Player` の公式メソッドとイベントだけを使う。** `document.querySelector('video')` の
   ような内部 DOM アクセスや、低レベルの `postMessage` を直接叩くのは規約違反。

出典: [RMF](https://developers.google.com/youtube/terms/required-minimum-functionality?hl=ja) ・
[IFrame Player API](https://developers.google.com/youtube/iframe_api_reference?hl=ja) ・
[Player Parameters](https://developers.google.com/youtube/player_parameters?hl=ja) ・
[Developer Policies](https://developers.google.com/youtube/terms/developer-policies?hl=ja)

### baseUrl と origin は app-origin にする（実害の話）

`loadDataWithBaseURL` の baseUrl と `playerVars.origin` には、applicationId から組んだ
**`https://com.handplus.handballrecorder`** を渡す。

**ここを `http://127.0.0.1` 系や `file://` にすると、公開 URL でなら再生できる動画が
`onError` 150 で弾かれる。** 2026-08-22 に web デモ側で実測した挙動で、それまで 1 か月ものあいだ
「投稿者が埋め込みを無効化しているらしい」と誤診していた。**規約適合と実害回避が同じ答えになる**
数少ない場所なので、動作確認のためであってもここを書き換えないこと。

### 実装の地図

| ファイル | 役割 |
|---|---|
| `app/src/main/assets/youtube_player.html` | ホスト HTML。iOS の同名ファイルからの移植で、**変えたのは JS → ネイティブの送信口だけ**（`window.webkit.messageHandlers.ytEvent` → `window.AndroidYouTube`） |
| `ui/video/YouTubePlayerController.kt` | `WebView` の所有・JS の評価・`@JavascriptInterface` の受け口 |
| `ui/video/YouTubeBridge.kt` | 届いた JSON を 4 種のイベントへ decode（純関数） |
| `ui/video/PlayerReadiness.kt` | 準備状態の遷移（純関数） |
| `ui/video/JsLiterals.kt` | JS へ渡す値のリテラル化（純関数） |
| `ui/video/YouTubePlayerFrame.kt` | Compose への載せ方（`AndroidView` と破棄） |
| `ui/playback/ClipPlaybackController.kt` | 通し再生。プレーヤーへは `ClipPlaybackTarget`（`seek` / `pause` / `currentTimeSeconds` の 3 つだけ）を通して触る |

通し再生がプレーヤーに出す指示も **`YT.Player` の公式メソッド 1:1**（`seekTo` + `playVideo` /
`pauseVideo` / `getCurrentTime`）に限ってある。**通し再生のためにコントロールを隠さないこと** —
連続再生画面は `minimalControls` を使いたくなる場所だが、それは上の 1 点目に触れる。

`org.json` を使わず JSON の decode を自前で持っているのは、**JVM 単体テストでは `org.json` が
スタブ**（呼ぶと既定値を返すだけ）になり CI で検証できないため。依存を増やさずに
`:app:testDebugUnitTest` で固定できることを優先した。

**位置は「着地」してからでないと読まない。** cued / unstarted の `getCurrentTime()` は 0 を返すが、
それは「動画の 0 秒地点に居る」ではない。準備状態を
`unloaded → loading → ready → positioned` の**前進のみ**で持ち、`positioned` 未満では
`currentTimeSeconds()` が null を返す。

## ビルド

前提:

- **JDK 21**
- **Android SDK** — この repo は SDK を持たない（[ADR 0006](https://github.com/kinjo-ryura/handball-toolkit/blob/main/docs/adr/0006-android-distribution.md) 決定 1）。
  `ANDROID_HOME` を設定するか `local.properties` に書く:

  ```sh
  echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # コミットしない
  ```

- ABI は **arm64-v8a 単独**（ADR 0006 決定 5）。エミュレータを使うなら同 ABI の AVD が要る

**Rust / NDK は要らない。** コアは `.aar` の中に `.so` として入っている。

```sh
# コアの .aar を Releases から落とす（app/libs/ はコミットしない）
mkdir -p app/libs
gh release download v0.5.0 --repo kinjo-ryura/handball-toolkit --pattern '*.aar' --dir app/libs

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### nix を使う場合（任意）

`flake.nix` が JDK と Gradle を供給する。**nix は必須ではない** — 上の `./gradlew` だけで完結する。

```sh
nix develop     # direnv なら direnv allow
./gradlew :app:assembleDebug
```

**Gradle のバージョンを決めるのは wrapper（`gradle/wrapper/gradle-wrapper.properties`）で、
flake ではない。** nix 環境でも `./gradlew` を使えば fork 先とまったく同じ Gradle で回る。
flake の `gradle` を直接叩くこともできるが、その場合バージョンが二重管理になるので、
devShell に入るたびに wrapper のピン留めと一致するかを検査して食い違いを警告する。

### バージョンの対応関係

| | バージョン | 備考 |
|---|---|---|
| Gradle | 8.14.4 | wrapper がピン留め（配布物の SHA-256 も固定） |
| AGP | 8.11.1 | Gradle 8.13+ を要求 |
| Kotlin | 2.1.21 | KSP・Compose コンパイラと組で上げること |
| KSP | 2.1.21-2.0.1 | Kotlin と完全一致が必要 |
| Compose コンパイラプラグイン | 2.1.21 | `org.jetbrains.kotlin.plugin.compose`。**Kotlin と完全一致が必要**（Kotlin 2.0 以降はコンパイラ同梱） |
| Compose BOM | 2025.06.01 | compose-ui 1.8.3 / material3 1.3.2 を決める |
| activity-compose | 1.10.1 | **BOM の管轄外**（BOM が版を決めるのは `androidx.compose.*` だけ） |
| lifecycle-viewmodel-compose / lifecycle-runtime-compose | 2.9.1 | 同上 |
| navigation-compose | 2.9.0 | 同上 |
| Room | 2.7.2 | |
| handball-toolkit | 0.5.0 | `app/libs/handball-toolkit-0.5.0.aar` |
| JNA | 5.17.0（`@aar`） | **`.aar` は依存情報を運ばない**ので利用側で宣言する |
| kotlinx-coroutines | 1.10.2 | 同上 |
| compileSdk / targetSdk | 36 | `buildToolsVersion = "37.0.0"` を明示 |
| minSdk | 24 | `java.time` を使うため `coreLibraryDesugaring` が要る |

**Compose 系はあえて最新を追っていない。** ここに書いた組は Kotlin 2.1.21 / AGP 8.11.1 と
同世代のもの。最新（compose-bom 2026.08.00 / lifecycle 2.11.0 / navigation-compose 2.10.0）は
`checkDebugAarMetadata` が **AGP 9.1.0 以上と compileSdk 37 以上を要求して落ちる**
（2026-09-01 に実測）。**上げるなら AGP・compileSdk・CI の `setup-android` が入れる
platform / build-tools まで一式で動かすこと。**

lint は `GradleDependency` で「もっと新しい版がある」と言い続けるが、これは警告であって
ビルドは通る（AGP・JNA・coroutines についても以前から同じ警告が出ている）。

## 変更の出し方

**`main` へ直接 push できない。** public repo なので ruleset `protect-main` を掛けてある
（**PR 必須 + CI green 必須。オーナーも bypass できない**）。

```sh
git switch -c <branch>
# ...
gh pr create --fill
gh pr merge --squash --delete-branch   # CI が緑になってから
```

CI（[`.github/workflows/ci.yml`](.github/workflows/ci.yml) の `check`）が回すのは
**assembleDebug / 単体テスト / lint**、そして **`gradle-wrapper.jar` の検証**（コミット済みの
wrapper が正規の配布物かを照合する。fork される前提の public repo なので、差し替えられた
wrapper を黙って実行しないことに意味がある）。コアの `.aar` は handball-toolkit の Release から
取るので、CI でも手元と同じく **Rust / NDK は要らない**。

`.aar` の版は `app/build.gradle.kts` の 1 か所だけが持ち、CI はそこから読む。上げるときは
そのファイルと上のバージョン表を直せばよい（workflow は触らない）。

**ジョブ名 `check` は ruleset の required status check と一致している。** 変えるなら
ruleset 側も同時に変えること（片方だけ変えると PR が永久にマージできなくなる）。

## インストール（利用者向け）

APK は [Releases](https://github.com/kinjo-ryura/handball-recorder-android/releases) で配布する。
Play ストア経由ではないため、インストール時に「提供元不明のアプリ」の許可と Play Protect の
警告の突破が要る。手順は [hand-plus.com](https://hand-plus.com/) に用意する。

## ライセンスと fork について

**MIT**。fork して自分の名前で公開すること、課金することを含めて自由に使ってよい。
これは事故ではなく意図で、Android 版の作り手を公募した経緯に対する回答としてこの形にしている。

コア（handball-toolkit）も MIT。
