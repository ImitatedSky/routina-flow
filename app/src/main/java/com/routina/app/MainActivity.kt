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
import android.net.Uri
import com.routina.app.ui.EditScreen
import com.routina.app.ui.GlobalVarsScreen
import com.routina.app.ui.HomeScreen
import com.routina.app.ui.LogScreen
import com.routina.app.ui.NfcLibraryScreen
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
    const val NFC = "nfc"
    const val GLOBALS = "globals"

    // 新建：可選帶入範本 id（?template=...）；不帶＝空白新建
    const val EDIT_NEW = "edit?template={templateId}"
    const val EDIT_EXISTING = "edit/{routineId}"
    // 從 NFC 標籤庫「設為觸發」進來：預先填好該標籤的 NFC 觸發
    const val EDIT_NFC = "edit_nfc?uid={uid}&name={name}"
    const val ARG_ROUTINE_ID = "routineId"
    const val ARG_TEMPLATE_ID = "templateId"
    const val ARG_NFC_UID = "uid"
    const val ARG_NFC_NAME = "name"

    fun edit(routineId: String) = "edit/$routineId"
    fun editNew(templateId: String? = null) =
        if (templateId == null) "edit" else "edit?template=$templateId"

    fun editNfc(uid: String, name: String) =
        "edit_nfc?uid=${Uri.encode(uid)}&name=${Uri.encode(name)}"
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
                onOpenLogs = { navController.navigate(Routes.LOGS) },
                onOpenNfc = { navController.navigate(Routes.NFC) },
                onOpenGlobals = { navController.navigate(Routes.GLOBALS) }
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

        composable(Routes.GLOBALS) {
            GlobalVarsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.NFC) {
            NfcLibraryScreen(
                viewModel = viewModel,
                onUseAsTrigger = { uid, name ->
                    navController.navigate(Routes.editNfc(uid, name))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EDIT_NFC,
            arguments = listOf(
                navArgument(Routes.ARG_NFC_UID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Routes.ARG_NFC_NAME) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            EditScreen(
                viewModel = viewModel,
                routineId = null,
                nfcUid = entry.arguments?.getString(Routes.ARG_NFC_UID),
                nfcName = entry.arguments?.getString(Routes.ARG_NFC_NAME),
                onDone = { navController.popBackStack() }
            )
        }
    }
}
