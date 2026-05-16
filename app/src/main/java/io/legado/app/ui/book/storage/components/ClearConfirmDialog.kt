package io.legado.app.ui.book.storage.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

// ### UI组件
// 7. ClearConfirmDialog.kt

// - 作用 ：清理确认对话框组件
// - 主要功能 ：
//   - ClearConfirmDialog - 单个缓存清理确认
//   - ClearAllConfirmDialog - 一键清理确认

@Composable
fun ClearConfirmDialog(
    targetName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("确认清理") },
        text = {
            Text("确定要清理 \"$targetName\" 吗？此操作不可撤销。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "确定",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ClearAllConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("一键清理") },
        text = {
            Text("确定要清理所有缓存吗？\n\n注意：这不会清理数据库中的书籍、书源等重要数据，仅清理临时缓存文件。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "确定",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
