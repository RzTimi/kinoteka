package com.kinoteka.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kinoteka.app.data.sync.AccountSwitch
import com.kinoteka.app.i18n.tr

/**
 * Вход в другой аккаунт, когда на телефоне оценки прежнего:
 * загрузить оценки нового или перенести то, что есть, в новый.
 */
@Composable
fun AccountSwitchDialog(switch: AccountSwitch, onLoadNew: () -> Unit, onMove: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.SwitchAccount, null) },
        title = { Text(tr("Інший акаунт", "Another account", "Другой аккаунт")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr(
                        "Ви входите як ${switch.email}, а на телефоні зараз оцінки акаунта ${switch.previous}. Вони збережені на його Google Диску.",
                        "You're signing in as ${switch.email}, but the phone has the ratings of ${switch.previous}. They are saved on that account's Google Drive.",
                        "Вы входите как ${switch.email}, а на телефоне сейчас оценки аккаунта ${switch.previous}. Они сохранены на его Google Диске.",
                    ),
                )
                if (switch.unsynced) {
                    Text(
                        tr(
                            "⚠ Зміни, зроблені після виходу з ${switch.previous}, ще ніде не збережені. Якщо завантажити оцінки нового акаунта, вони зникнуть.",
                            "⚠ Changes made after signing out of ${switch.previous} aren't saved anywhere yet. Loading the new account's ratings will remove them.",
                            "⚠ Изменения, сделанные после выхода из ${switch.previous}, ещё нигде не сохранены. Если загрузить оценки нового аккаунта, они пропадут.",
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Button(onClick = onLoadNew, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Завантажити оцінки нового акаунта", "Load the new account's ratings", "Загрузить оценки нового аккаунта"), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onMove, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Перенести ці оцінки в новий акаунт", "Move these ratings to the new account", "Перенести эти оценки в новый аккаунт"))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("Скасувати", "Cancel", "Отмена")) } },
    )
}
