package com.example.networktest.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.networktest.R
import com.example.networktest.ui.screens.DnsLookupScreen // Placeholder
import com.example.networktest.ui.screens.IpInfoScreen // Placeholder
import com.example.networktest.ui.screens.PingScreen // Placeholder
import com.example.networktest.ui.screens.PortScanScreen // Placeholder
import com.example.networktest.ui.screens.SpeedTestScreen // Placeholder
import com.example.networktest.ui.screens.TracerouteScreen // Placeholder
import com.example.networktest.ui.screens.WifiAnalyzerScreen // Placeholder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var currentTitle by remember { mutableStateOf(Screen.IpInfo.title) } // Default title

    // Observe back stack entry to update title
    navController.addOnDestinationChangedListener { _, destination, _ ->
        allAppScreens.find { it.route == destination.route }?.let { screen ->
            currentTitle = screen.title
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTitle) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                // Using allAppScreens for bottom nav for now, can be changed to bottomNavItems for fewer items
                allAppScreens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.IpInfo.route, // Default screen
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.IpInfo.route) { IpInfoScreen() } // Pass ViewModel later
            composable(Screen.Ping.route) { PingScreen() }
            composable(Screen.Traceroute.route) { TracerouteScreen() }
            composable(Screen.PortScan.route) { PortScanScreen() }
            composable(Screen.WifiAnalyzer.route) { WifiAnalyzerScreen() }
            composable(Screen.DnsLookup.route) { DnsLookupScreen() }
            composable(Screen.SpeedTest.route) { SpeedTestScreen() }
            // Add composable for Screen.NetworkInterfaces if implemented
        }
    }
}
