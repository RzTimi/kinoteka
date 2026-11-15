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
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.data.share.ShareLink
import com.kinoteka.app.ui.share.SharedListScreen
import com.kinoteka.app.ui.about.AboutScreen
import com.kinoteka.app.ui.settings.SettingsScreen
import com.kinoteka.app.ui.person.PersonScreen
import com.kinoteka.app.ui.manual.ManualScreen
import com.kinoteka.app.ui.stats.StatsScreen
import com.kinoteka.app.ui.library.ImportDialog
import com.kinoteka.app.ui.details.DetailsViewModel
import com.kinoteka.app.ui.details.SeasonScreen
import com.kinoteka.app.ui.details.displayTitle
import com.kinoteka.app.ui.components.UiState
import com.kinoteka.app.data.MediaType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kinoteka.app.ui.roulette.RouletteScreen
import com.kinoteka.app.ui.account.AccountScreen
import com.kinoteka.app.ui.account.WelcomeSyncDialog
import com.kinoteka.app.ui.account.rememberGoogleSignIn
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.kinoteka.app.ui.browse.BrowseScreen
import com.kinoteka.app.ui.components.LocalLibrary
import com.kinoteka.app.ui.components.LocalSectionPrefs
import com.kinoteka.app.ui.components.SectionPrefs
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
    val labelFn: () -> String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    val label: String get() = labelFn()
}

private val tabs = listOf(
    TopTab(HomeRoute, HomeRoute::class, { tr("Головна", "Home", "Главная") }, Icons.Outlined.Home, Icons.Filled.Home),
    TopTab(GenresRoute, GenresRoute::class, { tr("Жанри", "Genres", "Жанры") }, Icons.Outlined.Category, Icons.Filled.Category),
    TopTab(SearchRoute, SearchRoute::class, { tr("Пошук", "Search", "Поиск") }, Icons.Outlined.Search, Icons.Filled.Search),
    TopTab(LibraryRoute, LibraryRoute::class, { tr("Мої оцінки", "My ratings", "Мои оценки") }, Icons.Outlined.StarOutline, Icons.Filled.Star),
)

@Composable
fun KinotekaRoot(container: AppContainer) {
    val library by container.library.entries.collectAsStateWithLifecycle()
    val scale by container.ui.scale.collectAsStateWithLifecycle()
    val sectionStyle by container.ui.sectionStyle.collectAsStateWithLifecycle()
    val sectionOrder by container.ui.sectionOrder.collectAsStateWithLifecycle()
    val hiddenSections by container.ui.hiddenSections.collectAsStateWithLifecycle()
    val sectionPrefs = remember(sectionStyle, sectionOrder, hiddenSections) {
        SectionPrefs(visible = sectionOrder.filter { it !in hiddenSections }, style = sectionStyle)
    }
    // «Розмір інтерфейсу»: увеличиваем плотность, и пропорционально растёт всё — текст, кнопки, постеры.
    val base = LocalDensity.current
    val scaled = remember(base, scale) { Density(base.density * scale.factor, base.fontScale) }
    CompositionLocalProvider(
        LocalContainer provides container,
        LocalLibrary provides library,
        LocalDensity provides scaled,
        LocalSectionPrefs provides sectionPrefs,
    ) {
        if (!container.hasApiKey) {
            NoApiKeyScreen()
        } else {
            // Смена языка пересоздаёт экраны, чтобы всё загрузилось и перевелось заново.
            val language by container.ui.appLanguage.collectAsStateWithLifecycle()
            key(language) { AppScaffold() }
        }
    }
}

