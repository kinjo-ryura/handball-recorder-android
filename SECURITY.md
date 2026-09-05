# セキュリティ上の問題の報告

このアプリ（Android 版「ハンド記録」。APK 直配布）に脆弱性を見つけたら、**公開 Issue ではなく
GitHub の Private vulnerability reporting で報告してください**。Issue は誰でも読めるので、
修正が出る前に手口が広まります。

- 報告先: リポジトリの **Security** タブ → **Report a vulnerability**
  （<https://github.com/kinjo-ryura/handball-recorder-android/security/advisories/new>）
- 書いてほしいこと: 影響を受ける版（Release のタグ、または main のコミット）、端末と
  Android の版、再現手順、想定される影響
- 対象: この APK と、このリポジトリのビルド・配布の仕組み。コア（`.aar`）の問題は
  [handball-toolkit](https://github.com/kinjo-ryura/handball-toolkit/security/policy) へ、
  iOS 版・配布サイトの問題は <https://hand-plus.com/handball-recorder/support/> へ

個人で運営しているため即応の約束はできませんが、**受領の返事は 7 日以内**を目安にし、
修正が出たら Release notes と advisory で公表します。報告者名は希望があれば掲載します。

## 対象となる版

最新の Release と `main` のみ。自動更新の仕組みは無いので、修正版は Release から
入れ直してもらう形になります。

## 配布物が本物かを確かめる

署名証明書のフィンガープリントと APK の SHA-256 は README「本物かどうかを確かめる」に
あります。**合わない APK は報告の対象ではなく、入れないでください**（このリポジトリの
成果物ではありません）。
