plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // 画面は Jetpack Compose。版はルートの build.gradle.kts が持つ（Kotlin と同一）。
    id("org.jetbrains.kotlin.plugin.compose")
    // Room の DAO 実装生成（kapt ではなく KSP）。
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.handplus.handballrecorder"
    compileSdk = 36
    // nix が提供する SDK には build-tools が 1 つしか入っていないため明示する。
    // 既定値（AGP のバンドル値）を要求されると read-only な nix store の SDK へ
    // ダウンロードしようとして失敗する。
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.handplus.handballrecorder"
        // ADR 0006 決定 2 の暫定値をそのまま確定値として採用する（NDK リンカの
        // API レベルと一致させる）。java.time が API 26 未満で使えない点は
        // coreLibraryDesugaring で解消する（下記）。
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        // ADR 0006 決定 5: arm64-v8a 単独。
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 署名設定は置かない。ここは APK を直配布するリポジトリなので、debug 鍵で
            // 署名された release が「配布できる成果物」に見えてしまうほうが有害
            // （配布用の署名鍵は Releases を出すときに別途用意する）。
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // 生成 Kotlin の API 面に java.time.Instant が出る（UtcDateTime = java.time.Instant）。
        // java.time は API 26 以降の標準ライブラリなので、minSdk 24 では desugaring が要る。
        // 外部シェル実装者向けの注意点として README にも記載している。
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        jniLibs {
            // .aar が同梱してくる .so を strip しない。panic = "abort"（ADR 0006 決定 4）
            // ではコアの panic が Kotlin 例外ではなくネイティブ abort になるため、
            // シンボルが残っているかどうかがそのまま診断可否になる。
            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    // ── コア ──
    // 配布された .aar を app/libs/ に置いて参照する。**外部利用者とまったく同じ経路**で、
    // Rust / Nix / NDK は要らない（handball-project#135）。入手方法は 2 通り:
    //   - 外部利用者と同じ: GitHub Release から .aar をダウンロードして libs/ へ置く
    //   - 手元でコアを直したとき: ./scripts/build_aar.sh の出力を libs/ へコピー
    // どちらも手順は examples/android/README.md「ビルドと実行」。
    implementation(files("libs/handball-toolkit-0.5.0.aar"))

    // .aar ファイル単体は依存情報を運ばない（運ぶのは Maven の POM で、ローカルファイル
    // 参照では POM が介在しない）。そのため利用側がこの 2 つを自分で宣言する必要がある。
    // 生成コードが Native.register で .so を dlopen するのに JNA、suspend 関数に coroutines。
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // ── 画面（Jetpack Compose / Material 3）──
    // BOM が版をまとめて決めるのは **androidx.compose.* だけ**。activity / lifecycle /
    // navigation は BOM の管轄外なので個別に版を書く（README のバージョン表と対応）。
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // @Preview のレンダラは debug ビルドにだけ入れる（release へ持ち込むと無駄に太る）。
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 全画面のシステムバー制御（WindowInsetsControllerCompat）で直接使う。activity /
    // lifecycle が推移的に持ち込んでもいるが、**使うものは自分で宣言する**（推移で
    // 手に入るかどうかは相手の都合で変わる）。版は現状の解決結果に合わせてある。
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // ── 永続化（シェルの責務。コアは DB を所有しない）──
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // ── 単体テスト（JVM 上で回る。Android 実機 / エミュレータは要らない）──
    // CI の `check` が `:app:testDebugUnitTest` を回すので、テストが 0 件のままにならないよう
    // ここで実行系を宣言する。**アプリ本体の依存は増やさない**（testImplementation は APK に入らない）。
    testImplementation("junit:junit:4.13.2")
}
