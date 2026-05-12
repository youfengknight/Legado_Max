package io.legado.app.ui.debuglog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.legado.app.model.debug.DebugCategory
import io.legado.app.model.debug.DebugEvent
import io.legado.app.model.debug.FlowStage
import io.legado.app.model.debug.SourceSubCategory
import io.legado.app.ui.debuglog.components.DebugCategoryTabs
import io.legado.app.ui.debuglog.components.DebugLogItem
import io.legado.app.ui.debuglog.components.DebugLogDetailDialog
import io.legado.app.ui.debuglog.components.FlowLogDetailDialog
import io.legado.app.ui.debuglog.components.FlowLogList
import io.legado.app.ui.debuglog.components.FlowStageFilter
import io.legado.app.ui.debuglog.viewmodel.DebugLogViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.utils.share

/**
 * 调试日志主界面
 *
 * 显示应用运行过程中的各类调试日志，支持分类筛选、搜索、导出等功能。
 * 主要包含以下分类：
 * - 全部：显示所有日志
 * - 应用：来自 AppLog 的一般性日志
 * - 网络：来自 OkHttp 拦截器的网络访问记录
 * - 书源：与书源相关的操作（包含子分类）
 * - RSS：与 RSS 源相关的操作
 * - Toast：应用内所有 Toast 消息记录
 * - 校验：来自 CheckSource 的校验结果
 * - 崩溃：来自 CrashHandler 的崩溃信息
 *
 * @param viewModel 调试日志 ViewModel，默认使用 viewModel() 创建
 * @param onDismiss 关闭界面的回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    viewModel: DebugLogViewModel = viewModel(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 收集 ViewModel 中的状态
    val uiState by viewModel.uiState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSubCategory by viewModel.selectedSubCategory.collectAsState()
    val selectedFlowStage by viewModel.selectedFlowStage.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    val filteredFlowLogs by viewModel.filteredFlowLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // 搜索框显示状态
    var showSearch by remember { mutableStateOf(false) }

    // 进入界面时刷新日志，确保显示最新数据
    LaunchedEffect(Unit) {
        viewModel.refreshLogs()
        viewModel.refreshFlowLogs()
    }

    Scaffold(
        topBar = {
            Column {
                // 顶部工具栏
                TopAppBar(
                    title = { Text("调试日志") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭"
                            )
                        }
                    },
                    actions = {
                        // 刷新按钮：手动刷新日志列表
                        IconButton(onClick = {
                            viewModel.refreshLogs()
                            viewModel.refreshFlowLogs()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新"
                            )
                        }
                        // 搜索按钮：切换搜索框显示
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索"
                            )
                        }
                        // 暂停/继续按钮：控制日志采集
                        IconButton(onClick = { viewModel.togglePause() }) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "继续" else "暂停"
                            )
                        }
                        // 清空按钮：清除所有日志
                        IconButton(onClick = { viewModel.clearLogs() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "清空"
                            )
                        }
                        // 导出按钮：导出当前筛选的日志
                        IconButton(onClick = {
                            val exportedText = viewModel.exportFilteredLogs()
                            context.share(exportedText, "导出调试日志")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "导出"
                            )
                        }
                    }
                )

                // 搜索框（可展开/收起）
                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery ?: "",
                        onValueChange = { viewModel.setSearchQuery(it.ifBlank { null }) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("搜索日志内容...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (!searchQuery.isNullOrBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery(null) }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                viewModel.setSearchQuery(searchQuery)
                            }
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 分类标签页（排除 RULE，因为它作为 SOURCE 的子分类显示）
            DebugCategoryTabs(
                selectedCategory = selectedCategory,
                categories = DebugCategory.entries.filter {
                    it != DebugCategory.RULE
                },
                onCategorySelected = viewModel::selectCategory
            )

            // 书源分类的子分类选择器
            if (selectedCategory == DebugCategory.SOURCE) {
                SourceSubCategoryTabs(
                    selectedSubCategory = selectedSubCategory,
                    onSubCategorySelected = viewModel::selectSubCategory
                )

                // 流程日志的阶段筛选器
                if (selectedSubCategory == SourceSubCategory.FLOW) {
                    FlowStageFilter(
                        selectedStage = selectedFlowStage,
                        onStageSelected = viewModel::selectFlowStage
                    )
                }
            }

            HorizontalDivider()

            // 日志内容区域
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // 加载中状态
                    uiState.isLoading -> {
                        LoadingIndicator()
                    }
                    // 书源流程日志视图
                    selectedCategory == DebugCategory.SOURCE && selectedSubCategory == SourceSubCategory.FLOW -> {
                        if (filteredFlowLogs.isEmpty()) {
                            EmptyState(
                                message = if (searchQuery.isNullOrBlank()) "暂无流程日志"
                                         else "未找到匹配的日志"
                            )
                        } else {
                            FlowLogList(
                                logs = filteredFlowLogs,
                                onLogClick = viewModel::selectFlowLog,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // 空状态
                    uiState.isEmpty || filteredLogs.isEmpty() -> {
                        EmptyState(
                            message = if (searchQuery.isNullOrBlank()) "暂无调试日志"
                                     else "未找到匹配的日志"
                        )
                    }
                    // 普通日志列表
                    else -> {
                        DebugLogList(
                            logs = filteredLogs,
                            onLogClick = viewModel::selectLog,
                            onCopyLog = viewModel::copyLogDetail
                        )
                    }
                }

                // 日志详情弹窗
                if (uiState.selectedLog != null) {
                    DebugLogDetailDialog(
                        log = uiState.selectedLog!!,
                        onDismiss = { viewModel.clearSelection() },
                        onCopy = { viewModel.copyLogDetail(uiState.selectedLog!!) }
                    )
                }

                // 流程日志详情弹窗
                if (uiState.selectedFlowLog != null) {
                    FlowLogDetailDialog(
                        log = uiState.selectedFlowLog!!,
                        onDismiss = { viewModel.clearSelection() },
                        onCopy = { viewModel.copyFlowLogDetail(uiState.selectedFlowLog!!) }
                    )
                }
            }
        }
    }
}

/**
 * 加载指示器
 */
