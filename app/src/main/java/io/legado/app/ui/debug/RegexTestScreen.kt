/**
 * 正则表达式测试工具界面 - Jetpack Compose实现
 * 
 * 功能说明：
 * 测试正则表达式匹配效果，支持：
 * - 输入正则表达式模式
 * - 输入待匹配文本
 * - 设置匹配选项（忽略大小写、多行模式、点匹配换行）
 * - 实时预览：输入时自动执行匹配
 * - 替换预览：显示替换后的文本效果
 * - 显示匹配结果（完整匹配、分组信息、位置详情）
 * - 高亮显示匹配内容
 * - 状态提示（成功/错误）
 * 
 * 界面结构：
 * - 正则表达式输入区（含选项复选框）
 * - 替换文本输入区
 * - 待匹配文本输入区
 * - 操作按钮（测试、清空）
 * - 状态提示区
 * - 匹配结果显示区
 * - 高亮显示区
 * - 替换预览区
 */
package io.legado.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import java.util.regex.PatternSyntaxException

private data class MatchResultData(
    val start: Int,
    val end: Int,
    val value: String,
    val groups: List<String> = emptyList()
)

private data class TestResult(
    val success: Boolean,
    val message: String,
    val matchCount: Int = 0,
    val matches: List<MatchResultData> = emptyList(),
    val highlightedText: AnnotatedString? = null,
    val replacedText: String? = null,
    val matchInfo: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexTestScreen(
    onBackClick: () -> Unit,
    initialPattern: String = "",
    initialReplacement: String = "",
    initialIsRegex: Boolean = true
) {
    val context = LocalContext.current
    val containerColor = debugToolsCardContainerColor()
    val topBarColor = debugToolsTopBarContainerColor()
    
    var pattern by remember { mutableStateOf(initialPattern) }
    var input by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf(initialReplacement) }
    
    var ignoreCase by remember { mutableStateOf(false) }
    var multiline by remember { mutableStateOf(false) }
    var dotAll by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(initialIsRegex) }
    var realtimePreview by remember { mutableStateOf(true) }
    
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    
    fun performTest(): TestResult {
        if (input.isEmpty()) {
            return TestResult(
                success = false,
                message = context.getString(R.string.debug_input_hint)
            )
        }
        
        if (pattern.isEmpty()) {
            return TestResult(
                success = false,
                message = context.getString(R.string.pattern_empty)
            )
        }
        
        if (useRegex) {
            try {
                val regexOptions = mutableSetOf<RegexOption>()
                if (ignoreCase) regexOptions.add(RegexOption.IGNORE_CASE)
                if (multiline) regexOptions.add(RegexOption.MULTILINE)
                if (dotAll) regexOptions.add(RegexOption.DOT_MATCHES_ALL)
                
                kotlin.text.Regex(pattern, regexOptions)
            } catch (e: PatternSyntaxException) {
                return TestResult(
                    success = false,
                    message = context.getString(R.string.regex_syntax_error, e.localizedMessage)
                )
            }
        }
        
        try {
            val matches = findMatches(input, pattern, useRegex, ignoreCase, multiline, dotAll)
            
            if (matches.isEmpty()) {
                return TestResult(
                    success = false,
                    message = context.getString(R.string.no_match_found),
                    matchCount = 0
                )
            }
            
            val highlightColor = Color(0x40FFEB3B)
            val highlightedText = buildAnnotatedString {
                var lastIndex = 0
                val sortedMatches = matches.sortedBy { it.start }
                
                for (match in sortedMatches) {
                    if (match.start > lastIndex) {
                        append(input.substring(lastIndex, match.start))
                    }
                    withStyle(style = SpanStyle(background = highlightColor)) {
                        append(match.value)
                    }
                    lastIndex = match.end
                }
                
                if (lastIndex < input.length) {
                    append(input.substring(lastIndex))
                }
            }
            
            val replacedText = if (replacement.isNotEmpty()) {
                if (useRegex) {
                    val regexOptions = mutableSetOf<RegexOption>()
                    if (ignoreCase) regexOptions.add(RegexOption.IGNORE_CASE)
                    if (multiline) regexOptions.add(RegexOption.MULTILINE)
                    if (dotAll) regexOptions.add(RegexOption.DOT_MATCHES_ALL)
                    input.replace(kotlin.text.Regex(pattern, regexOptions), replacement)
                } else {
                    input.replace(pattern, replacement)
                }
            } else null
            
            val infoBuilder = StringBuilder()
            infoBuilder.append(context.getString(R.string.match_times, matches.size))
            matches.take(10).forEachIndexed { index, match ->
                infoBuilder.append("\n").append(
                    context.getString(R.string.match_position, index + 1, match.start, match.end)
                )
            }
            if (matches.size > 10) {
                infoBuilder.append("\n...").append(
                    context.getString(R.string.more_matches, matches.size - 10)
                )
            }
            
            return TestResult(
                success = true,
                message = context.getString(R.string.regex_valid_match_success),
                matchCount = matches.size,
                matches = matches,
                highlightedText = highlightedText,
                replacedText = replacedText,
                matchInfo = infoBuilder.toString()
            )
            
        } catch (e: Exception) {
            return TestResult(
                success = false,
                message = e.message ?: context.getString(R.string.no_match_found)
            )
        }
    }
    
    LaunchedEffect(pattern, input, replacement, ignoreCase, multiline, dotAll, useRegex, realtimePreview) {
        if (realtimePreview && pattern.isNotEmpty() && input.isNotEmpty()) {
            testResult = performTest()
        }
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Text(
                        text = stringResource(R.string.debug_regex_test),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.debug_regex_pattern),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.realtime_preview),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = realtimePreview,
                                onCheckedChange = { realtimePreview = it },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.debug_regex_pattern_hint)) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = useRegex,
                                onCheckedChange = { useRegex = it }
                            )
                            Text(
                                text = stringResource(R.string.use_regex),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    if (useRegex) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ignoreCase,
                                    onCheckedChange = { ignoreCase = it }
                                )
                                Text(
                                    text = stringResource(R.string.debug_regex_ignore_case),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = multiline,
                                    onCheckedChange = { multiline = it }
                                )
                                Text(
                                    text = stringResource(R.string.debug_regex_multiline),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = dotAll,
                                    onCheckedChange = { dotAll = it }
                                )
                                Text(
                                    text = stringResource(R.string.debug_regex_dot_all),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.replace_to),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.replace_to)) },
                        singleLine = true
                    )
                }
            }

            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_input),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        placeholder = { Text(stringResource(R.string.debug_input_hint)) }
                    )
                }
            }

            if (!realtimePreview) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (pattern.isEmpty()) {
                                context.toastOnUi(R.string.debug_pattern_empty)
                                return@Button
                            }
                            if (input.isEmpty()) {
                                context.toastOnUi(R.string.input_is_empty)
                                return@Button
                            }
                            testResult = performTest()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.debug_test))
                    }
                    
                    OutlinedButton(
                        onClick = {
                            pattern = ""
                            input = ""
                            replacement = ""
                            testResult = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.clear))
                    }
                }
            }

            testResult?.let { result ->
                StatusCard(
                    success = result.success,
                    message = result.message,
                    matchCount = result.matchCount,
                    containerColor = containerColor
                )
                
                if (result.success && result.matchInfo != null) {
                    MatchInfoCard(
                        matchInfo = result.matchInfo,
                        containerColor = containerColor,
                        onCopy = { context.sendToClip(result.matchInfo) }
                    )
                }
                
                result.highlightedText?.let { highlighted ->
                    HighlightCard(
                        highlightedText = highlighted,
                        containerColor = containerColor
                    )
                }
                
                result.replacedText?.let { replaced ->
                    ReplacePreviewCard(
                        replacedText = replaced,
                        containerColor = containerColor,
                        onCopy = { context.sendToClip(replaced) }
                    )
                }
            }
        }
    }
}

