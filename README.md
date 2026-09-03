# handball-recorder-android

ハンドボール試合記録アプリ **HandballRecorder** の Android 版。**MIT ライセンスの public repo** で、
Play ストアには出さず **APK を直接配布**する。

iOS 版（[App Store](https://apps.apple.com/jp/app/id6762165079)）と同じコアを共有し、
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
| 試合詳細（動画枠 + スコア + phase ごとのタイムライン） | `ui/detail/MatchDetailScreen.kt` |
| **サマリ**（チーム別 / 前後半別 / 選手別スタッツ + 得点差の推移） | `ui/detail/MatchSummaryScreen.kt` |
| ハイライト詳細（シーン一覧 + 「このハイライトの記録」） | `ui/detail/HighlightDetailScreen.kt` |
| **通し再生（「すべて再生」）** | `ui/playback/` |
| YouTube の再生・行タップからのシーク | `ui/video/` |

**スタッツは試合詳細の下ではなく別画面です**（試合詳細の右上「サマリ」から開く）。
iOS 版の導線（`MatchDetailViewV2` の右上 →「サマリ」→ `MatchSummaryViewV2`）に合わせたもので、
以前は詳細の一番下に inline で並べていました。タイムラインを読むこととスタッツを読むことは
別の用事で、混ぜると「タイムラインを最後まで送らないとスタッツに着かない」画面になります。
**ハイライト詳細には「サマリ」を出しません** — iOS もあちらの右上は「すべて再生」で、
サマリの概念が当てはまらないからです（スコアも前後半も選手の両チーム分も成立しない。下記）。

サマリの中身は iOS と同じ並びで **スコア / チーム別 / 前後半別 / 選手別 / 得点差の推移** の 5 つ。
**iOS にある 6 枚目の「共有カード」（スタッツ画像を生成して OS の共有シートへ渡す）は入れていません** —
画像生成が要るぶん見る専用 MVP の範囲を超えるためで、必要になった時点で足します。

**試合詳細とサマリは 1 つの `MatchDetailViewModel` を共有します。** サマリ側は
詳細の `NavBackStackEntry` を `viewModelStoreOwner` に渡して同じインスタンスを引き当てるので
（`MainActivity`）、取得も変換も 1 回で済み、**`MatchView`（= `resolver`）を閉じる責任は
最後まで詳細の `onCleared` 1 か所**にあります。サマリ画面は `MatchView` を開きも閉じもしません。

### 得点差の推移（`ui/detail/ScoreDiffChart.kt`）

iOS の `ScoreDiffChartV2` の移植で、**図の意味を 1 つも変えていません**:

- **横軸 = 得点差**。中央が 0 で、**左がホームリード / 右がアウェイリード**
  （コアの `diff` は `awayScore − homeScore`。`.aar` のシム `ProjectionsDerived.kt`）。
  目盛りは絶対値で出し、向きは「← ホーム名 / アウェイ名 →」の 1 行が示す
- **縦軸 = 試合の経過時間で、上が開始・下が終了**
- **階段は描画側で組み立てない。** コアの `points` は step-doubling 済みで 1 得点につき
  「得点前 / 得点後」の 2 点を持つので、素直に折れ線で繋ぐと階段になる
- **2 本目以降の regular phase の開始に破線**（前半の開始は図の上端そのものなので引かない）
- **ゼロ線は縦線として常に引く**（他のグリッドより濃く）
- 得点差を作れない試合（得点なし等。コアが null を返す）では**節ごと出さない**

**グラフライブラリは足していません**（依存を増やさない方針）。Compose の `Canvas` で描き、
**「点列 → 座標」の変換は純関数 `ScoreDiffChartGeometry` に切り出して単体テストで固定**して
あります（空 / 1 点 / 全部同値 / 最大差 0 / 試合時間 0 は、いずれも画面上では例外を出さずに
黙って線が消えるため）。iOS のズームスライダは入れていません — 図全体を 1 画面に収める
描き方にして、縦スクロールするリストの中に入れ子のスクロール領域を作らないためです。

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

残っているのは配布まわり（署名鍵・APK 配布導線）です。

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
| `ui/video/FullscreenState.kt` | 全画面の出入りの規則（純関数） |
| `ui/video/WebViewFullscreenHost.kt` | 全画面 view の載せ先・向きの固定・システムバーの制御 |

通し再生がプレーヤーに出す指示も **`YT.Player` の公式メソッド 1:1**（`seekTo` + `playVideo` /
`pauseVideo` / `getCurrentTime`）に限ってある。**通し再生のためにコントロールを隠さないこと** —
連続再生画面は `minimalControls` を使いたくなる場所だが、それは上の 1 点目に触れる。

### 全画面

`YT.Player` の全画面ボタンは document fullscreen を**要求するだけ**で、応えるのはホスト側の
責任である。`WebChromeClient.onShowCustomView` / `onHideCustomView` を実装していないと、
ボタンは出ているのに押しても何も起きない。**ボタンを消して解決してはいけない**（コントロールを
隠すのは上の禁止事項 1 に触れる）。

- 渡された view は **`window.decorView` へ直接 add する**。Compose のツリーには載せない —
  この view の所有者は Chromium で、寿命も付け外しの順序も `WebChromeClient` の契約が決めている
- 全画面中は **`SENSOR_LANDSCAPE`**（左右どちらの横向きにも追従する。`LANDSCAPE` だと端末を
  逆さに持った利用者に上下逆の映像を見せる）。抜けるときは**入る前の値へ戻す**
- システムバーは隠し、**縁からのスワイプで一時的に戻せる**ようにする
  （`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`）
- 抜ける経路は 3 つ（プレーヤーの縮小ボタン / 戻るキー / 画面を離れる）あるが、**すべて
  `WebViewFullscreenHost.hide()` の 1 か所を通す**。とくに画面を離れる経路が抜けていると、
  一覧へ戻った後も横向き固定・システムバー非表示のままになる
- **全画面 view の上には何も重ねない**（禁止事項 2）。戻るための UI も足していない

出入りの規則 — 二重に入らない・抜けるのが冪等・向きを入る前の値へ戻す — は
`ui/video/FullscreenState.kt` に純関数として切り出し、単体テストで固定してある。
冪等性は飾りではなく、戻るキーで抜けるときに `onCustomViewHidden()` が Chromium 側の
fullscreen を解き、その結果 `onHideCustomView` が返ってくるため、**同じ経路が必ず 2 回通る**。

**`MainActivity` の `configChanges` はこの機能の前提である。** 宣言が無いと向きが変わるたびに
Activity が作り直され、`WebView` を持つコントローラごと捨てられる（= 動画が頭に戻る）。
全画面は入るときに向きを横へ固定するので、宣言が無いと入った瞬間に再生成が走って全画面が
成立しない。ついでに、**それまであった「端末を回すと動画が頭に戻る」も消えている。**

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
| core-ktx | 1.13.1 | 全画面のシステムバー制御（`WindowInsetsControllerCompat`）。推移的にも入るが、使うものは自分で宣言する |
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

## アイコン

ランチャーアイコンは **iOS 版と同じ意匠**（五角形を作る 5 本のバー + 縁で切れる白い破片 2 つ）。
琥珀は [シュートフォーム分析](https://hand-plus.com/) と共通で **3 アプリを貫く family の印**、
背景の紫 `#A827BA`（iOS 版の `automatic-gradient` の基準色）が**ハンド記録固有**の色。

**MIT なので fork 先もこのアイコンをそのまま使ってよい。** コードと同じ扱いで、
別名で公開するときに差し替える義務はない（差し替えるのも自由）。

| ファイル | 役割 |
|---|---|
| `res/mipmap-anydpi-v26/ic_launcher{,_round}.xml` | API 26 以降の adaptive icon。中身は同じで、丸くするかは端末側のマスクが決める |
| `res/drawable/ic_launcher_background.xml` | 背景の紫（縦グラデーション） |
| `res/drawable/ic_launcher_foreground.xml` | 5 本のバー + 破片 2 つ。**意匠の唯一の出どころ** |
| `res/drawable/ic_launcher_monochrome.xml` | Android 13 以降のテーマアイコン。前景と同じ形を単色にしたもの |
| `res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png` | **API 24/25 用のフォールバック**。minSdk = 24 なのでこれが無いとその世代でアイコンが出ない |

前景のベクターは元データと同じ 1024×1024 の座標系で、**これを 108dp 全面に対応させ**、
`<group>` の `scale 0.86` + `translate(71.68, 71.68)` でキャンバス中心を軸に少しだけ縮めてある。
5 本のバーはどれも中心 (512,512) から半径 363.2 の円に載っているので、この倍率で
**外接円が Android のキーライン円（66dp）とほぼ一致する** — 円 / squircle / ほぼ四角の
どのマスクでも角が切れず、見える 72dp の 88% を占める。

iOS の質感（`soft-light` + 半透明 + 影）は vector drawable では表現できないので、
**iOS の実物から合成後の色を測って**縦グラデーション・半透明・縁の光・外側の影で近似している。
数値の根拠と、元データにあって意図的に落とした要素は
`ic_launcher_foreground.xml` の冒頭コメントに書いてある。

### 作り直し方

**PNG は手で編集しない。** 色や形を変えるときは上のベクター XML を直してから:

```sh
scripts/generate-launcher-icons.sh
```

スクリプトはベクター XML から色（単色 / グラデーション）とパスを**読み出して** 1024px の
マスタを描き、各 dpi へ縮小する（形も色もスクリプトは持たない）。`<group>` の変換・背景
グラデーションの向き・前景のパス本数を変えたのにスクリプト側の定数を直し忘れた場合は、
突き合わせに失敗して止まる。前景と `ic_launcher_monochrome.xml` は手で同期させること。

前提は **macOS（`sips`）+ Google Chrome**（`CHROME=` で場所を指定できる）。CI では回さない
（生成物はコミット済み）。

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

**CI の Action は完全な commit SHA でピン留めしてある**（handball-project#284）。可変タグは
差し替えが効くので、奪われるとランナーで任意コードが走る。**タグ参照に戻すと CI が起動しない**
（リポジトリ設定で SHA ピン留めを必須にしてある）。追随は `.github/dependabot.yml` が週次で
PR を出すので手で追わなくてよい。

手で引き直すときは**タグを peel した commit** を取ること:

```sh
git ls-remote https://github.com/gradle/actions 'refs/tags/v4^{}'
```

**`refs/tags/<tag>` だけを引くと annotated tag では tag オブジェクトの SHA が返り、commit では
ない。** `gradle/actions` がこれで、そのまま貼ると解決できない参照になる。

**Gradle 依存の Dependabot は Compose 系を除外している。** 最新は AGP 9.1.0 以上と
compileSdk 37 以上を要求して落ちるため（上の「バージョンの対応関係」）、1 依存だけを上げる PR は
必ず赤くなる。一式で上げると決めた時点で `dependabot.yml` の `ignore` を外すこと。

### リリースを出すとき

**APK の SHA-256 はリリースのたびに変わる。** 署名した APK の値を取り、Release 本文へ
その回のぶんとして書く（下の「本物かどうかを確かめる」が読者を Release 本文へ送っている）。

```sh
shasum -a 256 handball-recorder-android.apk
```

**署名証明書のフィンガープリントは鍵を替えるまで変わらない。** README の表と
https://hand-plus.com/handball-recorder/android/ を直すのは鍵を替えたときだけで、
それは `applicationId` を変える事態と同時に起きる。

**古いハッシュを貼りっぱなしにしないこと。** 合わない値が載っていると確かめた人に偽の
警告を出すことになり、次からは誰も確かめなくなる。載せないより悪い。

## インストール（利用者向け）

APK は [Releases](https://github.com/kinjo-ryura/handball-recorder-android/releases) で配布する。
Play ストア経由ではないため、インストール時に「提供元不明のアプリ」の許可と Play Protect の
警告の突破が要る。手順は
[hand-plus.com](https://hand-plus.com/handball-recorder/android/) に用意する。

### 本物かどうかを確かめる

ストア配布ではストアが「配布元がいつもと同じか」を保証するが、**直配布ではその保証が消える**。
上の手順は警告を通すことを案内するので、**何を見れば本物と判断できるか**をここに置く。

**恒久的な基準は署名証明書のフィンガープリント**である。APK のハッシュはリリースのたびに
変わるが、証明書は鍵を替えるまで変わらない。

| | 値 |
|---|---|
| 証明書 SHA-256 | `58eb75be3bcbbf7d2c6d58567e024db455738911743164a5da093777e1c5e20d` |
| 所有者 / 発行者 | `CN=kinjo-ryura, O=hand-plus, C=JP` |
| 鍵 | RSA 4096 / SHA384withRSA |
| 有効期限 | 2054-01-17 |

確かめ方。**JDK があれば追加のインストールは要らない**:

```sh
keytool -printcert -jarfile handball-recorder-android.apk
```

`SHA256:` の行が上の値と一致すれば同じ鍵で署名されている。**keytool は表示を
`58:EB:75:…` と大文字・コロン区切りにする**（値は同じ。目で比べるときは区切りを外す）。
**改ざんされた APK ではフィンガープリントを表示せずエラーで止まる**（JDK 21 で実測）。

Android SDK の build-tools があるなら、署名方式まで出る方でもよい:

```sh
apksigner verify --verbose --print-certs handball-recorder-android.apk
```

配布中の APK は **v2 + v3 方式**で署名している（**v1 は付いていない**）。
`keytool -printcert -jarfile` は v1 が無くても v2 / v3 の署名ブロックから読む。

ファイルそのものが配布物と同一かは、**各 Release の本文に載せた APK の SHA-256** で確かめる:

```sh
shasum -a 256 handball-recorder-android.apk               # macOS / Linux
certutil -hashfile handball-recorder-android.apk SHA256   # Windows
```

**`.sha256` は Release 資産に同梱しない。** APK と同じ場所に置いたチェックサムは、APK を
差し替えられる者に同時に差し替えられるので保証を足さない。効くのは**差し替えに痕跡が残る
場所に基準値があること**のほうで、この README は commit として履歴に残り fork にも複製され、
`main` は PR 必須で保護されている（上の「変更の出し方」）。Release 資産は黙って差し替えられる。
ファイル単体のハッシュは GitHub 自身が資産のダイジェスト（API の `digest`）として持っている。

## ライセンスと fork について

**MIT**。fork して自分の名前で公開すること、課金することを含めて自由に使ってよい。
これは事故ではなく意図で、Android 版の作り手を公募した経緯に対する回答としてこの形にしている。

コア（handball-toolkit）も MIT。

**ランチャーアイコンも同じ扱い**（iOS 版から移植した意匠をそのまま MIT で配っている）。
→ [アイコン](#アイコン)