@Composable
private fun AppScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // Вкладка, в которой сейчас находимся. Экраны поверх (настройки, карточка фильма…)
    // считаются частью той вкладки, из которой их открыли.
    var currentTabName by rememberSaveable { mutableStateOf(tabs.first().routeClass.simpleName.orEmpty()) }
    LaunchedEffect(destination) {
        tabs.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }
            ?.let { currentTabName = it.routeClass.simpleName.orEmpty() }
    }
    // Выбранный раздел общий для Главной, Жанров и т.д.
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    // Если выбранный раздел скрыли в настройках — берём первый видимый.
    val visibleSections = LocalSectionPrefs.current.visible
    val section = Section.fromName(sectionName)?.takeIf { it in visibleSections } ?: visibleSections.first()
    val onSection: (Section) -> Unit = { sectionName = it.name }

    val openDetails: (MediaCard) -> Unit = { nav.navigate(DetailsRoute(it.type.path, it.id)) }

    // Открыли ссылкой «Открыть в Кінотеці»: один фильм — сразу карточка, подборка — её экран.
    val container = LocalContainer.current
    val pendingLink by container.pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLink) {
        val data = pendingLink ?: return@LaunchedEffect
        container.pendingLink.value = null
        val payload = ShareLink.decode(data) ?: return@LaunchedEffect
        if (payload.isSingle) {
            val item = payload.items.first()
            nav.navigate(DetailsRoute(item.type.path, item.id))
        } else {
            nav.navigate(SharedListRoute(data))
        }
    }
    var showImport by remember { mutableStateOf(false) }
    if (showImport) ImportDialog(onDismiss = { showImport = false })

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
                    val selected = tab.routeClass.simpleName == currentTabName
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) {
                                // Уже в этой вкладке: закрываем всё, что открыто поверх, и показываем саму вкладку.
                                if (!nav.popBackStack(tab.route, inclusive = false)) {
                                    nav.navigate(tab.route) { launchSingleTop = true }
                                }
                            } else {
                                currentTabName = tab.routeClass.simpleName.orEmpty()
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
                    onRoulette = { nav.navigate(RouletteRoute) },
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
                    onItem = { entry ->
                        if (entry.manual) nav.navigate(ManualRoute(entry.key))
                        else nav.navigate(DetailsRoute(entry.mediaType, entry.tmdbId))
                    },
                    onAccount = { nav.navigate(AccountRoute) },
                    onStats = { nav.navigate(StatsRoute) },
                    onAddManual = { nav.navigate(ManualRoute()) },
                    onImport = { showImport = true },
                )
            }
            composable<BrowseRoute> { entry ->
                val route = entry.toRoute<BrowseRoute>()
                BrowseScreen(route = route, onBack = { nav.popBackStack() }, onItem = openDetails)
            }
            composable<ManualRoute> { entry ->
                val route = entry.toRoute<ManualRoute>()
                ManualScreen(key = route.key, onBack = { nav.popBackStack() })
            }
            composable<SharedListRoute> { entry ->
                val route = entry.toRoute<SharedListRoute>()
                SharedListScreen(data = route.data, onBack = { nav.popBackStack() }, onOpen = openDetails)
            }
            composable<StatsRoute> {
                StatsScreen(onBack = { nav.popBackStack() })
            }
            composable<PersonRoute> { entry ->
                val route = entry.toRoute<PersonRoute>()
                PersonScreen(id = route.id, onBack = { nav.popBackStack() }, onOpen = openDetails)
            }
            composable<RouletteRoute> {
                RouletteScreen(onBack = { nav.popBackStack() }, onOpen = openDetails, section = section)
            }
            composable<DetailsRoute> { entry ->
                val route = entry.toRoute<DetailsRoute>()
                DetailsScreen(
                    route = route,
                    onBack = { nav.popBackStack() },
                    onOpen = openDetails,
                    onPerson = { nav.navigate(PersonRoute(it)) },
                    onSeason = { season -> nav.navigate(SeasonRoute(route.id, season, "")) },
                    onGenre = { genreSection, type, title, params ->
                        nav.navigate(BrowseRoute(genreSection.name, type.path, title, RouteParams.encode(params), sortable = true))
                    },
                )
            }
            composable<SeasonRoute> { entry ->
                val route = entry.toRoute<SeasonRoute>()
                val container = LocalContainer.current
                // Берём ViewModel карточки сериала (предыдущий экран), чтобы отметки сразу меняли статус.
                val parent = remember(entry) { nav.previousBackStackEntry ?: entry }
                val detailsVm: DetailsViewModel = viewModel(parent) {
                    DetailsViewModel(container.repository, container.library, container.posters, MediaType.TV, route.tvId)
                }
                val title = (detailsVm.state.collectAsStateWithLifecycle().value as? UiState.Success)?.data?.displayTitle(MediaType.TV).orEmpty()
                SeasonScreen(route.tvId, route.season, title, detailsVm, onBack = { nav.popBackStack() })
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
            Text(tr("Потрібен ключ TMDB", "TMDB key required", "Нужен ключ TMDB"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(tr("1. Зареєструйтесь на themoviedb.org", "1. Sign up at themoviedb.org", "1. Зарегистрируйтесь на themoviedb.org"))
            Text(tr("2. Налаштування → API → створіть ключ", "2. Settings → API → create a key", "2. Настройки → API → создайте ключ"))
            Text(
                tr(
                    "3. Відкрийте файл local.properties у корені проєкту та впишіть:\nTMDB_API_KEY=ваш_ключ",
                    "3. Open local.properties in the project root and add:\nTMDB_API_KEY=your_key",
                    "3. Откройте файл local.properties в корне проекта и впишите:\nTMDB_API_KEY=ваш_ключ",
                )
            )
            Text(tr("4. Перезапустіть застосунок з Android Studio (▶ Run)", "4. Restart the app from Android Studio (▶ Run)", "4. Перезапустите приложение из Android Studio (▶ Run)"))
        }
    }
}
