/**
 * 书源检测界面 - Jetpack Compose实现
 *
 * 功能说明：
 * 提供书源检测的完整UI界面
 * 包括检测控制、进度显示、结果列表、统计信息
 * 支持结果过滤和排序
 *
 * 架构说明：
 * 使用Scaffold构建页面骨架
 * 使用LazyColumn展示检测结果列表
 * 使用Material Design 3组件和风格
 * 添加动画效果和渐变色设计
 * 观察CheckSourceUIState并根据状态渲染UI
 */
package io.legado.app.ui.book.source.check

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.data.entities.BookSource
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageMutedIconTint
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.theme.pageSurfaceVariantColor
import io.legado.app.ui.theme.pageTopBarContainerColor
import io.legado.app.utils.sendToClip

/**
 * 书源检测主界面
 *
 * @param viewModel ViewModel实例
 * @param onBackClick 返回按钮点击回调
 * @param onSelectSources 选择书源回调
 * @param onOpenConfig 打开配置回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckSourceScreen(
    viewModel: CheckSourceViewModel,
    onBackClick: () -> Unit,
    onSelectSources: () -> Unit = {},
    onOpenConfig: () -> Unit = {},
    onEditSource: (String) -> Unit = {},
    onDebugSource: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var detailResult by remember { mutableStateOf<CheckResult?>(null) }
    var showSourcePicker by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CheckSourceTopBar(
                uiState = uiState,
                onBackClick = onBackClick,
                onStopClick = { viewModel.stopCheck() },
                onClearClick = { viewModel.clearResults() },
                onConfigClick = onOpenConfig
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is CheckSourceUIState.Idle -> {
                    IdleContent(
                        statistics = state.statistics,
                        resultFilter = viewModel.resultFilter,
                        filteredResults = viewModel.filteredResults,
                        onStartCheck = { viewModel.startCheckSelectedSources() },
                        onSelectSources = {
                            onSelectSources()
                            showSourcePicker = true
                        },
                        onFilterChange = { viewModel.applyResultFilter(it) },
                        onResultClick = { detailResult = it },
                        onReCheckSource = { viewModel.reCheck(it.sourceUrl) },
                        onEditSource = { onEditSource(it.sourceUrl) },
                        onDebugSource = { onDebugSource(it.sourceUrl) }
                    )
                }
                is CheckSourceUIState.Checking -> {
                    CheckingContent(
                        progress = state.progress,
                        currentMessage = state.currentMessage,
                        statistics = state.statistics,
                        resultFilter = viewModel.resultFilter,
                        filteredResults = viewModel.filteredResults,
                        onPauseCheck = { viewModel.pauseCheck() },
                        onFilterChange = { viewModel.applyResultFilter(it) },
                        onResultClick = { detailResult = it },
                        onReCheckSource = { viewModel.reCheck(it.sourceUrl) },
                        onEditSource = { onEditSource(it.sourceUrl) },
                        onDebugSource = { onDebugSource(it.sourceUrl) }
                    )
                }
                is CheckSourceUIState.Paused -> {
                    PausedContent(
                        progress = state.progress,
                        statistics = state.statistics,
                        resultFilter = viewModel.resultFilter,
                        filteredResults = viewModel.filteredResults,
                        onResumeCheck = { viewModel.resumeCheck() },
                        onFilterChange = { viewModel.applyResultFilter(it) },
                        onResultClick = { detailResult = it },
                        onReCheckSource = { viewModel.reCheck(it.sourceUrl) },
                        onEditSource = { onEditSource(it.sourceUrl) },
                        onDebugSource = { onDebugSource(it.sourceUrl) }
                    )
                }
                is CheckSourceUIState.Completed -> {
                    CompletedContent(
                        statistics = state.statistics,
                        resultFilter = viewModel.resultFilter,
                        filteredResults = viewModel.filteredResults,
                        onStartCheck = { viewModel.startCheckSelectedSources() },
                        onReCheck = { viewModel.reCheck() },
                        onFilterChange = { viewModel.applyResultFilter(it) },
                        onResultClick = { detailResult = it },
                        onReCheckSource = { viewModel.reCheck(it.sourceUrl) },
                        onEditSource = { onEditSource(it.sourceUrl) },
                        onDebugSource = { onDebugSource(it.sourceUrl) }
                    )
                }
            }
        }
    }

    detailResult?.let { result ->
        ResultDetailDialog(
            result = result,
            onDismiss = { detailResult = null },
            onCopy = { context.sendToClip(result.toDetailText()) },
            onReCheck = {
                detailResult = null
                viewModel.reCheck(result.sourceUrl)
            },
            onEdit = {
                detailResult = null
                onEditSource(result.sourceUrl)
            },
            onDebug = {
                detailResult = null
                onDebugSource(result.sourceUrl)
            }
        )
    }

    if (showSourcePicker) {
        SourcePickerDialog(
            sources = viewModel.availableSources,
            selectedUrls = viewModel.selectedSourceUrls,
            onDismiss = { showSourcePicker = false },
            onToggleSource = { viewModel.toggleSourceSelection(it) },
            onSelectAll = { viewModel.selectAllSources() },
            onClear = { viewModel.clearSourceSelection() },
            onStart = {
                showSourcePicker = false
                viewModel.startCheckSelectedSources()
            }
        )
    }
}

@Composable
private fun ColumnScope.IdleContent(
    statistics: CheckStatistics,
    resultFilter: ResultFilter,
    filteredResults: List<CheckResult>,
    onStartCheck: () -> Unit,
    onSelectSources: () -> Unit,
    onFilterChange: (ResultFilter) -> Unit,
    onResultClick: (CheckResult) -> Unit,
    onReCheckSource: (CheckResult) -> Unit,
    onEditSource: (CheckResult) -> Unit,
    onDebugSource: (CheckResult) -> Unit
) {
    ProgressCard(
        checkState = CheckState.IDLE,
        progress = CheckProgress(),
        currentMessage = "",
        onStartCheck = onStartCheck,
        onPauseCheck = {},
        onResumeCheck = {},
        onSelectSources = onSelectSources,
        onReCheck = {}
    )

    if (statistics.totalCount > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        StatisticsCard(statistics = statistics)
    }

    if (filteredResults.isNotEmpty()) {
        ResultFilterChips(
            currentFilter = resultFilter,
            onFilterChange = onFilterChange
        )
    }

    ResultList(
        results = filteredResults,
        checkState = CheckState.IDLE,
        onResultClick = onResultClick,
        onReCheckSource = onReCheckSource,
        onEditSource = onEditSource,
        onDebugSource = onDebugSource,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun ColumnScope.CheckingContent(
    progress: CheckProgress,
    currentMessage: String,
    statistics: CheckStatistics,
    resultFilter: ResultFilter,
    filteredResults: List<CheckResult>,
    onPauseCheck: () -> Unit,
    onFilterChange: (ResultFilter) -> Unit,
    onResultClick: (CheckResult) -> Unit,
    onReCheckSource: (CheckResult) -> Unit,
    onEditSource: (CheckResult) -> Unit,
    onDebugSource: (CheckResult) -> Unit
) {
    ProgressCard(
        checkState = CheckState.CHECKING,
        progress = progress,
        currentMessage = currentMessage,
        onStartCheck = {},
        onPauseCheck = onPauseCheck,
        onResumeCheck = {},
        onSelectSources = {},
        onReCheck = {}
    )

    if (statistics.totalCount > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        StatisticsCard(statistics = statistics)
    }

    if (filteredResults.isNotEmpty()) {
        ResultFilterChips(
            currentFilter = resultFilter,
            onFilterChange = onFilterChange
        )
    }

    ResultList(
        results = filteredResults,
        checkState = CheckState.CHECKING,
        onResultClick = onResultClick,
        onReCheckSource = onReCheckSource,
        onEditSource = onEditSource,
        onDebugSource = onDebugSource,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun ColumnScope.PausedContent(
    progress: CheckProgress,
    statistics: CheckStatistics,
    resultFilter: ResultFilter,
    filteredResults: List<CheckResult>,
    onResumeCheck: () -> Unit,
    onFilterChange: (ResultFilter) -> Unit,
    onResultClick: (CheckResult) -> Unit,
    onReCheckSource: (CheckResult) -> Unit,
    onEditSource: (CheckResult) -> Unit,
    onDebugSource: (CheckResult) -> Unit
) {
    ProgressCard(
        checkState = CheckState.PAUSED,
        progress = progress,
        currentMessage = "",
        onStartCheck = {},
        onPauseCheck = {},
        onResumeCheck = onResumeCheck,
        onSelectSources = {},
        onReCheck = {}
    )

    if (statistics.totalCount > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        StatisticsCard(statistics = statistics)
    }

    if (filteredResults.isNotEmpty()) {
        ResultFilterChips(
            currentFilter = resultFilter,
            onFilterChange = onFilterChange
        )
    }

    ResultList(
        results = filteredResults,
        checkState = CheckState.PAUSED,
        onResultClick = onResultClick,
        onReCheckSource = onReCheckSource,
        onEditSource = onEditSource,
        onDebugSource = onDebugSource,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun ColumnScope.CompletedContent(
    statistics: CheckStatistics,
    resultFilter: ResultFilter,
    filteredResults: List<CheckResult>,
    onStartCheck: () -> Unit,
    onReCheck: () -> Unit,
    onFilterChange: (ResultFilter) -> Unit,
    onResultClick: (CheckResult) -> Unit,
    onReCheckSource: (CheckResult) -> Unit,
    onEditSource: (CheckResult) -> Unit,
    onDebugSource: (CheckResult) -> Unit
) {
    ProgressCard(
        checkState = CheckState.COMPLETED,
        progress = CheckProgress(),
        currentMessage = "",
        onStartCheck = onStartCheck,
        onPauseCheck = {},
        onResumeCheck = {},
        onSelectSources = {},
        onReCheck = onReCheck
    )

    if (statistics.totalCount > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        StatisticsCard(statistics = statistics)
    }

    if (filteredResults.isNotEmpty()) {
        ResultFilterChips(
            currentFilter = resultFilter,
            onFilterChange = onFilterChange
        )
    }

    ResultList(
        results = filteredResults,
        checkState = CheckState.COMPLETED,
        onResultClick = onResultClick,
        onReCheckSource = onReCheckSource,
        onEditSource = onEditSource,
        onDebugSource = onDebugSource,
        modifier = Modifier.weight(1f)
    )
}

/**
 * 顶部应用栏
 *
 * @param uiState 检测状态
 * @param onBackClick 返回按钮点击回调
 * @param onStopClick 停止按钮点击回调
 * @param onClearClick 清空按钮点击回调
 * @param onConfigClick 配置按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckSourceTopBar(
    uiState: CheckSourceUIState,
    onBackClick: () -> Unit,
    onStopClick: () -> Unit,
    onClearClick: () -> Unit,
    onConfigClick: () -> Unit
) {
    val containerColor = pageTopBarContainerColor()
    val (titleText, subtitleText) = when (uiState) {
        is CheckSourceUIState.Idle -> "准备就绪" to "准备就绪"
        is CheckSourceUIState.Checking -> "检测中..." to "检测中..."
        is CheckSourceUIState.Paused -> "已暂停" to "已暂停"
        is CheckSourceUIState.Completed -> "检测完成" to "检测完成"
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
            titleContentColor = MaterialTheme.colorScheme.onSecondary,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondary
        ),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.check_book_source),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelMedium,
                    color = pageSecondaryTextColor()
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = {
            IconButton(onClick = onConfigClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "配置"
                )
            }
            if (uiState is CheckSourceUIState.Checking) {
                IconButton(onClick = onStopClick) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.cancel)
                    )
                }
            }
            if (uiState is CheckSourceUIState.Completed) {
                IconButton(onClick = onClearClick) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
            }
        }
    )
}

/**
 * 进度卡片
 *
 * @param checkState 检测状态
 * @param progress 检测进度
 * @param currentMessage 当前消息
 * @param onStartCheck 开始检测回调
 * @param onPauseCheck 暂停检测回调
 * @param onResumeCheck 恢复检测回调
 * @param onSelectSources 选择书源回调
 * @param onReCheck 重新检测回调
 */
