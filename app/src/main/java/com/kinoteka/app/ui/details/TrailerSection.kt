package com.kinoteka.app.ui.details

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.kinoteka.app.data.tmdb.Video
import com.kinoteka.app.i18n.I18n
import com.kinoteka.app.i18n.tr
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/** Видео с YouTube, отсортированные: сначала трейлеры, среди них сначала украинские. */
fun youtubeVideos(videos: List<Video>): List<Video> = videos
    .filter { it.site == "YouTube" && it.key.isNotBlank() }
    .distinctBy { it.key }
    .sortedWith(compareBy({ typeOrder(it.type) }, { languageOrder(it.language) }))
    .take(20)

private fun typeOrder(type: String) = when (type) {
    "Trailer" -> 0
    "Teaser" -> 1
    "Clip" -> 2
    "Featurette" -> 3
    "Behind the Scenes" -> 4
    else -> 5
}

/** Сначала трейлеры на языке приложения, потом английские. */
private fun languageOrder(lang: String?) = when (lang) {
    I18n.language.code -> 0
    "en" -> 1
    else -> 2
}

private fun typeLabel(type: String) = when (type) {
    "Trailer" -> tr("Трейлер", "Trailer", "Трейлер")
    "Teaser" -> tr("Тизер", "Teaser", "Тизер")
    "Clip" -> tr("Уривок", "Clip", "Отрывок")
    "Featurette" -> tr("Про фільм", "Featurette", "О фильме")
    "Behind the Scenes" -> tr("За лаштунками", "Behind the scenes", "За кадром")
    "Bloopers" -> tr("Кумедні моменти", "Bloopers", "Забавные моменты")
    "Opening Credits" -> tr("Заставка", "Opening credits", "Заставка")
    else -> tr("Відео", "Video", "Видео")
}

private fun videoLabel(v: Video) = listOfNotNull(
    typeLabel(v.type),
    when (v.language) { "uk" -> "UA"; "en" -> "EN"; null -> null; else -> v.language.uppercase() },
).joinToString(" · ")

private fun thumbnail(key: String) = "https://img.youtube.com/vi/$key/hqdefault.jpg"
private fun youtubeUrl(key: String) = "https://www.youtube.com/watch?v=$key"

/** Блок «Трейлер»: превью → встроенный плеер, ниже ряд других видео. */
@Composable
fun TrailerSection(videos: List<Video>, modifier: Modifier = Modifier) {
    if (videos.isEmpty()) return
    val uri = LocalUriHandler.current
    var selectedKey by rememberSaveable { mutableStateOf(videos.first().key) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = videos.firstOrNull { it.key == selectedKey } ?: videos.first()

    Column(modifier) {
        // На широком экране плеер не растягиваем на всю ширину.
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
        ) {
            when {
                error != null -> EmbedErrorBox(error!!) { uri.openUri(youtubeUrl(selected.key)) }
                playing -> YouTubePlayerBox(
                    videoKey = selected.key,
                    onError = { e ->
                        val notEmbeddable = e == PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ||
                            e == PlayerConstants.PlayerError.REQUEST_MISSING_HTTP_REFERER
                        error = when {
                            notEmbeddable -> tr("Автор заборонив показ цього відео в застосунках", "The owner doesn't allow this video to be played in apps", "Автор запретил показ этого видео в приложениях")
                            e == PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> tr("Відео видалено з YouTube", "The video was removed from YouTube", "Видео удалено с YouTube")
                            else -> tr("Не вдалося відтворити відео", "Couldn't play the video", "Не удалось воспроизвести видео")
                        }
                        // Встраивать нельзя: сразу открываем в YouTube.
                        if (notEmbeddable) uri.openUri(youtubeUrl(selected.key))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> VideoPreview(selected) { playing = true }
            }
        }
        VideoInfoRow(selected, onOpenYoutube = { uri.openUri(youtubeUrl(selected.key)) })

        if (videos.size > 1) {
            Text(
                tr("Інші відео", "More videos", "Другие видео"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(videos, key = { it.key }) { v ->
                    VideoThumb(v, isSelected = v.key == selected.key) {
                        selectedKey = v.key
                        error = null
                        playing = true
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoInfoRow(video: Video, onOpenYoutube: () -> Unit) {
    Row(
        Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp).widthIn(max = 736.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(video.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(videoLabel(video), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onOpenYoutube) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("YouTube")
        }
    }
}

@Composable
private fun VideoPreview(video: Video, onPlay: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
        AsyncImage(thumbnail(video.key), video.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, tr("Відтворити", "Play", "Воспроизвести"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun VideoThumb(video: Video, isSelected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(176.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(thumbnail(video.key), video.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(videoLabel(video), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(video.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmbedErrorBox(message: String, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpen) { Text(tr("Відкрити в YouTube", "Open in YouTube", "Открыть в YouTube")) }
    }
}

/**
 * Официальный плеер YouTube внутри приложения (через библиотеку android-youtube-player).
 * Видео идёт напрямую с YouTube, мы его не скачиваем.
 */
@Composable
private fun YouTubePlayerBox(
    videoKey: String,
    onError: (PlayerConstants.PlayerError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError by rememberUpdatedState(onError)
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var fsView by remember { mutableStateOf<View?>(null) }
    var fsExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    val playerView = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            addFullscreenListener(object : FullscreenListener {
                override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                    fsView = fullscreenView
                    fsExit = exitFullscreen
                }

                override fun onExitFullscreen() {
                    fsView = null
                    fsExit = null
                }
            })
            val options = IFramePlayerOptions.Builder(context)
                .controls(1)
                .fullscreen(1)
                .rel(0)
                .ivLoadPolicy(3)
                .langPref(I18n.language.code)
                .build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    player = youTubePlayer
                }

                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                    currentOnError(error)
                }
            }, options)
        }
    }

    // Плеер готов или выбрали другое видео: запускаем.
    LaunchedEffect(player, videoKey) { player?.loadVideo(videoKey, 0f) }

    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(playerView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }

    AndroidView(factory = { playerView }, modifier = modifier)

    fsView?.let { view -> FullscreenPlayer(view) { fsExit?.invoke() } }
}

/** Полноэкранный режим: окно на весь экран, горизонтальная ориентация, без системных панелей. */
@Composable
private fun FullscreenPlayer(view: View, onExit: () -> Unit) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose { }
        }
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    (view.parent as? ViewGroup)?.removeView(view)
                    addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                }
            },
            onRelease = { it.removeAllViews() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
