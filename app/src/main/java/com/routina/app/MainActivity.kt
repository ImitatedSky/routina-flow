package com.routina.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.routina.app.ui.EditScreen
import com.routina.app.ui.HomeScreen
import com.routina.app.ui.LogScreen
import com.routina.app.ui.RoutineViewModel
import com.routina.app.ui.theme.RoutinaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RoutinaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RoutinaNavHost()
                }
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val LOGS = "logs"

    // 新建：可選帶入範本 id（?template=...）；不帶＝空白新建
    const val EDIT_NEW = "edit?template={templateId}"
    const val EDIT_EXISTING = "edit/{routineId}"
    const val ARG_ROUTINE_ID = "routineId"
    const val ARG_TEMPLATE_ID = "templateId"

    fun edit(routineId: String) = "edit/$routineId"
    fun editNew(templateId: String? = null) =
        if (templateId == null) "edit" else "edit?template=$templateId"
}

@Composable
private fun RoutinaNavHost() {
    val navController = rememberNavController()
    val viewModel: RoutineViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onCreate = { navController.navigate(Routes.editNew()) },
                onCreateFromTemplate = { id -> navController.navigate(Routes.editNew(id)) },
                onEdit = { id -> navController.navigate(Routes.edit(id)) },
                onOpenLogs = { navController.navigate(Routes.LOGS) }
            )
        }

        composable(
            route = Routes.EDIT_NEW,
            arguments = listOf(
                navArgument(Routes.ARG_TEMPLATE_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            EditScreen(
                viewModel = viewModel,
                routineId = null,
                templateId = entry.arguments?.getString(Routes.ARG_TEMPLATE_ID),
                onDone = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EDIT_EXISTING,
            arguments = listOf(navArgument(Routes.ARG_ROUTINE_ID) { type = NavType.StringType })
        ) { entry ->
            EditScreen(
                viewModel = viewModel,
                routineId = entry.arguments?.getString(Routes.ARG_ROUTINE_ID),
                onDone = { navController.popBackStack() }
            )
        }

        composable(Routes.LOGS) {
            LogScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
