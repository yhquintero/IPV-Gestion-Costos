package cu.ipvgc.android

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cu.ipvgc.android.core.designsystem.IpvTheme
import cu.ipvgc.android.feature.auth.LoginRoute
import cu.ipvgc.android.feature.catalog.CatalogRoute
import cu.ipvgc.android.feature.control.ControlsRoute
import cu.ipvgc.android.feature.costing.CostSheetsRoute
import cu.ipvgc.android.feature.home.HomeRoute
import cu.ipvgc.android.feature.inventory.InventoryRoute
import cu.ipvgc.android.feature.ipv.IpvValuesRoute
import cu.ipvgc.android.feature.license.LicenseRoute
import cu.ipvgc.android.feature.rates.RatesRoute
import cu.ipvgc.android.feature.settings.SettingsRoute
import cu.ipvgc.android.feature.sync.SyncRoute

private data class Dest(val route: String, val label: String)

private val bar =
    listOf(
        Dest("home", "Inicio"),
        Dest("catalog", "Catálogo"),
        Dest("ipv", "IPV"),
        Dest("sheets", "Fichas"),
        Dest("license", "Licencia"),
    )

@Composable
fun IpvRoot() {
    IpvTheme {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        Scaffold(
            bottomBar = {
                if (route != null && route != "login") {
                    NavigationBar {
                        bar.forEach { d ->
                            NavigationBarItem(
                                selected = route == d.route,
                                onClick = {
                                    nav.navigate(d.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Text(d.label.take(1)) },
                                label = { Text(d.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = "login", modifier = Modifier.padding(padding)) {
                composable("login") { LoginRoute(onLoggedIn = { nav.navigate("home") { popUpTo("login") { inclusive = true } } }) }
                composable("home") { HomeRoute() }
                composable("catalog") { CatalogRoute() }
                composable("ipv") { IpvValuesRoute() }
                composable("sheets") { CostSheetsRoute() }
                composable("controls") { ControlsRoute() }
                composable("inventory") { InventoryRoute() }
                composable("rates") { RatesRoute() }
                composable("license") { LicenseRoute() }
                composable("sync") { SyncRoute() }
                composable("settings") { SettingsRoute() }
            }
        }
    }
}
