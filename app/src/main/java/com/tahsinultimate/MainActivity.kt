package com.tahsinultimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tahsinultimate.ui.AboutScreen
import com.tahsinultimate.ui.AppViewModel
import com.tahsinultimate.ui.HomeScreen
import com.tahsinultimate.ui.MushafScreen
import com.tahsinultimate.ui.RecordScreen
import com.tahsinultimate.ui.ResultScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                val vm: AppViewModel = viewModel()
                NavHost(nav, startDestination = "home") {
                    composable("home") { HomeScreen(nav, vm) }
                    composable("record") { RecordScreen(nav, vm) }
                    composable("result") { ResultScreen(nav, vm) }
                    composable(
                        "mushaf/{idx}",
                        arguments = listOf(navArgument("idx") { type = NavType.IntType }),
                    ) { backStack ->
                        MushafScreen(backStack.arguments?.getInt("idx") ?: 0, nav, vm)
                    }
                    composable("about") { AboutScreen(nav, vm) }
                }
            }
        }
    }
}
