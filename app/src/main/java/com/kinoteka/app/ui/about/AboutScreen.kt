package com.kinoteka.app.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kinoteka.app.BuildConfig
import com.kinoteka.app.R
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.components.LocalLibrary

private const val TMDB_URL = "https://www.themoviedb.org/"
private const val JUSTWATCH_URL = "https://www.justwatch.com/"
private const val YOUTUBE_URL = "https://www.youtube.com/"
private const val GITHUB_URL = "https://github.com/RzTimi/kinoteka"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onAccount: () -> Unit) {
    val uri = LocalUriHandler.current
    val library = LocalLibrary.current.values
    val rated = library.count { it.rating != null }
    val customPosters = library.count { it.customPoster != null }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(tr("Про застосунок", "About", "О приложении")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад", "Back", "Назад")) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Шапка: иконка, название, версия
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1B1622)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(96.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(tr("Кінотека", "Kinoteka", "Кинотека"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        tr("Версія ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}", "Версия ${BuildConfig.VERSION_NAME}"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr(
                            "Каталог фільмів, серіалів, дорам, аніме, мультфільмів і документального кіно з вашими власними оцінками.",
                            "A catalog of movies, TV shows, dramas, anime, cartoons and documentaries with your own ratings.",
                            "Каталог фильмов, сериалов, дорам, аниме, мультфильмов и документального кино с вашими собственными оценками.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                // Ваши данные
                InfoCard(tr("Ваші дані", "Your data", "Ваши данные")) {
                    InfoRow(
                        Icons.Default.Lock,
                        tr(
                            "Оцінки, статуси, нотатки та свої постери зберігаються лише на цьому пристрої. Застосунок не збирає жодних даних про вас і не показує рекламу.",
                            "Ratings, statuses, notes and your own posters are stored only on this device. The app collects no data about you and shows no ads.",
                            "Оценки, статусы, заметки и свои постеры хранятся только на этом устройстве. Приложение не собирает никаких данных о вас и не показывает рекламу.",
                        ),
                    )
                    Text(
                        tr(
                            "У бібліотеці: ${library.size} · оцінено: $rated · своїх постерів: $customPosters",
                            "In library: ${library.size} · rated: $rated · custom posters: $customPosters",
                            "В библиотеке: ${library.size} · оценено: $rated · своих постеров: $customPosters",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 36.dp),
                    )
                    HorizontalDivider()
                    LinkRow(
                        Icons.Default.CloudSync,
                        tr("Синхронізація з Google Диском", "Sync with Google Drive", "Синхронизация с Google Диском"),
                        tr("Резервна копія та доступ з інших телефонів", "Backup and access from other phones", "Резервная копия и доступ с других телефонов"),
                        onAccount,
                    )
                }

                // Источники данных (обязательная атрибуция TMDB и JustWatch)
                InfoCard(tr("Джерела даних", "Data sources", "Источники данных")) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { uri.openUri(TMDB_URL) }.padding(vertical = 4.dp),
                    ) {
                        Image(painterResource(R.drawable.tmdb_logo), "TMDB", Modifier.height(20.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(tr("Інформація про фільми й серіали, постери, фото та актори надані TMDB.", "Movie and TV show information, posters, photos and cast are provided by TMDB.", "Информация о фильмах и сериалах, постеры, фото и актёры предоставлены TMDB."), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Перевод обязательной английской атрибуции (в английском интерфейсе не нужен).
                        val disclaimer = tr(
                            "Цей продукт використовує TMDB API, але не схвалений і не сертифікований TMDB.",
                            "",
                            "Этот продукт использует TMDB API, но не одобрен и не сертифицирован TMDB.",
                        )
                        if (disclaimer.isNotEmpty()) {
                            Text(
                                disclaimer,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                    LinkRow(Icons.Default.Tv, tr("Де подивитись", "Where to watch", "Где посмотреть"), tr("Дані про онлайн-кінотеатри надані JustWatch", "Streaming service data provided by JustWatch", "Данные об онлайн-кинотеатрах предоставлены JustWatch")) { uri.openUri(JUSTWATCH_URL) }
                    LinkRow(Icons.Default.PlayCircle, tr("Трейлери", "Trailers", "Трейлеры"), tr("Відео відтворюються з YouTube", "Videos play from YouTube", "Видео воспроизводятся с YouTube")) { uri.openUri(YOUTUBE_URL) }
                }

                // Проект
                InfoCard(tr("Проєкт", "Project", "Проект")) {
                    LinkRow(Icons.Default.Code, tr("Вихідний код на GitHub", "Source code on GitHub", "Исходный код на GitHub"), "github.com/RzTimi/kinoteka") { uri.openUri(GITHUB_URL) }
                }

                Text(
                    tr("Зроблено в Україні 💙💛", "Made in Ukraine 💙💛", "Сделано в Украине 💙💛"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
