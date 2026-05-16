package io.legado.app.ui.book.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.ui.book.storage.components.CacheItemCard
import io.legado.app.ui.book.storage.components.CacheSummaryCard
import io.legado.app.ui.book.storage.components.ClearAllConfirmDialog
import io.legado.app.ui.book.storage.components.ClearConfirmDialog
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageTopBarContainerColor

// UI层
// 4. StorageManageScreen.kt

// - 作用 ：Compose 主界面，显示缓存列表
// - 主要功能 ：
//   - TopAppBar 显示标题、刷新按钮、一键清理按钮
//   - 根据 UI状态 显示不同内容（Loading、Clearing、Error、Idle）
//   - LazyColumn 渲染缓存汇总卡片和缓存项列表
//   - 管理清理确认对话框的显示

data class ClearTarget(
    val cacheType: CacheType,
    val detailId: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManageScreen(
    viewModel: StorageManageViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val cacheItems by viewModel.cacheItems.collectAsState()
    val totalSize by viewModel.totalSize.collectAsState()
    
    var showClearDialog by remember { mutableStateOf<ClearTarget?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    
    val containerColor = pageCardContainerColor()
    val topBarColor = pageTopBarContainerColor()
    
    showClearDialog?.let { target ->
        val targetName = target.detailId ?: viewModel.getCacheName(target.cacheType)
        ClearConfirmDialog(
            targetName = targetName,
            onConfirm = {
                viewModel.clearCache(target.cacheType, target.detailId)
                showClearDialog = null
            },
            onDismiss = { showClearDialog = null }
        )
    }
    
    if (showClearAllDialog) {
        ClearAllConfirmDialog(
            onConfirm = {
                viewModel.clearAllCache()
                showClearAllDialog = false
            },
            onDismiss = { showClearAllDialog = false }
        )
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                title = {
                    Column {
                        Text(
                            text = "存储管理",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp, 
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadCacheInfo() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { showClearAllDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "一键清理")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is StorageUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is StorageUiState.Clearing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在清理 ${state.target}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is StorageUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = { viewModel.loadCacheInfo() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重试")
                        }
                    }
                }
            }
            is StorageUiState.Idle -> {
                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CacheSummaryCard(
                            totalSize = totalSize,
                            itemCount = cacheItems.size
                        )
                    }
                    
                    items(cacheItems) { item ->
                        CacheItemCard(
                            item = item,
                            onExpandClick = { 
                                viewModel.toggleExpand(CacheType.valueOf(item.id))
                            },
                            onClearClick = { 
                                showClearDialog = ClearTarget(CacheType.valueOf(item.id), null)
                            },
                            onDetailClearClick = { detailId ->
                                showClearDialog = ClearTarget(CacheType.valueOf(item.id), detailId)
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
