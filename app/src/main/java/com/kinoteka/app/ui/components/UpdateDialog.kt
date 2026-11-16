package com.kinoteka.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.kinoteka.app.BuildConfig
import com.kinoteka.app.data.update.AppUpdate
import com.kinoteka.app.i18n.tr

/** «Вийшла нова версія»: що нового, «Завантажити», «Пізніше», «Пропустити». */
@Composable
fun UpdateDialog(update: AppUpdate, onDismiss: () -> Unit, onSkip: (() -> Unit)? = null) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, null) },
        title = { Text(tr("Вийшла версія ${update.version}", "Version ${update.version} is out", "Вышла версия ${update.version}")) },
        text = {
            Text(
                tr("У вас ${BuildConfig.VERSION_NAME}. ", "You have ${BuildConfig.VERSION_NAME}. ", "У вас ${BuildConfig.VERSION_NAME}. ") +
                    tr(
                        "Завантажте файл і відкрийте його — застосунок оновиться, дані збережуться.",
                        "Download the file and open it — the app will update and keep your data.",
                        "Скачайте файл и откройте его — приложение обновится, данные сохранятся.",
                    ) +
                    if (update.notes.isNotBlank()) "\n\n" + update.notes else "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            Button(onClick = { uri.openUri(update.downloadUrl); onDismiss() }) {
                Text(tr("Завантажити", "Download", "Скачать"))
            }
        },
        dismissButton = {
            if (onSkip != null) {
                TextButton(onClick = { onSkip(); onDismiss() }) { Text(tr("Пропустити", "Skip", "Пропустить")) }
            }
            TextButton(onClick = onDismiss) { Text(tr("Пізніше", "Later", "Позже")) }
        },
    )
}
