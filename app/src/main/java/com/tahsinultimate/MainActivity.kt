package com.tahsinultimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route ?: "home"
                val showBar = route == "home" || route.startsWith("mushaf") || route == "about"
                Scaffold(
                    bottomBar = {
                        if (showBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = route == "home",
                                    onClick = { nav.navigate("home") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.Home, "Latihan") },
                                    label = { Text("Latihan") },
                                )
                                NavigationBarItem(
                                    selected = route.startsWith("mushaf"),
                                    onClick = { nav.navigate("mushaf/0") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.MenuBook, "Mushaf") },
                                    label = { Text("Mushaf") },
                                )
                                NavigationBarItem(
                                    selected = route == "about",
                                    onClick = { nav.navigate("about") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.Info, "Tentang") },
                                    label = { Text("Tentang") },
                                )
                            }
                        }
                    },
                ) { pad ->
                    NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
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
}
