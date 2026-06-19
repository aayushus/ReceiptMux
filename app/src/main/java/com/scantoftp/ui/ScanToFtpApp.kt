package com.scantoftp.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.graphics.Color
import com.scantoftp.ui.navigation.Destination
import com.scantoftp.ui.theme.Brand
import com.scantoftp.ui.screen.CropAdjustScreen
import com.scantoftp.ui.screen.DashboardScreen
import com.scantoftp.ui.screen.DiagnosticsScreen
import com.scantoftp.ui.screen.PreviewScreen
import com.scantoftp.ui.screen.QueueScreen
import com.scantoftp.ui.screen.ReceiptViewerScreen
import com.scantoftp.ui.screen.ScannerScreen
import com.scantoftp.ui.screen.ServerEditorScreen
import com.scantoftp.ui.screen.ServerListScreen
import com.scantoftp.ui.screen.SettingsScreen
import com.scantoftp.ui.screen.UploadResultScreen
import com.scantoftp.ui.viewmodel.AppViewModel

@Composable
fun ScanToFtpApp(
    launchRoute: String? = null,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val bottomDestinations = listOf(Destination.Home, Destination.Queue, Destination.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val pendingUploadCount by viewModel.pendingUploadCount.collectAsStateWithLifecycle()
    val fullScreenRoutes = setOf(
        Destination.Scanner.route,
        Destination.Preview.route,
        Destination.CropAdjust.route,
        Destination.UploadResult.routeWithArg,
        Destination.ReceiptViewer.routeWithArg,
        Destination.ServerList.routeWithArg,
        Destination.ServerEditor.routeWithArg,
        Destination.Diagnostics.route,
    )

    LaunchedEffect(launchRoute) {
        if (!launchRoute.isNullOrBlank() && currentRoute != launchRoute) {
            navController.navigate(launchRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute !in fullScreenRoutes) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    bottomDestinations.forEach { destination ->
                        val accent = destination.navAccent()
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (destination == Destination.Queue && pendingUploadCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(pendingUploadCount.coerceAtMost(99).toString())
                                            }
                                        },
                                    ) {
                                        androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label)
                                    }
                                } else {
                                    androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label)
                                }
                            },
                            label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accent,
                                selectedTextColor = accent,
                                indicatorColor = accent.copy(alpha = 0.16f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(
                route = Destination.Home.route,
                enterTransition = { fadeThroughIn() },
                exitTransition = { fadeThroughOut() },
            ) {
                DashboardScreen(
                    onScan = { navController.navigate(Destination.Scanner.route) },
                    onOpenQueue = {
                        navController.navigate(Destination.Queue.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenSettings = { navController.navigate(Destination.Settings.route) },
                    onOpenReceipt = { receiptId ->
                        navController.navigate(Destination.ReceiptViewer.routeFor(receiptId))
                    },
                )
            }
            composable(
                route = Destination.Scanner.route,
                enterTransition = { fadeThroughIn() },
                exitTransition = { fadeThroughOut() },
            ) {
                ScannerScreen(
                    onPreviewRequested = { navController.navigate(Destination.Preview.route) },
                    onSettingsRequested = { navController.navigate(Destination.Settings.route) },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.Queue.route,
                enterTransition = { sharedXAxisIn(direction = AnimatedContentTransitionScope.SlideDirection.Left) },
                exitTransition = { sharedXAxisOut(direction = AnimatedContentTransitionScope.SlideDirection.Left) },
            ) {
                QueueScreen(
                    onOpenReceipt = { receiptId ->
                        navController.navigate(Destination.ReceiptViewer.routeFor(receiptId))
                    },
                )
            }
            composable(
                route = Destination.Settings.route,
                enterTransition = { sharedXAxisIn(direction = AnimatedContentTransitionScope.SlideDirection.Left) },
                exitTransition = { sharedXAxisOut(direction = AnimatedContentTransitionScope.SlideDirection.Right) },
            ) {
                SettingsScreen(
                    onOpenServers = { kind -> navController.navigate(Destination.ServerList.routeFor(kind)) },
                    onOpenDiagnostics = { navController.navigate(Destination.Diagnostics.route) },
                )
            }
            composable(
                route = Destination.Preview.route,
                enterTransition = { fadeThroughIn() },
                popExitTransition = { fadeThroughOut() },
            ) {
                PreviewScreen(
                    onBack = { navController.popBackStack() },
                    onAdjustCrop = { navController.navigate(Destination.CropAdjust.route) },
                    onSettingsRequested = { navController.navigate(Destination.Settings.route) },
                    onUploaded = { receiptId ->
                        navController.navigate(Destination.UploadResult.routeFor(receiptId)) {
                            popUpTo(Destination.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Destination.CropAdjust.route,
                enterTransition = { fadeThroughIn() },
                popExitTransition = { fadeThroughOut() },
            ) {
                CropAdjustScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.UploadResult.routeWithArg,
                arguments = listOf(navArgument(Destination.UploadResult.ARG_RECEIPT_ID) { type = NavType.StringType }),
                enterTransition = { fadeThroughIn() },
                popExitTransition = { fadeThroughOut() },
            ) {
                UploadResultScreen(
                    onScanAnother = {
                        navController.navigate(Destination.Scanner.route) {
                            popUpTo(Destination.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onDone = {
                        navController.popBackStack(Destination.Home.route, inclusive = false)
                    },
                    onViewQueue = {
                        navController.navigate(Destination.Queue.route) {
                            popUpTo(Destination.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Destination.ReceiptViewer.routeWithArg,
                arguments = listOf(navArgument(Destination.ReceiptViewer.ARG_RECEIPT_ID) { type = NavType.StringType }),
                enterTransition = { fadeIn(animationSpec = tween(180)) },
                exitTransition = { fadeOut(animationSpec = tween(120)) },
                popExitTransition = { fadeOut(animationSpec = tween(120)) },
            ) {
                ReceiptViewerScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.ServerList.routeWithArg,
                arguments = listOf(navArgument(Destination.ServerList.ARG_KIND) { type = NavType.StringType }),
                enterTransition = { sharedXAxisIn(direction = AnimatedContentTransitionScope.SlideDirection.Left) },
                popExitTransition = { sharedXAxisOut(direction = AnimatedContentTransitionScope.SlideDirection.Right) },
            ) { backStackEntry ->
                val kind = backStackEntry.arguments?.getString(Destination.ServerList.ARG_KIND).orEmpty()
                ServerListScreen(
                    kind = kind,
                    onBack = { navController.popBackStack() },
                    onAddServer = { navController.navigate(Destination.ServerEditor.routeFor(kind)) },
                    onEditServer = { id -> navController.navigate(Destination.ServerEditor.routeFor(kind, id)) },
                )
            }
            composable(
                route = Destination.ServerEditor.routeWithArg,
                arguments = listOf(
                    navArgument(Destination.ServerEditor.ARG_KIND) { type = NavType.StringType },
                    navArgument(Destination.ServerEditor.ARG_PROFILE_ID) { type = NavType.StringType },
                ),
                enterTransition = { sharedXAxisIn(direction = AnimatedContentTransitionScope.SlideDirection.Left) },
                popExitTransition = { sharedXAxisOut(direction = AnimatedContentTransitionScope.SlideDirection.Right) },
            ) { backStackEntry ->
                ServerEditorScreen(
                    kind = backStackEntry.arguments?.getString(Destination.ServerEditor.ARG_KIND).orEmpty(),
                    profileId = backStackEntry.arguments?.getString(Destination.ServerEditor.ARG_PROFILE_ID)
                        ?: Destination.ServerEditor.NEW_PROFILE,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.Diagnostics.route,
                enterTransition = { sharedXAxisIn(direction = AnimatedContentTransitionScope.SlideDirection.Left) },
                popExitTransition = { sharedXAxisOut(direction = AnimatedContentTransitionScope.SlideDirection.Right) },
            ) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun Destination.navAccent(): Color = when (this) {
    Destination.Home -> Brand.Indigo
    Destination.Queue -> Brand.Blue
    Destination.Settings -> Brand.Violet
    else -> Brand.Indigo
}

private fun AnimatedContentTransitionScope<*>.fadeThroughIn(): EnterTransition =
    fadeIn(animationSpec = tween(220, delayMillis = 90))

private fun AnimatedContentTransitionScope<*>.fadeThroughOut(): ExitTransition =
    fadeOut(animationSpec = tween(90))

private fun AnimatedContentTransitionScope<*>.sharedXAxisIn(
    direction: AnimatedContentTransitionScope.SlideDirection,
): EnterTransition = when (direction) {
    AnimatedContentTransitionScope.SlideDirection.Left ->
        fadeIn(animationSpec = tween(300)) + androidx.compose.animation.slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { it / 6 },
        )

    AnimatedContentTransitionScope.SlideDirection.Right ->
        fadeIn(animationSpec = tween(300)) + androidx.compose.animation.slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { -it / 6 },
        )

    else -> fadeIn(animationSpec = tween(300))
}

private fun AnimatedContentTransitionScope<*>.sharedXAxisOut(
    direction: AnimatedContentTransitionScope.SlideDirection,
): ExitTransition = when (direction) {
    AnimatedContentTransitionScope.SlideDirection.Left ->
        fadeOut(animationSpec = tween(300)) + androidx.compose.animation.slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { -it / 6 },
        )

    AnimatedContentTransitionScope.SlideDirection.Right ->
        fadeOut(animationSpec = tween(300)) + androidx.compose.animation.slideOutHorizontally(
            animationSpec = tween(300),
            targetOffsetX = { it / 6 },
        )

    else -> fadeOut(animationSpec = tween(300))
}