private fun findMatches(
    input: String,
    pattern: String,
    useRegex: Boolean,
    ignoreCase: Boolean,
    multiline: Boolean,
    dotAll: Boolean
): List<MatchResultData> {
    val results = mutableListOf<MatchResultData>()
    
    if (useRegex) {
        val regexOptions = mutableSetOf<RegexOption>()
        if (ignoreCase) regexOptions.add(RegexOption.IGNORE_CASE)
        if (multiline) regexOptions.add(RegexOption.MULTILINE)
        if (dotAll) regexOptions.add(RegexOption.DOT_MATCHES_ALL)
        
        val regex = kotlin.text.Regex(pattern, regexOptions)
        regex.findAll(input).forEach { matchResult ->
            results.add(
                MatchResultData(
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1,
                    value = matchResult.value,
                    groups = matchResult.groupValues.drop(1)
                )
            )
        }
    } else {
        var startIndex = 0
        while (true) {
            val index = input.indexOf(pattern, startIndex)
            if (index == -1) break
            results.add(MatchResultData(index, index + pattern.length, pattern))
            startIndex = index + pattern.length
        }
    }
    
    return results
}

@Composable
private fun StatusCard(
    success: Boolean,
    message: String,
    matchCount: Int,
    containerColor: Color
) {
    val backgroundColor = if (success) {
        Color(0xFF4CAF50).copy(alpha = 0.15f)
    } else {
        Color(0xFFF44336).copy(alpha = 0.15f)
    }
    val iconColor = if (success) Color(0xFF4CAF50) else Color(0xFFF44336)
    val icon: ImageVector = if (success) Icons.Default.Check else Icons.Default.Error
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = iconColor,
                    fontWeight = FontWeight.Medium
                )
                if (success && matchCount > 0) {
                    Text(
                        text = stringResource(R.string.match_count_format, matchCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = iconColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchInfoCard(
    matchInfo: String,
    containerColor: Color,
    onCopy: () -> Unit
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "匹配详情",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = matchInfo,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HighlightCard(
    highlightedText: AnnotatedString,
    containerColor: Color
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.debug_highlight),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SelectionContainer {
                Text(
                    text = highlightedText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ReplacePreviewCard(
    replacedText: String,
    containerColor: Color,
    onCopy: () -> Unit
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.replace_preview),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SelectionContainer {
                Text(
                    text = replacedText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
