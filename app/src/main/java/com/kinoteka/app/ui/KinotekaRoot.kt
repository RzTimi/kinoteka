package com.kinoteka.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kinoteka.app.AppContainer
import com.kinoteka.app.data.MediaCard
import com.kinoteka.app.data.Section
import com.kinoteka.app.ui.about.AboutScreen
import com.kinoteka.app.ui.settings.SettingsScreen
import com.kinoteka.app.ui.account.AccountScreen
import com.kinoteka.app.ui.account.WelcomeSyncDialog
import com.kinoteka.app.ui.account.rememberGoogleSignIn
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.kinoteka.app.ui.browse.BrowseScreen
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.details.DetailsScreen
import com.kinoteka.app.ui.genres.GenresScreen
import com.kinoteka.app.ui.home.HomeScreen
import com.kinoteka.app.ui.library.LibraryScreen
import com.kinoteka.app.ui.search.SearchScreen
import kotlin.reflect.KClass

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer не задан") }

private data class TopTab(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val tabs = listOf(
    TopTab(HomeRoute, HomeRoute::class, "Головна", Icons.Outlined.Home, Icons.Filled.Home),
    TopTab(GenresRoute, GenresRoute::class, "Жанри", Icons.Outlined.Category, Icons.Filled.Category),
    TopTab(SearchRoute, SearchRoute::class, "Пошук", Icons.Outlined.Search, Icons.Filled.Search),
    TopTab(LibraryRoute, LibraryRoute::class, "Мої оцінки", Icons.Outlined.StarOutline, Icons.Filled.Star),
)

@Composable
fun KinotekaRoot(container: AppContainer) {
    val library by container.library.entries.collectAsStateWithLifecycle()
    val scale by container.ui.scale.collectAsStateWithLifecycle()
    // «Розмір інтерфейсу»: увеличиваем плотность, и пропорционально растёт всё — текст, кнопки, постеры.
    val base = LocalDensity.current
    val scaled = remember(base, scale) { Density(base.density * scale.factor, base.fontScale) }
    CompositionLocalProvider(
        LocalContainer provides container,
        LocalLibrary provides library,
        LocalDensity provides scaled,
    ) {
        if (!container.hasApiKey) {
            NoApiKeyScreen()
        } else {
            AppScaffold()
        }
    }
}

@Composable
private fun AppScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // Выбранный раздел общий для Главной, Жанров и т.д.
    var sectionName by rememberSaveable { mutableStateOf(Section.MOVIES.name) }
    val section = Section.fromName(sectionName) ?: Section.MOVIES
    val onSection: (Section) -> Unit = { sectionName = it.name }

    val openDetails: (MediaCard) -> Unit = { nav.navigate(DetailsRoute(it.type.path, it.id)) }

    // Первый запуск: предложить хранить оценки на Google Диске (один раз).
    val sync = LocalContainer.current.sync
    var showWelcome by remember { mutableStateOf(!sync.welcomeShown && !sync.state.value.signedIn) }
    val signIn = rememberGoogleSignIn(sync)
    if (showWelcome) {
        WelcomeSyncDialog(
            onSignIn = { sync.welcomeShown = true; showWelcome = false; signIn() },
            onLater = { sync.welcomeShown = true; showWelcome = false },
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    section = section,
                    onSection = onSection,
                    onItem = openDetails,
                    onMore = { feed ->
                        nav.navigate(BrowseRoute(section.name, feed.type.path, feed.title, RouteParams.encode(feed.params)))
                    },
                    onSettings = { nav.navigate(SettingsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onAccount = { nav.navigate(AccountRoute) },
                    onAbout = { nav.navigate(AboutRoute) },
                )
            }
            composable<AboutRoute> {
                AboutScreen(onBack = { nav.popBackStack() }, onAccount = { nav.navigate(AccountRoute) })
            }
            composable<AccountRoute> { AccountScreen(onBack = { nav.popBackStack() }) }
            composable<GenresRoute> {
                GenresScreen(
                    section = section,
                    onSection = onSection,
                    onOpen = { type, title, params ->
                        nav.navigate(BrowseRoute(section.name, type.path, title, RouteParams.encode(params), sortable = true))
                    },
                )
            }
            composable<SearchRoute> { SearchScreen(onItem = openDetails) }
            composable<LibraryRoute> {
                LibraryScreen(
                    onItem = { entry -> nav.navigate(DetailsRoute(entry.mediaType, entry.tmdbId)) },
                    onAccount = { nav.navigate(AccountRoute) },
                )
            }
            composable<BrowseRoute> { entry ->
                val route = entry.toRoute<BrowseRoute>()
                BrowseScreen(route = route, onBack = { nav.popBackStack() }, onItem = openDetails)
            }
            composable<DetailsRoute> { entry ->
                val route = entry.toRoute<DetailsRoute>()
                DetailsScreen(route = route, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun NoApiKeyScreen() {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("Потрібен ключ TMDB", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("1. Зареєструйтесь на themoviedb.org")
            Text("2. Налаштування → API → створіть ключ")
            Text("3. Відкрийте файл local.properties у корені проєкту та впишіть:\nTMDB_API_KEY=ваш_ключ")
            Text("4. Перезапустіть застосунок з Android Studio (▶ Run)")
        }
    }
}
