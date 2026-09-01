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

## ビルド

前提:

- JDK 21
- Android SDK（`ANDROID_HOME` を設定するか `local.properties` に `sdk.dir` を書く）
- ABI は **arm64-v8a 単独**（[ADR 0006](https://github.com/kinjo-ryura/handball-toolkit/blob/main/docs/adr/0006-android-distribution.md)）。
  エミュレータを使うなら arm64-v8a の AVD が要る

**Rust / NDK は要らない。** コアは `.aar` の中に `.so` として入っている。

```sh
# コアの .aar を Releases から落とす
mkdir -p app/libs
gh release download v0.5.0 --repo kinjo-ryura/handball-toolkit --pattern '*.aar' --dir app/libs

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`app/libs/` と `local.properties` はコミットしない。

## インストール（利用者向け）

APK は [Releases](https://github.com/kinjo-ryura/handball-recorder-android/releases) で配布する。
Play ストア経由ではないため、インストール時に「提供元不明のアプリ」の許可と Play Protect の
警告の突破が要る。手順は [hand-plus.com](https://hand-plus.com/) に用意する。

## ライセンスと fork について

**MIT**。fork して自分の名前で公開すること、課金することを含めて自由に使ってよい。
これは事故ではなく意図で、Android 版の作り手を公募した経緯に対する回答としてこの形にしている。

コア（handball-toolkit）も MIT。
