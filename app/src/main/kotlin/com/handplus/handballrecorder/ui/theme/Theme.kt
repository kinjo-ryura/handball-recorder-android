package com.handplus.handballrecorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * アプリ唯一のテーマ。**Material 3 の既定配色をそのまま使い、端末の設定で明暗だけを切り替える**。
 *
 * 独自のブランド色を持たないのは意図で、このアプリの画面は「試合の記録を読む」ことしかしない
 * ため、配色に固有の意味を負わせる箇所が無い（チーム帰属はタイムラインの左右の位置で表す）。
 * Dynamic Color（端末の壁紙由来の配色）も入れない — API 31 以降でしか効かず、
 * minSdk 24 のこのアプリでは「端末によって見た目が違う」だけになる。
 */
@Composable
fun HandballRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme = darkColorScheme()
