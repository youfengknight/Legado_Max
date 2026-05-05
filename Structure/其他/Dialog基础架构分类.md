# Legado Plus 项目 Dialog 分类文档

这个项目中的 Dialog 可以按**基础架构**和**功能用途**两大维度进行分类。

---

## 一、Dialog 基础架构分类（5种）

| 类型 | 基类 | 用途 |
|:---|:---|:---|
| BaseDialogFragment | DialogFragment | 项目自定义的对话框基类，支持软键盘适配、EInk模式、主题适配 |
| BasePrefDialogFragment | DialogFragment | 偏好设置对话框基类 |
| Dialog | android.app.Dialog | 原生对话框，用于简单弹窗 |
| AlertDialog | androidx.appcompat.app.AlertDialog | 标准警告对话框，通过 `alert{}` DSL 封装 |
| ProgressDialog | android.app.ProgressDialog | 进度对话框，通过 `progressDialog{}` DSL 封装 |

---

## 二、具体 Dialog 功能分类（80+个）

### 1. 阅读配置类（13个）

- ReadAloudDialog - 朗读配置
- ReadAloudConfigDialog - 朗读引擎配置
- ReadStyleDialog - 阅读样式
- MoreConfigDialog - 更多配置
- AutoReadDialog - 自动阅读
- BgTextConfigDialog - 背景文字配置
- PaddingConfigDialog - 边距配置
- ClickActionConfigDialog - 点击动作配置
- TipConfigDialog - 提示配置
- PageKeyDialog - 翻页键配置
- SpeakEngineDialog - 朗读引擎
- HttpTtsEditDialog - HTTP TTS 编辑
- ContentEditDialog - 内容编辑

### 2. 书源管理类（8个）

- ImportBookSourceDialog - 导入书源
- ChangeBookSourceDialog - 换书源
- ChangeChapterSourceDialog - 换章节源
- SourceJsonEditDialog - 书源 JSON 编辑
- GroupManageDialog - 分组管理
- GroupEditDialog - 分组编辑
- GroupSelectDialog - 分组选择
- SourcePickerDialog - 书源选择

### 3. 搜索与帮助类（2个）

- HelpSearchDialog - 帮助文档搜索
- SearchScopeDialog - 搜索范围

### 4. 文本查看类（5个）

- TextDialog - 文本弹窗（支持 Markdown/HTML/纯文本）
- TextListDialog - 文本列表
- CodeDialog - 代码编辑查看
- AppLogDialog - 应用日志
- CrashLogsDialog - 崩溃日志

### 5. 导入导出类（7个）

- ImportRssSourceDialog - 导入 RSS 源
- ImportReplaceRuleDialog - 导入替换规则
- ImportDictRuleDialog - 导入词典规则
- ImportThemeDialog - 导入主题
- ImportHttpTtsDialog - 导入 HTTP TTS
- ImportTxtTocRuleDialog - 导入 TXT 目录规则
- AddToBookshelfDialog - 添加到书架

### 6. 配置设置类（9个）

- ThemeListDialog - 主题列表
- ServerConfigDialog - 服务器配置
- ServersDialog - 服务器列表
- FontSelectDialog - 字体选择
- FilePickerDialog - 文件选择
- CoverRuleConfigDialog - 封面规则配置
- CoverHtmlCodeDialog - 封面 HTML 代码
- CoverHtmlTemplateListDialog - 封面模板列表
- NumberPickerDialog - 数字选择器

### 7. 字典词典类（3个）

- DictDialog - 词典查询
- DictRuleEditDialog - 词典规则编辑
- DictRuleDebugDialog - 词典规则调试

### 8. 漫画相关类（3个）

- MangaFooterSettingDialog - 漫画底部设置
- MangaEpaperDialog - 漫画电子纸模式
- MangaColorFilterDialog - 漫画色彩滤镜

### 9. RSS 相关类（4个）

- ReadRecordDialog - 阅读记录
- RssFavoritesDialog - RSS 收藏
- GroupManageDialog (RSS) - RSS 分组管理
- RssSourceJsonEditDialog - RSS 源 JSON 编辑

### 10. 工具辅助类（9个）

- WaitDialog - 等待对话框
- VariableDialog - 变量设置
- PhotoDialog - 图片查看
- CookieViewerDialog - Cookie 查看器
- UrlOptionDialog - URL 选项
- RegexTestDialog - ~~正则测试~~ (已迁移至 RegexTestActivity - Compose)
- VerificationCodeDialog - 验证码
- OpenUrlConfirmDialog - URL 确认
- BackupInfoDialog - 备份信息

### 11. 视频播放类（3个）

- ChoiceEpisodeDialog - 剧集选择
- ChoiceSpeedDialog - 播放速度选择
- BottomWebViewDialog - 底部 WebView

### 12. 代码编辑器类（2个）

- SettingsDialog (code) - 代码编辑器设置
- ChangeThemeDialog (code) - 代码主题切换

### 13. 其他功能类（5个）

- SourceLoginDialog - 源登录
- BookmarkDialog - 书签
- ChangeCoverDialog - 封面更换
- EffectiveReplacesDialog - 生效的替换规则
- TxtTocRuleDialog / TxtTocRuleEditDialog - TXT 目录规则

### 14. 偏好设置类（3个）

- MultiSelectListPreferenceDialog - 多选列表偏好
- ListPreferenceDialog - 列表偏好
- EditTextPreferenceDialog - 文本编辑偏好

### 15. 系统更新类（1个）

- UpdateDialog - 应用更新

### 16. 视频设置类（1个）

- SettingsDialog (video) - 视频播放器设置

---

## 三、架构特点总结

### 1. 统一基类管理

大部分 Dialog 继承自 `BaseDialogFragment`，统一处理主题、软键盘、EInk 模式适配。

### 2. DSL 便捷调用

通过 `alert{}`、`progressDialog{}` 等 Kotlin DSL 简化 AlertDialog 使用。

### 3. 模块化设计

Dialog 按功能模块分目录管理（book/rss/dict/widget 等）。

### 4. 扩展函数支持

`DialogExtensions.kt` 提供布局设置、系统栏控制等扩展功能。

### 5. 生命周期安全

`BaseDialogFragment` 通过反射 + FragmentManager 安全显示，防止连续 add 崩溃。
