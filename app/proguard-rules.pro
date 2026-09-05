# アプリ固有の R8 ルール（handball-project#285）。
#
# **いまは空。** 必要な keep ルールは依存側が consumer ルールとして持っている:
#   - JNA と UniFFI 生成コード … コアの .aar（handball-toolkit の android/toolkit/consumer-rules.pro）
#   - Room の生成 DAO / DB 実装 … room-runtime
#   - kotlinx-coroutines / Compose … 各ライブラリ
#
# ここに足すときは「何が実行時に壊れたか」を 1 行添える。R8 が壊すのは reflection 経由の
# 参照だけで、ビルド時には露見しない（release ビルドを端末で動かして初めて分かる）。
