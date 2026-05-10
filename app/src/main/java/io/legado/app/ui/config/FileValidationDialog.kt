package io.legado.app.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.help.storage.BackupInfoHelper
import io.legado.app.help.storage.ValidationResult
import io.legado.app.help.storage.ValidationState

@Composable
fun FileValidationDialog(
    files: List<BackupInfoHelper.BackupFileInfo>,
    validationResults: Map<String, ValidationResult>,
    onValidate: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onInfoClick: (ValidationResult) -> Unit
) {
    val checkedStates = remember { mutableStateMapOf<Int, Boolean>().apply { files.indices.forEach { put(it, true) } } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择要恢复的文件",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = onValidate) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("检测格式")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(files) { index, file ->
                        val result = validationResults[file.fileName]
                        FileValidationItem(
                            file = file,
                            isChecked = checkedStates[index] ?: true,
                            result = result,
                            onCheckedChange = { checked -> checkedStates[index] = checked },
                            onInfoClick = { result?.let { onInfoClick(it) } }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val selectedFiles = files.filterIndexed { index, _ ->
                            checkedStates[index] == true
                        }.map { it.fileName }
                        onConfirm(selectedFiles)
                    }) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileValidationItem(
    file: BackupInfoHelper.BackupFileInfo,
    isChecked: Boolean,
    result: ValidationResult?,
    onCheckedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            )
        )

        Text(
            text = "${file.displayName} (${BackupInfoHelper.formatSize(file.size)})",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )

        when (result?.state) {
            ValidationState.VALID -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "有效",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            ValidationState.WARNING, ValidationState.ERROR -> {
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = "查看详情",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "无效",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
            ValidationState.VALIDATING -> {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = "验证中",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            else -> {}
        }
    }
}

@Composable
fun ValidationErrorDetailDialog(
    result: ValidationResult,
    onDismiss: () -> Unit
) {
    val icon = when (result.state) {
        ValidationState.WARNING -> Icons.Default.Warning
        ValidationState.ERROR -> Icons.Default.Error
        else -> Icons.Default.Help
    }

    val message = buildString {
        append(result.message)
        if (result.details.isNotBlank()) {
            append("\n\n${result.details}")
        }
        if (result.missingFields.isNotEmpty()) {
            append("\n\n缺少字段: ${result.missingFields.joinToString(", ")}")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (result.state == ValidationState.ERROR)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.tertiary
            )
        },
        title = {
            Text(text = result.fileName)
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}