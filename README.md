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
| 端末への取り込みと保存（Room） | ローカル動画 |
| タイムライン / スタッツ表示 | 2 台同期録画 |
| YouTube 再生とシーク | Play ストア公開 |

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

**シード投入直後**で、画面はまだ handball-toolkit の
[`examples/android`](https://github.com/kinjo-ryura/handball-toolkit/tree/main/examples/android)
（シェル契約の参照実装）のままです。Room の DB 層と 15 メソッドの write repository は動きますが、
上のスコープにある一覧・詳細画面と YouTube 再生はこれから実装します。

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
| Kotlin | 2.1.21 | KSP と組で上げること |
| KSP | 2.1.21-2.0.1 | Kotlin と完全一致が必要 |
| Room | 2.7.2 | |
| handball-toolkit | 0.5.0 | `app/libs/handball-toolkit-0.5.0.aar` |
| JNA | 5.17.0（`@aar`） | **`.aar` は依存情報を運ばない**ので利用側で宣言する |
| kotlinx-coroutines | 1.10.2 | 同上 |
| compileSdk / targetSdk | 36 | `buildToolsVersion = "37.0.0"` を明示 |
| minSdk | 24 | `java.time` を使うため `coreLibraryDesugaring` が要る |

## インストール（利用者向け）

APK は [Releases](https://github.com/kinjo-ryura/handball-recorder-android/releases) で配布する。
Play ストア経由ではないため、インストール時に「提供元不明のアプリ」の許可と Play Protect の
警告の突破が要る。手順は [hand-plus.com](https://hand-plus.com/) に用意する。

## ライセンスと fork について

**MIT**。fork して自分の名前で公開すること、課金することを含めて自由に使ってよい。
これは事故ではなく意図で、Android 版の作り手を公募した経緯に対する回答としてこの形にしている。

コア（handball-toolkit）も MIT。