@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 空状态提示
 *
 * @param message 提示消息
 */
@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 日志列表容器
 *
 * 使用 LazyColumn 实现高性能的日志列表滚动
 *
 * @param logs 日志列表
 * @param onLogClick 日志点击回调
 * @param onCopyLog 日志长按复制回调
 */
@Composable
private fun DebugLogList(
    logs: List<DebugEvent>,
    onLogClick: (DebugEvent) -> Unit,
    onCopyLog: (DebugEvent) -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        items(
            count = logs.size,
            key = { index -> logs[index].id }
        ) { index ->
            val log = logs[index]
            DebugLogItem(
                log = log,
                onClick = onLogClick,
                onLongClick = onCopyLog,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 源日志子分类选择器
 *
 * 用于书源分类下的子分类筛选：
 * - 全部：显示所有书源相关日志
 * - 更新：书源更新相关
 * - 搜索：书源搜索相关
 * - 目录：书源目录相关
 * - 正文：书源正文相关
 * - 流程：书源操作流程日志
 *
 * @param selectedSubCategory 当前选中的子分类
 * @param onSubCategorySelected 子分类选择回调
 */
@Composable
private fun SourceSubCategoryTabs(
    selectedSubCategory: SourceSubCategory?,
    onSubCategorySelected: (SourceSubCategory?) -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            // "全部"选项
            androidx.compose.material3.FilterChip(
                selected = selectedSubCategory == null,
                onClick = { onSubCategorySelected(null) },
                label = { Text("全部") }
            )
            // 各子分类选项
            SourceSubCategory.entries.forEach { subCategory ->
                androidx.compose.material3.FilterChip(
                    selected = selectedSubCategory == subCategory,
                    onClick = { onSubCategorySelected(subCategory) },
                    label = { Text(subCategory.displayName) }
                )
            }
        }
    }
}
