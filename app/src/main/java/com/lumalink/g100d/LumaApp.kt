package com.lumalink.g100d

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lumalink.g100d.core.settings.AppSettings
import com.lumalink.g100d.core.settings.ThemeMode
import com.lumalink.g100d.navigation.Destination
import com.lumalink.g100d.screens.AboutScreen
import com.lumalink.g100d.screens.SettingsScreen
import com.lumalink.g100d.transport.session.CameraConnectionState
import com.lumalink.g100d.transport.ui.ConnectScreen
import com.lumalink.g100d.transport.ui.ConnectViewModel
import com.lumalink.g100d.transport.ui.TransfersScreen
import com.lumalink.g100d.transport.ui.TransfersViewModel
import com.lumalink.g100d.ui.component.LumaGlassBackground
import com.lumalink.g100d.ui.component.StatusPill
import com.lumalink.g100d.ui.component.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumaApp(
    container: AppContainer,
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val connectionState by container.connectionManager.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = Destination.fromRoute(currentRoute) ?: Destination.Connect
    val showBottomBar = currentRoute == null || Destination.bottomItems.any { it.route == currentRoute }

    fun navigateToRoot(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LumaGlassBackground {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentRoute == null || currentDestination == Destination.Connect) {
                            "LumaLink"
                        } else {
                            stringResource(currentDestination.labelRes)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    if (!showBottomBar) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (showBottomBar) {
                        StatusPill(
                            text = connectionLabel(connectionState),
                            tone = connectionTone(connectionState),
                        )
                        IconButton(onClick = { navController.navigate(Destination.Settings.route) }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                ),
            )
        },
        bottomBar = {
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    NavigationBar(
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier
                            .shadow(
                                elevation = 14.dp,
                                shape = RoundedCornerShape(28.dp),
                                ambientColor = Color.Black.copy(alpha = 0.22f),
                                spotColor = Color.Black.copy(alpha = 0.30f),
                            )
                            .clip(RoundedCornerShape(28.dp))
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(28.dp),
                            ),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                        tonalElevation = 0.dp,
                    ) {
                        Destination.bottomItems.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { navigateToRoot(destination.route) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .then(if (showBottomBar) Modifier else Modifier.navigationBarsPadding())

        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) {
        NavHost(
            navController = navController,
            startDestination = Destination.Connect.route,
            modifier = contentModifier,
        ) {
            composable(Destination.Connect.route) {
                val connectViewModel: ConnectViewModel = viewModel(
                    factory = ConnectViewModel.factory(
                        connectionManager = container.connectionManager,
                        wifiScanner = container.wifiScanner,
                        transferRepository = container.transferRepository,
                        context = container.applicationContext,
                    ),
                )
                ConnectScreen(
                    viewModel = connectViewModel,
                    onOpenTransfers = { navigateToRoot(Destination.Transfers.route) },
                )
            }
            composable(Destination.Transfers.route) {
                val transfersViewModel: TransfersViewModel = viewModel(
                    factory = TransfersViewModel.factory(
                        repository = container.transferRepository,
                        context = container.applicationContext,
                    ),
                )
                TransfersScreen(
                    viewModel = transfersViewModel,
                    onOpenConnect = { navigateToRoot(Destination.Connect.route) },
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    settings = settings,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onOpenAbout = { navController.navigate(Destination.About.route) },
                )
            }
            composable(Destination.About.route) {
                AboutScreen()
            }
        }
        }
    }
    }
}

private fun connectionLabel(state: CameraConnectionState): String = when (state) {
    CameraConnectionState.Disconnected -> "未连接"
    is CameraConnectionState.Connecting -> "连接中"
    is CameraConnectionState.Handshaking -> "握手中"
    is CameraConnectionState.Connected -> "已连接"
    is CameraConnectionState.Error -> "连接异常"
}

private fun connectionTone(state: CameraConnectionState): StatusTone = when (state) {
    is CameraConnectionState.Connected -> StatusTone.Success
    is CameraConnectionState.Connecting,
    is CameraConnectionState.Handshaking,
    -> StatusTone.Warning

    is CameraConnectionState.Error -> StatusTone.Error
    CameraConnectionState.Disconnected -> StatusTone.Neutral
}
