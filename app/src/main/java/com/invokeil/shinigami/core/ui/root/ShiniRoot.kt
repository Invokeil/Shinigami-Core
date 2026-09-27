package com.invokeil.shinigami.core.ui.root

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.invokeil.shinigami.R
import com.invokeil.shinigami.ThemeViewModel
import com.invokeil.shinigami.feature.audit.AuditScreen
import com.invokeil.shinigami.feature.commands.CommandsScreen
import com.invokeil.shinigami.feature.history.HistoryScreen
import com.invokeil.shinigami.feature.home.AssistantScreen
import com.invokeil.shinigami.feature.home.AssistantViewModel
import com.invokeil.shinigami.feature.onboarding.OnboardingScreen
import com.invokeil.shinigami.feature.permissions.PermissionsScreen
import com.invokeil.shinigami.feature.providers.ProviderEditorScreen
import com.invokeil.shinigami.feature.providers.ProvidersScreen
import com.invokeil.shinigami.feature.settings.SettingsScreen

object Routes {
    const val ASSISTANT = "assistant"
    const val HISTORY = "history"
    const val PROVIDERS = "providers"
    const val PROVIDER_EDITOR = "provider_editor/{providerId}"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val AUDIT = "audit"
    const val COMMANDS = "commands"

    fun providerEditor(id: Long) = "provider_editor/$id"
}

private data class BottomDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
)

@Composable
fun ShiniRoot(
    autoStartVoice: Boolean,
    onVoiceConsumed: () -> Unit,
    activityViewModel: ThemeViewModel? = null,
) {
    val navController: NavHostController = rememberNavController()
    val themeVm: ThemeViewModel = activityViewModel ?: hiltViewModel()
    val themeState by themeVm.themeState.collectAsState()

    if (!themeState.onboardingDone) {
        OnboardingScreen(onFinished = { themeVm.requestVoiceStart() })
        return
    }

    val destinations = listOf(
        BottomDestination(Routes.ASSISTANT, Icons.Rounded.SmartToy, R.string.nav_assistant),
        BottomDestination(Routes.HISTORY, Icons.Rounded.ChatBubbleOutline, R.string.nav_history),
        BottomDestination(Routes.PROVIDERS, Icons.Rounded.Tune, R.string.nav_providers),
        BottomDestination(Routes.SETTINGS, Icons.Rounded.Settings, R.string.nav_settings),
    )

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in destinations.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    destinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(Routes.ASSISTANT) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                            label = { Text(stringResource(dest.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = Routes.ASSISTANT,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.ASSISTANT) {
                    val vm: AssistantViewModel = hiltViewModel()
                    AssistantScreen(
                        viewModel = vm,
                        openProviders = { navController.navigate(Routes.PROVIDERS) },
                        reduceAnimations = themeState.reduceAnimations,
                        autoStartVoice = autoStartVoice,
                        onAutoStartConsumed = onVoiceConsumed,
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen()
                }
                composable(Routes.PROVIDERS) {
                    ProvidersScreen(
                        onAdd = { navController.navigate(Routes.providerEditor(0L)) },
                        onEdit = { id -> navController.navigate(Routes.providerEditor(id)) },
                    )
                }
                composable(Routes.PROVIDER_EDITOR) { entry ->
                    val id = entry.arguments?.getString("providerId")?.toLongOrNull() ?: 0L
                    ProviderEditorScreen(
                        providerId = id,
                        onDone = { navController.popBackStack() },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        themeViewModel = themeVm,
                        onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                        onOpenAudit = { navController.navigate(Routes.AUDIT) },
                        onOpenCommands = { navController.navigate(Routes.COMMANDS) },
                    )
                }
                composable(Routes.PERMISSIONS) {
                    PermissionsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.AUDIT) {
                    AuditScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.COMMANDS) {
                    CommandsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
