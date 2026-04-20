# Web服务代码架构

## 核心文件结构

```
app/src/main/java/io/legado/app/
├── service/
│   ├── WebService.kt          # Web服务主服务（后台运行、通知管理）
│   └── WebTileService.kt      # 快捷设置开关（Android 7.0+）
├── web/
│   ├── HttpServer.kt          # HTTP服务器（NanoHTTPD）
│   ├── WebSocketServer.kt     # WebSocket服务器（NanoWSD）
│   ├── ReadMe.md              # 说明文档
│   ├── socket/
│   │   ├── BookSourceDebugWebSocket.kt   # 书源调试WebSocket
│   │   ├── RssSourceDebugWebSocket.kt    # RSS源调试WebSocket
│   │   └── BookSearchWebSocket.kt        # 书籍搜索WebSocket
│   └── utils/
│       └── AssetsWeb.kt       # 静态资源服务
└── api/
    ├── ReturnData.kt          # API返回数据封装
    └── controller/
        ├── BookController.kt       # 书籍相关API
        ├── BookSourceController.kt # 书源相关API
        ├── RssSourceController.kt  # RSS源相关API
        └── ReplaceRuleController.kt # 替换规则API
```

## 服务架构

| 组件 | 端口 | 功能 |
|------|------|------|
| WebService.kt | - | 后台服务管理，WakeLock/WifiLock |
| HttpServer.kt | 配置端口(默认1122) | REST API服务 |
| WebSocketServer.kt | HTTP端口+1 | 实时通信服务 |

## HTTP API接口

### GET 请求
| 路径 | 功能 |
|------|------|
| `/getBookSource` | 获取单个书源 |
| `/getBookSources` | 获取所有书源 |
| `/getBookshelf` | 获取书架列表 |
| `/getChapterList` | 获取章节目录 |
| `/refreshToc` | 刷新目录 |
| `/getBookContent` | 获取正文内容 |
| `/cover` | 获取封面图片 |
| `/image` | 获取正文图片 |
| `/getReadConfig` | 获取阅读配置 |
| `/getRssSource` | 获取RSS源 |
| `/getRssSources` | 获取所有RSS源 |
| `/getReplaceRules` | 获取替换规则 |

### POST 请求
| 路径 | 功能 |
|------|------|
| `/saveBookSource` | 保存书源 |
| `/saveBookSources` | 批量保存书源 |
| `/deleteBookSources` | 删除书源 |
| `/saveBook` | 保存书籍 |
| `/deleteBook` | 删除书籍 |
| `/saveBookProgress` | 保存阅读进度 |
| `/addLocalBook` | 添加本地书籍 |
| `/saveReadConfig` | 保存阅读配置 |
| `/saveRssSource` | 保存RSS源 |
| `/saveReplaceRule` | 保存替换规则 |
| `/testReplaceRule` | 测试替换规则 |

## WebSocket接口

| 路径 | 功能 |
|------|------|
| `/bookSourceDebug` | 书源调试 |
| `/rssSourceDebug` | RSS源调试 |
| `/searchBook` | 书籍搜索 |

## 关键扩展点

### 新增HTTP API
1. 在 `HttpServer.kt` 的 `when(uri)` 中添加新路由
2. 在 `controller/` 下创建对应的Controller

### 新增WebSocket服务
1. 继承 `NanoWSD.WebSocket`
2. 在 `WebSocketServer.kt` 注册新路径

### 修改服务配置
- 端口：`PreferKey.webPort`（默认1122）
- WakeLock：`PreferKey.webServiceWakeLock`

### 前端页面
- 静态资源位于 `assets/web/` 目录
- Vue前端源码位于 `modules/web/`

## 核心类说明

### WebService.kt
- 继承 `BaseService`，作为Android后台服务运行
- 管理HTTP和WebSocket服务器的生命周期
- 处理网络状态变化，动态更新通知栏IP地址
- 支持WakeLock和WifiLock保持服务运行

### HttpServer.kt
- 继承 `NanoHTTPD`，轻量级HTTP服务器
- 处理GET/POST请求，路由到对应Controller
- 支持CORS跨域请求
- 大数据响应使用Pipe流式传输

### WebSocketServer.kt
- 继承 `NanoWSD`，WebSocket服务器
- 根据URI路径创建不同的WebSocket处理器
- 用于实时调试和搜索功能

### ReturnData.kt
- API统一返回格式
- 字段：`isSuccess`、`errorMsg`、`data`
