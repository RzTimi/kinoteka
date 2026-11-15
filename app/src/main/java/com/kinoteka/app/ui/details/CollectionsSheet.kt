package com.kinoteka.app.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AssistChipDefaults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kinoteka.app.ui.LocalContainer

/** Строка «Підбірки» в карточке: чипы подборок фильма и кнопка «Додати в підбірку». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionsRow(current: List<String>, onToggle: (String) -> Unit, onCreate: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        current.forEach { name ->
            AssistChip(
                onClick = { open = true },
                label = { Text(name) },
                leadingIcon = { Icon(Icons.Default.CollectionsBookmark, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
        AssistChip(
            onClick = { open = true },
            label = { Text(if (current.isEmpty()) "Додати в підбірку" else "Підбірки") },
            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
    }
    if (open) CollectionsSheet(current, onToggle, onCreate) { open = false }
}

/** Лист выбора подборок: галочки, новая подборка, удаление. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsSheet(current: List<String>, onToggle: (String) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val library = LocalContainer.current.library
    val all by library.collections.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Мої підбірки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(8.dp))
            if (all.isEmpty()) {
                Text("Поки немає жодної. Створіть першу, наприклад «Новорічне» чи «З друзями».", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            all.entries.sortedBy { it.value }.forEach { (name, _) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onToggle(name) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = name in current, onCheckedChange = { onToggle(name) })
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { toDelete = name }) { Icon(Icons.Outlined.Delete, "Видалити підбірку") }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Нова підбірка") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onCreate(newName.trim()); newName = "" },
                    enabled = newName.isNotBlank(),
                ) { Text("Створити") }
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    toDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Видалити підбірку «$name»?") },
            text = { Text("Фільми залишаться в бібліотеці, просто вийдуть із цієї підбірки.") },
            confirmButton = { TextButton(onClick = { library.deleteCollection(name); toDelete = null }) { Text("Видалити") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Скасувати") } },
        )
    }
}
