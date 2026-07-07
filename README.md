# Ember

Android 多媒体聚合播放器：多源 · 多引擎 · 媒体刮削。

> **v0.01** — 架构重构首发版本。本版本从 0 重建，旧版本（HybridPlayerEngine / EmbyLite）历史已清空。

## 功能特性

### 多媒体源（统一接口 `MediaSource`）

按四大类组织，已实现类型可直接使用，未实现类型在 UI 上标记「开发中」并在调用时返回友好提示。

| 分类 | 支持类型 | v0.01 状态 |
|------|----------|-----------|
| 本地媒体 | LOCAL（SAF 选文件） | ✅ 已实现 |
| 媒体服务器 | Emby / Jellyfin / Plex | Emby ✅ / Jellyfin / Plex 预留 |
| 网络协议 | SMB / WebDAV / FTP / SFTP / NFS | SMB ✅ / WebDAV ✅ / 其余预留 |
| 网盘 | 阿里云盘 / 百度网盘 / OneDrive / Google Drive | 全部预留（OAuth 流程待接入） |

- **统一数据模型** `UnifiedItem`：屏蔽不同源的差异，UI 层不关心来源
- **`SourceFactory`** 工厂模式按 `SourceType` 创建 Provider；未实现类型返回 `NotImplementedSource` 占位
- **`ServerManager`** 持久化用户添加的 `ServerConfig`（DataStore `ember_servers`）

### 多播放器引擎切换

| 引擎 | 适用场景 | v0.01 状态 |
|------|----------|-----------|
| ExoPlayer | HLS / DASH / 常规格式，省电 | ✅ 默认引擎 |
| MPV | ISO / BDMV 蓝光原盘 | 预留（回退 ExoPlayer） |
| VLC | 高兼容性 | 预留（回退 ExoPlayer） |

- **统一接口** `IPlayerCore`：play / pause / seekTo / 状态回调
- **`PlayerFactory`** 工厂按 `PlayerType` 创建内核；MPV/VLC 暂回退到 `ExoPlayerCore`
- **设置页可切换引擎**：非 EXO 引擎会提示「待集成，本次回退 ExoPlayer」
- **`PlayerActivity`** 独立 Activity，通过 Intent `item_id` 接收播放目标

### 媒体刮削（TMDB）

- **`Scraper`** 接口 + `TmdbScraper` 实现（TMDB API v3，OkHttp + JSON 解析）
- **`QueryCleaner`** 文件名清洗：去扩展名 / 分辨率（720p/1080p/4K）/ 编码（x264/h265）/ 来源（BluRay/WEB-DL）/ 提取年份
- **`ScrapePreviewDialog`**：点击视频文件先弹元数据卡片（海报 + 标题 + 年份 + 评分 + 简介），确认后播放
- **API Key 在设置页填写**，存于独立 DataStore（`ember_app_prefs`），避免与登录态冲突

### 其他

- **Jetpack Compose** 全量 Material3 UI，FlowRow + AssistChip 分类展示
- **DataStore Preferences** 持久化：登录态 / 服务器列表 / 引擎选择 / TMDB Key
- **AppLogger** 文件按天滚动 + UI 查看
- **OTA 更新**：检查 GitHub Releases 最新版本并一键升级

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 1.9.22 |
| JVM | 1.8 |
| Gradle | 8.14.4（mise 管理 java@17.0.2） |
| compileSdk / minSdk | 34 / 24 |
| Jetpack Compose BOM | 2024.04.01 |
| Material3 | 1.2.0 |
| ExoPlayer | 2.19.1 |
| Retrofit / OkHttp | 2.9.0 / 4.12.0 |
| Coil | 2.6.0 |
| DataStore | 1.0.0 |
| Navigation Compose | 2.7.7 |
| SMBj | 0.12.2 |

## 项目结构

```
com.ember/
├── data/
│   ├── api/                     # EmbyService / GithubService / AuthInterceptor
│   ├── local/
│   │   ├── TokenManager.kt      # 登录态（ember_prefs）
│   │   └── AppPrefs.kt          # 引擎选择 + TMDB Key（ember_app_prefs）
│   ├── model/                   # Emby 数据模型
│   ├── repository/              # EmbyRepository / UpdateManager(OTA)
│   └── source/                  # 媒体源统一架构
│       ├── MediaSource.kt       # 接口 + SourceType(13种) + SourceCategory(4类) + UnifiedItem + ServerConfig
│       ├── SourceFactory.kt     # 工厂 + NotImplementedSource 占位
│       ├── ServerManager.kt     # ServerConfig 持久化（ember_servers）
│       ├── LocalFileProvider.kt # SAF 本地文件
│       ├── SmbProvider.kt       # SMB 协议
│       └── WebDavProvider.kt    # WebDAV 协议
├── player/                      # 多播放器引擎
│   ├── IPlayerCore.kt           # 统一接口
│   ├── PlayerTypes.kt           # PlayerType(EXO/MPV/VLC) + PlayerState + FallbackType
│   ├── core/ExoPlayerCore.kt    # ExoPlayer 实现
│   ├── factory/PlayerFactory.kt # 工厂（MPV/VLC 暂回退 Exo）
│   ├── manager/PlayerManager.kt # 调度 + 智能切换
│   ├── interceptor/             # Emby URL 拦截器（内网/公网切换）
│   └── data/SmbDataSource.kt    # SMB 流式数据源
├── scrape/                      # 媒体刮削
│   ├── Scraper.kt               # 接口 + MediaMetadata + QueryCleaner
│   └── TmdbScraper.kt           # TMDB v3 实现
├── presentation/
│   ├── navigation/Screen.kt
│   ├── theme/
│   └── ui/
│       ├── common/              # BackScaffold 等通用组件
│       ├── login/               # 登录
│       ├── main/                # 主页 + 底部导航
│       ├── home/                # Emby 首页
│       ├── library/             # 媒体库
│       ├── search/              # 搜索
│       ├── detail/              # Emby 详情页
│       ├── sources/             # 媒体源（SourcesScreen + AddServer + FileBrowser）
│       ├── scrape/              # ScrapePreviewDialog 刮削预览
│       ├── player/              # PlayerActivity + PlayerViewModel
│       ├── settings/            # 设置（引擎/TMDB Key/日志/关于）
│       └── update/              # OTA 更新
├── utils/                       # AppLogger / NetworkModule / PlayerUtils / ErrorTranslator
└── EmberApplication.kt          # Application：初始化 TokenManager / ServerManager / AppPrefs
```

