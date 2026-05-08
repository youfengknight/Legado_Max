/**
 * URL访问记录界面 - Jetpack Compose实现
 * 
 * 这个文件展示了如何使用Compose构建一个完整的列表界面
 * 包含：搜索、筛选、菜单、对话框等常见功能
 * 
 * Compose核心概念：
 * 1. @Composable - 标记一个函数是可组合函数（UI组件）
 * 2. State - 状态，当状态变化时UI自动更新
 * 3. Modifier - 修饰符，用于设置组件的大小、位置、样式等
 * 4. Material3 - Google的UI设计系统，提供现成的组件
 */
package io.legado.app.ui.urlRecord

// ==================== 导入部分 ====================
// 动画相关
import androidx.compose.animation.AnimatedVisibility

// 基础布局和交互
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn      // 类似RecyclerView的高效列表
import androidx.compose.foundation.lazy.items          // 列表项渲染
import androidx.compose.foundation.shape.RoundedCornerShape

// 图标
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

// Material3 UI组件库
import androidx.compose.material3.*

// Compose核心
import androidx.compose.runtime.*

// UI相关工具
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 协程和ViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel

// Android Context和Toast
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

// 数据模型
import io.legado.app.data.entities.UrlRecord
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageTopBarContainerColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * URL记录主界面
 * 
 * @Composable 注解表示这是一个可组合函数
 * 可组合函数是Compose的基本构建块，类似于View系统中的View
 * 
 * @param viewModel ViewModel实例，通过viewModel()自动创建
 * @param onBackClick 返回按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)  // 使用实验性API
@Composable
fun UrlRecordScreen(
    viewModel: UrlRecordViewModel = viewModel(),  // 自动创建ViewModel
    onBackClick: () -> Unit
) {
    // ==================== 状态管理 ====================
    // collectAsState() 将StateFlow转换为Compose的State
    // 当Flow中的值变化时，UI会自动重新绘制（这叫"重组"）
    // "by" 是属性委托，让我们可以直接用变量名访问值，而不是state.value
    
    val uiState by viewModel.uiState.collectAsState()           // UI状态
    val domains by viewModel.domains.collectAsState()           // 域名列表
    val recordCount by viewModel.recordCount.collectAsState()   // 记录数量
    val isRecordEnabled by viewModel.isRecordEnabled.collectAsState()  // 开关状态
    
    // ==================== 本地UI状态 ====================
    // remember 保存状态，在重组时不会丢失
    // mutableStateOf 创建一个可变状态，变化时会触发重组
    
    var showSearch by remember { mutableStateOf(false) }        // 是否显示搜索框
    var searchQuery by remember { mutableStateOf("") }          // 搜索关键词
    var showDomainFilter by remember { mutableStateOf(false) }  // 是否显示域名筛选
    var showClearDialog by remember { mutableStateOf<Int?>(null) } // 显示清除对话框（null不显示）
    var showMenu by remember { mutableStateOf(false) }          // 是否显示菜单
    
    // ==================== 其他配置 ====================
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val containerColor = pageCardContainerColor()
    val topBarColor = pageTopBarContainerColor()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // ==================== 副作用 ====================
    // LaunchedEffect 当key变化时执行副作用
    // 这里当searchQuery或currentDomain变化时，更新ViewModel
    LaunchedEffect(searchQuery, viewModel.currentDomain) {
        viewModel.setSearchQuery(searchQuery.ifBlank { null })
    }

    // ==================== 清除确认对话框 ====================
    // 条件渲染：只有showClearDialog不为null时才显示
    if (showClearDialog != null) {
        val days = showClearDialog!!
        AlertDialog(
            onDismissRequest = { showClearDialog = null },  // 点击外部关闭
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (days == 0) "清除所有记录" else "清除${days}天前的记录") },
            text = {
                Text(if (days == 0) "确定要清除所有URL访问记录吗？" else "确定要清除${days}天前的记录吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 在协程中调用suspend函数
                        coroutineScope.launch {
                            if (days == 0) {
                                viewModel.clearAll()
                            } else {
                                viewModel.deleteOldRecords(days)
                            }
                        }
                        showClearDialog = null
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // ==================== 页面骨架 ====================
    // Scaffold 是Material3的基础页面模板
    // 它提供了topBar、bottomBar、floatingActionButton等预定义区域
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // ==================== 标题栏 ====================
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                title = {
                    // Column 是垂直布局，子元素从上到下排列
                    Column {
                        Text(
                            text = "URL访问记录",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        )
                        // 条件显示：只有recordCount > 0时才显示
                        if (recordCount > 0) {
                            Text(
                                text = "共 $recordCount 条记录",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                // 导航图标（返回按钮）
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                // 右侧操作按钮
                actions = {
                    // 搜索按钮
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    // 更多菜单
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        // 下拉菜单
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            // 域名筛选菜单项
                            DropdownMenuItem(
                                text = { Text("按域名筛选") },
                                onClick = {
                                    showDomainFilter = !showDomainFilter
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FilterList, contentDescription = null)
                                }
                            )
                            // URL记录开关菜单项
                            DropdownMenuItem(
                                text = {
                                    // Row 是水平布局，子元素从左到右排列
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("开启URL记录")
                                        Spacer(Modifier.weight(1f))  // 占满剩余空间
                                        if (isRecordEnabled) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    val newEnabled = !isRecordEnabled
                                    viewModel.setRecordUrl(newEnabled)
                                    // 显示Toast提示
                                    Toast.makeText(
                                        context,
                                        if (newEnabled) "已开启URL记录" else "已关闭URL记录",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isRecordEnabled) Icons.Default.ToggleOn 
                                        else Icons.Default.ToggleOff,
                                        contentDescription = null
                                    )
                                }
                            )
                            HorizontalDivider()  // 分割线
                            // 清除记录菜单项
                            DropdownMenuItem(
                                text = { Text("清除7天前的记录") },
                                onClick = {
                                    showClearDialog = 7
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清除30天前的记录") },
                                onClick = {
                                    showClearDialog = 30
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text("清除所有记录", color = MaterialTheme.colorScheme.error) 
                                },
                                onClick = {
                                    showClearDialog = 0
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteForever, 
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        // paddingValues 是Scaffold计算出的内边距，避免内容被标题栏遮挡
        
        // ==================== 主内容区域 ====================
        Column(
            modifier = Modifier
                .fillMaxSize()           // 填满父容器
                .padding(paddingValues)  // 应用Scaffold的内边距
        ) {
            // ==================== 搜索框（可动画显示/隐藏）====================
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },  // 输入时更新状态
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索URL/域名/来源") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = containerColor,
                        unfocusedContainerColor = containerColor,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true  // 单行输入
                )
            }

            // ==================== 域名筛选列表 ====================
            AnimatedVisibility(visible = showDomainFilter && domains.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    // LazyColumn 类似 RecyclerView，只渲染可见项，高效
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // 单个静态项
                        item {
                            DomainFilterItem(
                                domain = "全部",
                                isSelected = viewModel.currentDomain == null,
                                onClick = {
                                    viewModel.filterByDomain(null)
                                    showDomainFilter = false
                                }
                            )
                        }
                        // 动态列表项
                        items(domains) { domain ->
                            DomainFilterItem(
                                domain = domain,
                                isSelected = viewModel.currentDomain == domain,
                                onClick = {
                                    viewModel.filterByDomain(domain)
                                    showDomainFilter = false
                                }
                            )
                        }
                    }
                }
            }

            // ==================== 根据UI状态显示不同内容 ====================
            // when 表达式根据状态显示不同UI
            when (val state = uiState) {
                // 加载中状态
                is UrlRecordUIState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()  // 加载动画
                    }
                }
                // 空数据状态
                is UrlRecordUIState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))  // 垂直间距
                            Text(
                                text = "暂无URL访问记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "开启URL记录后，所有网络请求都会被记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // 错误状态
                is UrlRecordUIState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
                        }
                    }
                }
                // 成功状态 - 显示列表
                is UrlRecordUIState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        // key 参数用于唯一标识列表项，提高性能
                        items(state.records, key = { it.id }) { record ->
                            UrlRecordItem(record = record)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 域名筛选项组件
 * 
 * 这是一个自定义的可组合函数
 * 可以在其他地方复用
 * 
 * @param domain 域名文本
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun DomainFilterItem(
    domain: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)  // 添加点击效果
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = domain,
            style = MaterialTheme.typography.bodyMedium,
            // 根据选中状态设置不同颜色
            color = if (isSelected) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)  // 占满剩余空间
        )
        // 条件显示：选中时显示勾选图标
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * URL记录列表项组件
 * 
 * 展示单条URL记录的详细信息
 * 
 * @param record URL记录数据
 */