@Composable
fun ProgressCard(
    checkState: CheckState,
    progress: CheckProgress,
    currentMessage: String,
    onStartCheck: () -> Unit,
    onPauseCheck: () -> Unit,
    onResumeCheck: () -> Unit,
    onSelectSources: () -> Unit,
    onReCheck: () -> Unit
) {
    val containerColor = checkSourceCardContainerColor()
    val border = checkSourceCardBorder()
    val isDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.18f
    val outlinedButtonBorderColor = if (isDarkBackground) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
    }
    val outlinedButtonColors = ButtonDefaults.outlinedButtonColors(
        containerColor = if (isDarkBackground) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        } else {
            Color.Transparent
        },
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        border = border,
        shadowElevation = if (isDarkBackground) 0.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusIcon(
                            checkState = checkState,
                            modifier = Modifier.size(32.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = when (checkState) {
                                CheckState.IDLE -> "准备就绪"
                                CheckState.CHECKING -> "检测中..."
                                CheckState.PAUSED -> "已暂停"
                                CheckState.COMPLETED -> "检测完成"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (checkState == CheckState.CHECKING || checkState == CheckState.PAUSED) {
                        Text(
                            text = progress.progressText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (checkState == CheckState.CHECKING || checkState == CheckState.PAUSED) {
                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedProgressIndicator(
                        progress = progress.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (progress.currentSourceName.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Source,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = pageMutedIconTint()
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = progress.currentSourceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = pageSecondaryTextColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentMessage.isNotEmpty()) {
                        Text(
                            text = currentMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = pageSecondaryTextColor(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (checkState) {
                        CheckState.IDLE -> {
                            OutlinedButton(
                                onClick = onSelectSources,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, outlinedButtonBorderColor),
                                colors = outlinedButtonColors
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("选择书源")
                            }

                            Button(
                                onClick = onStartCheck,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("开始检测")
                            }
                        }
                        CheckState.CHECKING -> {
                            OutlinedButton(
                                onClick = onPauseCheck,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, outlinedButtonBorderColor),
                                colors = outlinedButtonColors
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("暂停")
                            }
                        }
                        CheckState.PAUSED -> {
                            Button(
                                onClick = onResumeCheck,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("继续")
                            }
                        }
                        CheckState.COMPLETED -> {
                            OutlinedButton(
                                onClick = onReCheck,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, outlinedButtonBorderColor),
                                colors = outlinedButtonColors
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("重新检测")
                            }

                            Button(
                                onClick = onStartCheck,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("新检测")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 状态图标
 *
 * @param checkState 检测状态
 * @param modifier 修饰符
 */
@Composable
fun StatusIcon(
    checkState: CheckState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val (icon, tint) = when (checkState) {
        CheckState.IDLE -> Icons.Default.CheckCircle to pageAccentColor()
        CheckState.CHECKING -> Icons.Default.Sync to pageAccentColor()
        CheckState.PAUSED -> Icons.Default.PauseCircle to MaterialTheme.colorScheme.secondary
        CheckState.COMPLETED -> Icons.Default.CheckCircle to pageAccentColor()
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tint.copy(alpha = 0.15f)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(20.dp)
                    .then(
                        if (checkState == CheckState.CHECKING) {
                            Modifier.graphicsLayer { rotationZ = rotation }
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

/**
 * 动画进度指示器
 *
 * @param progress 进度值 (0-1)
 * @param modifier 修饰符
 */
@Composable
fun AnimatedProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "progress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = pageSurfaceVariantColor()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(
                        Brush.horizontalGradient(
                            listOf(primaryColor, secondaryColor)
                        )
                    )
            )
        }
    }
}

/**
 * 统计信息卡片
 *
 * @param statistics 统计数据
 */
@Composable
fun StatisticsCard(
    statistics: CheckStatistics
) {
    val containerColor = checkSourceCardContainerColor()
    val border = checkSourceCardBorder()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = border,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatisticItem(
                icon = Icons.Default.Assignment,
                label = "总数",
                value = statistics.totalCount.toString(),
                color = MaterialTheme.colorScheme.primary
            )

            StatisticItem(
                icon = Icons.Default.CheckCircle,
                label = "成功",
                value = statistics.successCount.toString(),
                color = pageAccentColor()
            )

            StatisticItem(
                icon = Icons.Default.Error,
                label = "失败",
                value = statistics.failedCount.toString(),
                color = MaterialTheme.colorScheme.error
            )

            StatisticItem(
                icon = Icons.Default.TrendingUp,
                label = "成功率",
                value = statistics.successRateText,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

/**
 * 统计项
 *
 * @param icon 图标
 * @param label 标签
 * @param value 值
 * @param color 颜色
 */
@Composable
fun StatisticItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = pageSecondaryTextColor()
        )
    }
}

/**
 * 结果过滤器芯片
 *
 * @param currentFilter 当前过滤器
 * @param onFilterChange 过滤器改变回调
 */
@Composable
fun ResultFilterChips(
    currentFilter: ResultFilter,
    onFilterChange: (ResultFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResultFilter.entries.forEach { filter ->
            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        when (filter) {
                            ResultFilter.ALL -> "全部"
                            ResultFilter.SUCCESS -> "成功"
                            ResultFilter.FAILED -> "失败"
                        }
                    )
                },
                leadingIcon = {
                    if (currentFilter == filter) {
                        Icon(
                            imageVector = when (filter) {
                                ResultFilter.ALL -> Icons.Default.List
                                ResultFilter.SUCCESS -> Icons.Default.Check
                                ResultFilter.FAILED -> Icons.Default.Close
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }
    }
}

/**
 * 结果列表
 *
 * @param results 结果列表
 * @param checkState 检测状态
 * @param modifier 修饰符
 */
@Composable
fun ResultList(
    results: List<CheckResult>,
    checkState: CheckState,
    onResultClick: (CheckResult) -> Unit,
    onReCheckSource: (CheckResult) -> Unit,
    onEditSource: (CheckResult) -> Unit,
    onDebugSource: (CheckResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = checkSourceCardContainerColor()

    AnimatedVisibility(
        visible = results.isNotEmpty(),
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = results,
                key = { it.sourceUrl }
            ) { result ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInHorizontally(),
                    exit = fadeOut() + slideOutHorizontally()
                ) {
                    ResultItem(
                        result = result,
                        containerColor = containerColor,
                        onClick = { onResultClick(result) },
                        onReCheck = { onReCheckSource(result) },
                        onEdit = { onEditSource(result) },
                        onDebug = { onDebugSource(result) }
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = results.isEmpty() && checkState != CheckState.CHECKING,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            EmptyStateView()
        }
    }
}

/**
 * 空状态视图
 */
@Composable
fun EmptyStateView() {
    val isDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.18f
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = if (isDarkBackground) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            },
            modifier = Modifier.size(80.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Source,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (isDarkBackground) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "暂无检测结果",
            style = MaterialTheme.typography.titleMedium,
            color = pageSecondaryTextColor(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "点击\"开始检测\"按钮开始检测书源",
            style = MaterialTheme.typography.bodyMedium,
            color = pageSecondaryTextColor().copy(alpha = 0.82f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 结果项
 *
 * @param result 检测结果
 * @param containerColor 容器颜色
 */
@Composable
fun ResultItem(
    result: CheckResult,
    containerColor: Color,
    onClick: () -> Unit,
    onReCheck: () -> Unit,
    onEdit: () -> Unit,
    onDebug: () -> Unit
) {
    val successColor = pageAccentColor()
    val errorColor = MaterialTheme.colorScheme.error
    val border = checkSourceCardBorder()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = border,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (result.isSuccess) successColor.copy(alpha = 0.15f)
                        else errorColor.copy(alpha = 0.15f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (result.isSuccess) Icons.Default.Check
                                     else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (result.isSuccess) successColor else errorColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = result.sourceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = result.getStatusText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.isSuccess) successColor else errorColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = pageSurfaceVariantColor()
                    ) {
                        Text(
                            text = result.getRespondTimeText(),
                            style = MaterialTheme.typography.labelMedium,
                            color = pageSecondaryTextColor(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onReCheck,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新检测",
                            modifier = Modifier.size(18.dp),
                            tint = pageMutedIconTint()
                        )
                    }
                }

                if (result.errorType != ErrorType.NONE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = getErrorTypeText(result.errorType),
                        style = MaterialTheme.typography.labelSmall,
                        color = errorColor.copy(alpha = 0.8f)
                    )
                }

                Row {
                    IconButton(
                        onClick = onDebug,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "调试",
                            modifier = Modifier.size(18.dp),
                            tint = pageMutedIconTint()
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(18.dp),
                            tint = pageMutedIconTint()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResultDetailDialog(
    result: CheckResult,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onReCheck: () -> Unit,
    onEdit: () -> Unit,
    onDebug: () -> Unit
) {
    val dialogContainerColor = checkSourceCardContainerColor()
    val dialogTextColor = MaterialTheme.colorScheme.onSurface
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        titleContentColor = dialogTextColor,
        textContentColor = dialogTextColor,
        title = {
            Text(
                text = result.sourceName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                DetailLine(label = "状态", value = result.getStatusText())
                DetailLine(label = "响应", value = result.getRespondTimeText())
                if (result.errorType != ErrorType.NONE) {
                    DetailLine(label = "类型", value = getErrorTypeText(result.errorType))
                }
                DetailLine(label = "地址", value = result.sourceUrl)
            }
        },
        confirmButton = {
            TextButton(onClick = onReCheck) {
                Text("重检")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) {
                    Text("复制")
                }
                TextButton(onClick = onDebug) {
                    Text("调试")
                }
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

@Composable
fun SourcePickerDialog(
    sources: List<BookSource>,
    selectedUrls: Set<String>,
    onDismiss: () -> Unit,
    onToggleSource: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onStart: () -> Unit
) {
    val sourceDialogContainerColor = checkSourceCardContainerColor()
    val sourceDialogTextColor = MaterialTheme.colorScheme.onSurface
    val sourceDialogSecondaryTextColor = pageSecondaryTextColor()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sourceDialogContainerColor,
        titleContentColor = sourceDialogTextColor,
        textContentColor = sourceDialogTextColor,
        title = {
            Text("选择书源")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选 ${selectedUrls.size} / ${sources.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = sourceDialogSecondaryTextColor
                    )
                    Row {
                        TextButton(onClick = onSelectAll) {
                            Text("全选")
                        }
                        TextButton(onClick = onClear) {
                            Text("清空")
                        }
                    }
                }

                LazyColumn {
                    items(
                        items = sources,
                        key = { it.bookSourceUrl }
                    ) { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSource(source.bookSourceUrl) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedUrls.contains(source.bookSourceUrl),
                                onCheckedChange = { onToggleSource(source.bookSourceUrl) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = pageAccentColor(),
                                    uncheckedColor = sourceDialogSecondaryTextColor,
                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.bookSourceName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = sourceDialogTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = source.bookSourceUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sourceDialogSecondaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onStart,
                enabled = selectedUrls.isNotEmpty()
            ) {
                Text("检测")
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
private fun DetailLine(label: String, value: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = pageSecondaryTextColor()
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun CheckResult.toDetailText(): String {
    return buildString {
        appendLine(sourceName)
        appendLine(sourceUrl)
        appendLine(getStatusText())
        appendLine(getRespondTimeText())
        if (errorType != ErrorType.NONE) {
            appendLine(errorType.name)
        }
    }
}

/**
 * 获取错误类型文本
 *
 * @param errorType 错误类型
 * @return 文本描述
 */
@Composable
fun getErrorTypeText(errorType: ErrorType): String {
    return when (errorType) {
        ErrorType.NONE -> ""
        ErrorType.TIMEOUT -> "超时"
        ErrorType.NETWORK_ERROR -> "网络错误"
        ErrorType.PARSE_ERROR -> "解析错误"
        ErrorType.SCRIPT_ERROR -> "脚本错误"
        ErrorType.DOMAIN_ERROR -> "域名错误"
        ErrorType.SEARCH_ERROR -> "搜索错误"
        ErrorType.DISCOVERY_ERROR -> "发现错误"
        ErrorType.INFO_ERROR -> "详情错误"
        ErrorType.TOC_ERROR -> "目录错误"
        ErrorType.CONTENT_ERROR -> "正文错误"
    }
}

/**
 * 计算卡片背景色
 * 根据主题亮度自动调整透明度
 */
@Composable
fun checkSourceCardContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    return if (background.luminance() < 0.18f) {
        lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
            0.72f
        )
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    }
}

@Composable
fun checkSourceCardBorder(): BorderStroke? {
    val background = MaterialTheme.colorScheme.background
    return if (background.luminance() < 0.18f) {
        BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
        )
    } else {
        null
    }
}