## 使用方法

### 1. 连接 Emby 服务器
1. 启动 App → 登录页
2. 输入服务器地址（如 `http://192.168.1.100:8096`）+ 用户名密码
3. 登录成功进入首页

### 2. 添加媒体源（SMB / WebDAV / 本地）
- 首页 → 媒体源
- 顶部「本地视频」入口：SAF 选文件直接播放
- 按分类点击 chip 添加服务器：填写地址 / 账号 / 密码 → 测试 → 保存
- 已添加的源出现在列表，点击进入文件浏览器浏览目录

### 3. 浏览与播放（含刮削预览）
- 文件浏览器点击视频文件 → 弹出**刮削预览对话框**
  - 已配置 TMDB Key：自动搜索元数据，展示海报 + 标题 + 年份 + 评分 + 简介
  - 未配置 Key 或无结果：提示后提供「播放」按钮直接播放
- 点击「播放」跳转 PlayerActivity

### 4. 设置
- 首页 → 设置
- **播放器引擎**：切换 EXO / MPV / VLC（MPV/VLC 待集成，会回退 ExoPlayer）
- **TMDB API Key**：填写后启用媒体刮削（[申请地址](https://www.themoviedb.org/settings/api)）
- **检查更新** / **系统日志** / **退出登录**

### 5. 播放器手势
- 单击：切换控制层
- 双击：播放/暂停
- 进度条：拖动跳转
- 倍速按钮：0.5x ~ 2.0x
- 锁屏 / 旋转 / 快进快退

## 内核扩展指南

v0.01 默认仅编译 ExoPlayer 内核。添加 MPV/VLC：

### MPV
1. `app/build.gradle.kts` 添加：`implementation("com.github.jarnedemeester:mpv-android-lib:v2.0.4")`
2. 实现 `MpvCore : IPlayerCore`
3. `PlayerFactory` 中将 `PlayerType.MPV -> ExoPlayerCore()` 改为 `PlayerType.MPV -> MpvCore()`

### VLC
1. 添加：`implementation("org.videolan.android:libvlc-all:3.5.1")`
2. 实现 `VlcCore : IPlayerCore`
3. `PlayerFactory` 中启用 VLC 分支

## 媒体源扩展指南

新增一种协议 / 网盘 Provider：

1. 在 `MediaSource.kt` 的 `SourceType` 枚举添加类型，设置 `category` / `displayName` / `implemented`
2. 实现 `XxxProvider : MediaSource`（参考 `SmbProvider`）
3. `SourceFactory.create()` 中添加 when 分支
4. 若是网盘，将类型加入 `OAUTH_TYPES`，并实现 OAuth 授权流程
5. `AddServerScreen` 的表单字段按类型自动适配

## 构建

```bash
# 需要 JDK 17 + Android SDK 34
gradle :app:assembleRelease
```

签名配置：`ember.keystore`（密码 `ember123`，alias `ember`）

## 发布同步

```bash
# 设置 GitHub Token（需 repo 权限）
export GH_TOKEN="你的GitHub Personal Access Token"

# 自动读取 versionName 构建 + 提交 + 推送 + 创建 Release + 上传 APK
./scripts/sync_to_github.sh

# 或指定版本和说明
./scripts/sync_to_github.sh v0.01 "首发版本说明"
```

## 下载

[GitHub Releases](https://github.com/xmfx11/Ember/releases)

## 版本

| 版本 | 说明 |
|------|------|
| v0.04 | 启动流程重构：一打开直接进首页（不再强制 Emby 登录）；媒体源集中到设置里（抽屉式入口）；设置页完全重写为现代分组式 UI（借鉴 iOS/Material You，圆角图标+分组卡片+右箭头）；底部导航精简为 3 Tab（首页/搜索/设置）；未连接 Emby 时首页显示引导卡片 |
| v0.03 | 播放器全面对标主流播放器：长按临时 3x 倍速（松开恢复）、进度拖动顶部大时间预览、画面比例切换（FIT/FILL/ZOOM/16:9）、截图保存相册、睡眠定时（15/30/45/60/90 分钟）、顶部更多菜单、手势提示放大 |
| v0.02 | 媒体源入口前置：底部导航新增「媒体源」Tab（替换重复的媒体库 Tab）；播放器手势优化（水平滑动改为固定基准+120s/屏限速，更自然）；播放器样式美化（进度条 thumb 弹簧动画+光晕、中央按钮阴影放大、渐变增强）；起播速度优化（minBuffer 1s）|
| v0.01 | 架构重构首发：多媒体源统一架构（本地/Emby/SMB/WebDAV 已实现，其余预留）+ 多播放器引擎切换框架（ExoPlayer 默认，MPV/VLC 预留）+ TMDB 媒体刮削（设置页填 Key，文件浏览器刮削预览）|