@Composable
private fun UrlRecordItem(record: UrlRecord) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 第一行：方法 + 状态码 + 耗时 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MethodBadge(method = record.method)
                Spacer(modifier = Modifier.width(8.dp))  // 水平间距
                StatusBadge(
                    responseCode = record.responseCode,
                    errorMsg = record.errorMsg
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${record.duration}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))  // 占满剩余空间
                Text(
                    text = dateFormat.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 第二行：域名
            Text(
                text = record.domain,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF009688)  // 青色
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 第三行：URL
            Text(
                text = record.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,  // 最多2行
                overflow = TextOverflow.Ellipsis  // 超出显示省略号
            )

            // 第四行：来源标签（可选）
            record.sourceName?.let { source ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * HTTP方法标签组件
 * 
 * 显示GET/POST等HTTP方法，不同方法用不同颜色
 * 
 * @param method HTTP方法名
 */
@Composable
private fun MethodBadge(method: String) {
    val isPost = method.equals("POST", ignoreCase = true)
    Surface(
        color = if (isPost) Color(0xFF9C27B0).copy(alpha = 0.15f)  // 紫色
                else Color(0xFF2196F3).copy(alpha = 0.15f),        // 蓝色
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = method.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isPost) Color(0xFF9C27B0) else Color(0xFF2196F3),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * HTTP状态码标签组件
 * 
 * 根据响应码显示不同颜色：
 * - 2xx: 绿色（成功）
 * - 其他: 橙色（异常）
 * - 错误: 红色
 * 
 * @param responseCode HTTP响应码
 * @param errorMsg 错误信息（可选）
 */
@Composable
private fun StatusBadge(responseCode: Int, errorMsg: String?) {
    // Kotlin的解构声明，同时获取颜色和文本
    val (color, text) = when {
        errorMsg != null -> Color(0xFFF44336) to "错误"           // 红色
        responseCode in 200..299 -> Color(0xFF4CAF50) to "$responseCode"  // 绿色
        else -> Color(0xFFFF9800) to "$responseCode"              // 橙色
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = color
    )
}
