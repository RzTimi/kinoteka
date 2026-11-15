package com.kinoteka.app.ui.account

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kinoteka.app.data.sync.DriveApi
import com.kinoteka.app.data.sync.GoogleAuth
import com.kinoteka.app.data.sync.SyncManager
import com.kinoteka.app.data.sync.SyncState
import com.kinoteka.app.ui.LocalContainer
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Запуск входа через Google: сначала тихая попытка, при необходимости —
 * системное окно выбора аккаунта и согласия на доступ к Диску.
 */
@Composable
fun rememberGoogleSignIn(sync: SyncManager): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            runCatching { sync.resultFromIntent(res.data) }
                .onSuccess(sync::onAuthorized)
                .onFailure { sync.reportError(GoogleAuth.describe(it)) }
        }
    }
    return {
        scope.launch {
            runCatching { sync.beginSignIn() }
                .onSuccess { result ->
                    val pending = result.pendingIntent
                    if (result.hasResolution() && pending != null) {
                        launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else {
                        sync.onAuthorized(result)
                    }
                }
                .onFailure { sync.reportError(GoogleAuth.describe(it)) }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("uk"))
fun formatSyncTime(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val sync = LocalContainer.current.sync
    val state by sync.state.collectAsStateWithLifecycle()
    val signIn = rememberGoogleSignIn(sync)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Google Диск") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.signedIn) SignedInContent(state, sync, signIn) else SignedOutContent(state, signIn)
            }
        }
    }
}

@Composable
private fun SignedOutContent(state: SyncState, signIn: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CloudSync, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Синхронізація з Google Диском", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Benefit(Icons.Default.Restore, "Оцінки не загубляться", "Навіть якщо видалите застосунок або загубите телефон.")
            Benefit(Icons.Default.Devices, "На кількох пристроях", "Увійдіть тим самим акаунтом, і все підтягнеться.")
            Benefit(Icons.Default.FolderOpen, "Папка «${SyncManager.FOLDER_NAME}» на вашому Диску", "Там лежать оцінки (data.json) і свої постери.")
            Benefit(Icons.Default.Lock, "Тільки своя папка", "Застосунок не бачить інших ваших файлів на Диску.")
        }
    }
    state.error?.let { ErrorText(it) }
    Button(onClick = signIn, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Увійти через Google") }
}

@Composable
private fun SignedInContent(state: SyncState, sync: SyncManager, signIn: () -> Unit) {
    val uri = LocalUriHandler.current
    var confirmSignOut by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val letter = (state.name ?: state.email ?: "?").first().uppercase()
                Text(letter, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (state.photo != null) {
                    AsyncImage(state.photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(state.name ?: "Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(state.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.syncing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            else Icon(Icons.Default.CloudSync, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    when {
                        state.syncing -> "Синхронізація…"
                        state.lastSync != null -> "Синхронізовано: ${formatSyncTime(state.lastSync)}"
                        else -> "Ще не синхронізовано"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Зміни зберігаються на Диск автоматично через кілька секунд.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    state.error?.let { ErrorText(it) }
    if (state.needsReauth) {
        Button(onClick = signIn, modifier = Modifier.fillMaxWidth()) { Text("Увійти знову") }
    }

    FilledTonalButton(onClick = sync::syncNow, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Sync, null)
        Spacer(Modifier.width(8.dp))
        Text("Синхронізувати зараз")
    }
    state.folderId?.let { id ->
        OutlinedButton(onClick = { uri.openUri(DriveApi.folderUrl(id)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text("Відкрити папку на Диску")
        }
    }
    TextButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.Logout, null)
        Spacer(Modifier.width(8.dp))
        Text("Вийти з акаунта")
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Вийти з акаунта?") },
            text = { Text("Синхронізація зупиниться. Ваші оцінки залишаться і на телефоні, і в папці «${SyncManager.FOLDER_NAME}» на Диску.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; sync.signOut() }) { Text("Вийти") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
}

/** Приветствие при первом запуске: предложить вход через Google. */
@Composable
fun WelcomeSyncDialog(onSignIn: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = { Icon(Icons.Default.CloudSync, null) },
        title = { Text("Зберігати оцінки на Google Диску?") },
        text = {
            Text("Увійдіть через Google, і ваші оцінки, нотатки та свої постери зберігатимуться в папці «${SyncManager.FOLDER_NAME}» на вашому Диску. Так вони не загубляться і будуть доступні на інших телефонах.\n\nМожна зробити це пізніше: «Мої оцінки» → значок хмари.")
        },
        confirmButton = { Button(onClick = onSignIn) { Text("Увійти через Google") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Пізніше") } },
    )
}
