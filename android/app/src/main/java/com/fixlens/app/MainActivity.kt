package com.fixlens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fixlens.app.network.DiagnoseResponseDto
import com.fixlens.app.ui.screens.AssemblyCaptureScreen
import com.fixlens.app.ui.screens.ErrorState
import com.fixlens.app.ui.screens.GuidedRepairScreen
import com.fixlens.app.ui.screens.HomeScreen
import com.fixlens.app.ui.screens.LiveCameraScreen
import com.fixlens.app.ui.screens.MyRepairsScreen
import com.fixlens.app.ui.screens.PaywallReason
import com.fixlens.app.ui.screens.PaywallScreen
import com.fixlens.app.ui.screens.PhotoCaptureScreen
import com.fixlens.app.ui.screens.RepairsViewModel
import com.fixlens.app.ui.theme.FixLensTheme

/** FixLens routes. */
object Routes {
    const val HOME = "home"
    const val PHOTO = "photo"
    const val LIVE = "live"
    const val REPAIRS = "repairs"
    const val ASSEMBLY = "assembly"
    const val GUIDED_REPAIR = "guided_repair"
    const val PAYWALL = "paywall"
    const val DEMO_PICKER = "demo_picker"
    const val DEMO = "demo"
}

class MainActivity : ComponentActivity() {

    private val repairsViewModel: RepairsViewModel by viewModels {
        RepairsViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val container = (application as FixLensApp).appContainer
        setContent {
            FixLensTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FixLensNavHost(repairsViewModel, container.billingRepository)
                }
            }
        }
    }
}

/**
 * Phase 4 navigation. The confirmed diagnosis is held at nav-host level:
 * "Start Fix" on the diagnosis result stores it and routes to the guided
 * repair screen, which generates the plan once for the whole session.
 *
 * Phase 6: the paywall is a first-class route (also reachable from Home),
 * and the home badge reflects the real billing state.
 */
@Composable
private fun FixLensNavHost(
    repairsViewModel: RepairsViewModel,
    billingRepository: com.fixlens.app.billing.BillingRepository,
) {
    val navController = rememberNavController()
    var pendingDiagnosis by remember { mutableStateOf<DiagnoseResponseDto?>(null) }
    var paywallReason by remember { mutableStateOf(PaywallReason.PREMIUM_FEATURE) }
    var activeDemo by remember { mutableStateOf<com.fixlens.app.ui.screens.DemoContext?>(null) }
    val billingState by billingRepository.state.collectAsStateWithLifecycle()

    val planBadge: String? = when {
        !billingState.configured -> null
        billingState.isPro -> "FIXLENS PRO"
        else ->
            "${billingState.freeScansRemaining} free scan" +
                (if (billingState.freeScansRemaining == 1) "" else "s") + " left"
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onScanPhoto = { navController.navigate(Routes.PHOTO) },
                onLiveCamera = { navController.navigate(Routes.LIVE) },
                onMyRepairs = { navController.navigate(Routes.REPAIRS) },
                onAssemblyMode = { navController.navigate(Routes.ASSEMBLY) },
                onGetPro = {
                    paywallReason = PaywallReason.PREMIUM_FEATURE
                    navController.navigate(Routes.PAYWALL)
                },
                onDemoMode = { navController.navigate(Routes.DEMO_PICKER) },
                planBadge = planBadge,
            )
        }
        composable(Routes.PHOTO) {
            PhotoCaptureScreen(
                onDone = { navController.popBackStack() },
                onDismiss = { navController.popBackStack() },
                onStartFix = { result ->
                    pendingDiagnosis = result
                    navController.navigate(Routes.GUIDED_REPAIR)
                },
            )
        }
        composable(Routes.LIVE) {
            LiveCameraScreen(
                onDone = { navController.popBackStack() },
                onDismiss = { navController.popBackStack() },
                onStartFix = { result ->
                    pendingDiagnosis = result
                    navController.navigate(Routes.GUIDED_REPAIR)
                },
            )
        }
        composable(Routes.REPAIRS) {
            MyRepairsScreen(
                viewModel = repairsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ASSEMBLY) {
            AssemblyCaptureScreen(
                onDone = { navController.popBackStack() },
                onDismiss = { navController.popBackStack() },
            )
        }
        composable(Routes.GUIDED_REPAIR) {
            val diagnosis = pendingDiagnosis
            if (diagnosis == null) {
                ErrorState(
                    title = "No active diagnosis",
                    detail = "Scan the object first, then start the guided fix.",
                    actionLabel = "Back",
                    onAction = { navController.popBackStack() },
                )
            } else {
                GuidedRepairScreen(
                    diagnosis = diagnosis,
                    onExit = {
                        pendingDiagnosis = null
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(Routes.PAYWALL) {
            PaywallScreen(
                repository = billingRepository,
                reason = paywallReason,
                onDismiss = { navController.popBackStack() },
                onEntitled = {
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.DEMO_PICKER) {
            com.fixlens.app.ui.screens.DemoPickerScreen(
                onPick = { kind ->
                    activeDemo = com.fixlens.app.ui.screens.DemoContext(
                        kind = kind,
                        scenario = com.fixlens.app.demo.DemoScenarios.scenarioFor(kind),
                    )
                    navController.navigate(Routes.DEMO)
                },
                onExit = { navController.popBackStack() },
            )
        }
        composable(Routes.DEMO) {
            val demo = activeDemo
            if (demo == null) {
                ErrorState(
                    title = "No demo selected",
                    detail = "Pick a demo scenario to continue.",
                    actionLabel = "Back",
                    onAction = { navController.popBackStack() },
                )
            } else {
                com.fixlens.app.ui.screens.DemoFlowRouter(
                    demo = demo,
                    onExit = {
                        activeDemo = null
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
