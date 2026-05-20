package com.navink.ui.theme

import androidx.compose.runtime.Composable
import com.mudita.mmd.ThemeMMD

@Composable
fun NavinkTheme(content: @Composable () -> Unit) {
    ThemeMMD(content = content)
}
