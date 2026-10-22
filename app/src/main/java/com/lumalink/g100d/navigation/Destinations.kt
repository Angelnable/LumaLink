package com.lumalink.g100d.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.lumalink.g100d.R

sealed class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    data object Connect : Destination("connect", R.string.nav_connect, Icons.Outlined.Wifi)

    data object Transfers : Destination("transfers", R.string.nav_transfers, Icons.Outlined.SwapVert)

    data object Settings : Destination("settings", R.string.nav_settings, Icons.Outlined.Settings)

    data object About : Destination("about", R.string.nav_about, Icons.Outlined.Info)

    companion object {
        val bottomItems = listOf(Connect, Transfers)

        fun fromRoute(route: String?): Destination? = when (route) {
            Connect.route -> Connect
            Transfers.route -> Transfers
            Settings.route -> Settings
            About.route -> About
            else -> null
        }
    }
}
