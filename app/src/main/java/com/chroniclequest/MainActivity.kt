package com.chroniclequest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chroniclequest.presentation.debug.OptimizationPanelScreen
import com.chroniclequest.presentation.home.HomeScreen
import com.chroniclequest.presentation.monitor.MonitorScreen
import com.chroniclequest.presentation.theme.ChronicleQuestTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChronicleQuestTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onOpenDebug = { navController.navigate(Routes.DEBUG) },
                            onOpenMonitor = { navController.navigate(Routes.MONITOR) },
                        )
                    }
                    composable(Routes.DEBUG) {
                        OptimizationPanelScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.MONITOR) {
                        MonitorScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val DEBUG = "debug"
    const val MONITOR = "monitor"
}
