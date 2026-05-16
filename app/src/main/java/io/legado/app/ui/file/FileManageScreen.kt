package io.legado.app.ui.file

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.ui.file.utils.FilePickerIcon
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageTopBarContainerColor
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManageScreen(
    viewModel: FileManageViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val files by viewModel.filesLiveData.collectAsState(initial = emptyList())
    val subDocs by viewModel.subDocsFlow.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState(initial = "")
    
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    
    val topBarColor = pageTopBarContainerColor()
    
    val upIcon = remember { bitmapFromBytes(FilePickerIcon.getUpDir()) }
    val folderIcon = remember { bitmapFromBytes(FilePickerIcon.getFolder()) }
    val fileIcon = remember { bitmapFromBytes(FilePickerIcon.getFile()) }
    val arrowIcon = remember { bitmapFromBytes(FilePickerIcon.getArrow()) }
    
    showDeleteDialog?.let { file ->
        DeleteConfirmDialog(
            fileName = file.name,
            onConfirm = {
                viewModel.delFile(file)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(modifier = Modifier.background(topBarColor)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                        titleContentColor = MaterialTheme.colorScheme.onSecondary,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.file_manage),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    hint = "${stringResource(R.string.screen)} • ${stringResource(R.string.file_manage)}"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PathBreadcrumb(
                subDocs = subDocs,
                arrowIcon = arrowIcon,
                onRootClick = { viewModel.goToRoot() },
                onPathClick = { index -> viewModel.goToPath(index) }
            )
            
            if (files.isEmpty()) {
                EmptyMessage()
            } else {
                FileList(
                    files = files,
                    lastDir = viewModel.lastDir,
                    upIcon = upIcon,
                    folderIcon = folderIcon,
                    fileIcon = fileIcon,
                    onFileClick = { file ->
                        when {
                            file == viewModel.lastDir -> viewModel.gotoLastDir()
                            file.isDirectory -> viewModel.enterDir(file)
                            else -> viewModel.openFile(file)
                        }
                    },
                    onFileLongClick = { file ->
                        if (file != viewModel.lastDir) {
                            showDeleteDialog = file
                        }
                    }
                )
            }
        }
    }
}

private fun bitmapFromBytes(bytes: ByteArray): ImageBitmap {
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String
) {
    val containerColor = pageCardContainerColor()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { })
                )
            }
        }
    }
}

@Composable
private fun PathBreadcrumb(
    subDocs: List<File>,
    arrowIcon: ImageBitmap,
    onRootClick: () -> Unit,
    onPathClick: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val containerColor = pageCardContainerColor()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(containerColor)
            .horizontalScroll(scrollState)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PathItem(
            text = "root",
            arrowIcon = arrowIcon,
            onClick = onRootClick
        )
        
        subDocs.forEachIndexed { index, file ->
            PathItem(
                text = file.name,
                arrowIcon = arrowIcon,
                onClick = { onPathClick(index) }
            )
        }
    }
}

@Composable
private fun PathItem(
    text: String,
    arrowIcon: ImageBitmap,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false)
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Image(
            bitmap = arrowIcon,
            contentDescription = null,
            modifier = Modifier
                .width(20.dp)
                .height(24.dp)
        )
    }
}

@Composable
private fun FileList(
    files: List<File>,
    lastDir: File?,
    upIcon: ImageBitmap,
    folderIcon: ImageBitmap,
    fileIcon: ImageBitmap,
    onFileClick: (File) -> Unit,
    onFileLongClick: (File) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 0.dp)
    ) {
        items(files) { file ->
            FileItem(
                file = file,
                isParentDir = file == lastDir,
                upIcon = upIcon,
                folderIcon = folderIcon,
                fileIcon = fileIcon,
                onClick = { onFileClick(file) },
                onLongClick = { onFileLongClick(file) }
            )
        }
    }
}

@Composable
private fun FileItem(
    file: File,
    isParentDir: Boolean,
    upIcon: ImageBitmap,
    folderIcon: ImageBitmap,
    fileIcon: ImageBitmap,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            isParentDir -> upIcon
            file.isDirectory -> folderIcon
            else -> fileIcon
        }
        
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = if (isParentDir) ".." else file.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete)) },
        text = { Text("确定要删除 \"$fileName\" 吗？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
