package com.gems.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gems.android.ui.screen.ComparisonScreen
import com.gems.android.ui.screen.DemoScreen
import com.gems.android.ui.screen.HomeScreen
import com.gems.android.ui.screen.ImageGenDemoScreen
import com.gems.android.ui.screen.ModelDownloadScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val DEMO = "demo"
    const val MODEL_DOWNLOAD = "model_download"
    const val IMAGE_GEN_DEMO = "image_gen_demo/{steps}"
    const val DEMO_WITH_MODEL = "demo/{useE4B}"
    const val COMPARISON = "comparison/{prompt}/{steps}/{iterations}/{useE4B}"

    fun comparison(prompt: String, steps: Int, iterations: Int, useE4B: Boolean): String {
        val encoded = URLEncoder.encode(prompt, "UTF-8")
        return "comparison/$encoded/$steps/$iterations/$useE4B"
    }

    fun demo(useE4B: Boolean) = "demo/$useE4B"

    fun imageGenDemo(steps: Int) = "image_gen_demo/$steps"
}

@Composable
fun GemsNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToComparison = { prompt, steps, iterations, useE4B ->
                    navController.navigate(Routes.comparison(prompt, steps, iterations, useE4B))
                },
                onNavigateToDemo = { useE4B ->
                    navController.navigate(Routes.demo(useE4B))
                },
                onNavigateToModelDownload = {
                    navController.navigate(Routes.MODEL_DOWNLOAD)
                },
                onNavigateToImageGenDemo = { steps ->
                    navController.navigate(Routes.imageGenDemo(steps))
                }
            )
        }

        composable(
            route = Routes.DEMO_WITH_MODEL,
            arguments = listOf(navArgument("useE4B") { type = NavType.BoolType })
        ) { backStackEntry ->
            val useE4B = backStackEntry.arguments?.getBoolean("useE4B") ?: false
            DemoScreen(useE4B = useE4B)
        }

        composable(Routes.MODEL_DOWNLOAD) {
            ModelDownloadScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.IMAGE_GEN_DEMO,
            arguments = listOf(navArgument("steps") { type = NavType.IntType })
        ) { backStackEntry ->
            val steps = backStackEntry.arguments?.getInt("steps") ?: 2
            ImageGenDemoScreen(
                onBack = { navController.popBackStack() },
                steps = steps,
            )
        }

        composable(
            route = Routes.COMPARISON,
            arguments = listOf(
                navArgument("prompt") { type = NavType.StringType },
                navArgument("steps") { type = NavType.IntType },
                navArgument("iterations") { type = NavType.IntType },
                navArgument("useE4B") { type = NavType.BoolType },
            )
        ) { backStackEntry ->
            val encodedPrompt = backStackEntry.arguments?.getString("prompt").orEmpty()
            val prompt = URLDecoder.decode(encodedPrompt, "UTF-8")
            val steps = backStackEntry.arguments?.getInt("steps") ?: 2
            val iterations = backStackEntry.arguments?.getInt("iterations") ?: 2
            val useE4B = backStackEntry.arguments?.getBoolean("useE4B") ?: false
            ComparisonScreen(
                prompt = prompt,
                steps = steps,
                iterations = iterations,
                useE4B = useE4B,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
