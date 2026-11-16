package com.kinoteka.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kinoteka.app.BuildConfig
import com.kinoteka.app.data.update.AppUpdate
import com.kinoteka.app.i18n.tr
import com.kinoteka.app.ui.LocalContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** Что сейчас происходит с обновлением. */
private sealed interface UpdateStep {
    data object Offer : UpdateStep
    data class Downloading(val progress: Float) : UpdateStep
    /** Скачано, но Android ещё не разрешил Кинотеке ставить приложения. */
    data class NeedsPermission(val file: File) : UpdateStep
    data class Ready(val file: File) : UpdateStep
    data class Failed(val message: String) : UpdateStep
}

/**
 * «Вийшла нова версія»: что нового и «Оновити». Файл скачивается прямо в приложении,
 * потом открывается системный установщик — выходить в браузер не нужно.
 */
@Composable
fun UpdateDialog(update: AppUpdate, onDismiss: () -> Unit, onSkip: (() -> Unit)? = null) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val updates = LocalContainer.current.updates
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<UpdateStep>(UpdateStep.Offer) }
    var job by remember { mutableStateOf<Job?>(null) }

    // Вернулись из настроек «Установка неизвестных приложений».
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val s = step
        if (s is UpdateStep.NeedsPermission && canInstall(context)) {
            step = UpdateStep.Ready(s.file)
            installApk(context, s.file)
        }
    }

    fun startDownload() {
        step = UpdateStep.Downloading(0f)
        job = scope.launch {
            try {
                val file = updates.download(update) { p -> step = UpdateStep.Downloading(p) }
                if (canInstall(context)) {
                    step = UpdateStep.Ready(file)
                    installApk(context, file)
                } else {
                    step = UpdateStep.NeedsPermission(file)
                }
            } catch (e: CancellationException) {
                step = UpdateStep.Offer
                throw e
            } catch (e: Exception) {
                step = UpdateStep.Failed(
                    tr("Не вдалося завантажити. Перевірте інтернет.", "Download failed. Check your internet connection.", "Не удалось скачать. Проверьте интернет."),
                )
            }
        }
    }

    val busy = step is UpdateStep.Downloading
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Default.SystemUpdate, null) },
        title = { Text(tr("Вийшла версія ${update.version}", "Version ${update.version} is out", "Вышла версия ${update.version}")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val s = step) {
                    UpdateStep.Offer -> Text(
                        tr("У вас ${BuildConfig.VERSION_NAME}. Дані збережуться.", "You have ${BuildConfig.VERSION_NAME}. Your data will be kept.", "У вас ${BuildConfig.VERSION_NAME}. Данные сохранятся.") +
                            if (update.notes.isNotBlank()) "\n\n" + update.notes else "",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                    )
                    is UpdateStep.Downloading -> {
                        Text(
                            tr("Завантажуємо…", "Downloading…", "Скачиваем…") +
                                if (s.progress >= 0) " ${(s.progress * 100).toInt()}%" else "",
                        )
                        if (s.progress >= 0) LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateStep.NeedsPermission -> Text(
                        tr(
                            "Файл завантажено. Android попросить дозволити Кінотеці встановлювати застосунки — це потрібно лише один раз. Увімкніть перемикач і поверніться назад.",
                            "Downloaded. Android will ask to allow Kinoteka to install apps — only once. Turn the switch on and come back.",
                            "Файл скачан. Android попросит разрешить Кинотеке устанавливать приложения — это нужно только один раз. Включите переключатель и вернитесь назад.",
                        ),
                    )
                    is UpdateStep.Ready -> Text(
                        tr(
                            "Відкрилося вікно встановлення — натисніть «Оновити». Якщо воно не з'явилося, натисніть «Встановити» ще раз.",
                            "The installer has opened — tap “Update”. If it didn't appear, tap “Install” again.",
                            "Открылось окно установки — нажмите «Обновить». Если оно не появилось, нажмите «Установить» ещё раз.",
                        ),
                    )
                    is UpdateStep.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when (val s = step) {
                UpdateStep.Offer, is UpdateStep.Failed -> Button(
                    onClick = { if (update.apkUrl != null) startDownload() else { uri.openUri(update.downloadUrl); onDismiss() } },
                ) { Text(tr("Оновити", "Update", "Обновить")) }
                is UpdateStep.Downloading -> TextButton(onClick = { job?.cancel() }) { Text(tr("Скасувати", "Cancel", "Отмена")) }
                is UpdateStep.NeedsPermission -> Button(onClick = {
                    permissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text(tr("Дозволити", "Allow", "Разрешить")) }
                is UpdateStep.Ready -> Button(onClick = { installApk(context, s.file) }) { Text(tr("Встановити", "Install", "Установить")) }
            }
        },
        dismissButton = {
            if (!busy) {
                if (step is UpdateStep.Failed) {
                    // Запасной путь — скачать через браузер.
                    TextButton(onClick = { uri.openUri(update.downloadUrl); onDismiss() }) { Text(tr("У браузері", "In browser", "В браузере")) }
                } else if (step == UpdateStep.Offer && onSkip != null) {
                    TextButton(onClick = { onSkip(); onDismiss() }) { Text(tr("Пропустити", "Skip", "Пропустить")) }
                }
                TextButton(onClick = onDismiss) { Text(tr("Пізніше", "Later", "Позже")) }
            }
        },
    )
}

/** Разрешено ли Кинотеке ставить приложения (Android 8+ спрашивает один раз). */
private fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

/** Открыть системный установщик для скачанного APK. */
private fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
