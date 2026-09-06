// ルートビルドスクリプト。プラグイン版は app モジュール側で適用する。
//
// **Kotlin のプラグインは宣言しない。** AGP 9.0 から Kotlin サポートが AGP に内蔵され、
// `org.jetbrains.kotlin.android` は不要になっただけでなく、**宣言していること自体が
// エラーになる**（"The 'org.jetbrains.kotlin.android' plugin is no longer required for
// Kotlin support since AGP 9.0"）。→ https://kotl.in/gradle/agp-built-in-kotlin
//
// **Kotlin の版を決めるのは下の compose プラグインである。** AGP 9.4.0 が既定で連れてくる
// KGP は 2.2.10 だが、compose-compiler-gradle-plugin:2.4.10 が kotlin-gradle-plugin:2.4.10 に
// 依存するので、buildscript のクラスパス解決（同一モジュールは最大版が勝つ）で 2.4.10 になる。
// **つまり compose プラグインの版 = このプロジェクトの Kotlin 版**で、両者は元から
// 完全一致が要求される関係にあるため二重管理にはならない。
// 上げるときは README「バージョンの対応関係」も同時に直すこと。
plugins {
    id("com.android.application") version "9.4.0" apply false
    // Compose コンパイラプラグイン。Kotlin 2.0 以降はコンパイラ本体と同梱なので、
    // **バージョンは Kotlin と完全一致でなければならない**（片方だけ上げると解決に失敗する）。
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // KSP は 2.3.x から Kotlin と別系列の版を持つ（以前の `<Kotlin>-<KSP>` 形式ではない）。
    // Kotlin と完全一致させる必要はなくなったが、対応する Kotlin の範囲は KSP の
    // リリースノートが決めるので、Kotlin を上げたらここも見ること。
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
