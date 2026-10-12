# HeHook - 一站式现代 LSPosed 多应用聚合增强模块

基于 **LibXposed (API 101)** 与 **DexKit** 构建的工业级 Android Hook 聚合框架。一个模块同时覆盖多款主流 App，支持全版本混淆模式匹配与通杀。

---

## 🎯 第一批支持应用与特性

### 1. 🎬 红果短剧 (`com.phoenix.read`)
- **强制 1080P 最高画质**：Hook 字节底层 `TTVideoEngine`，防止弱网自适应降档并强锁 ExtremelyHigh 档位；
- **免广与会员特权**：Hook `NsVipImpl` 与免广权益判定类，纯本地免广告畅看；
- **信息流与贴片广告净化**：屏蔽 Lynx 动态卡片页、电商带货商品卡与贴片展示口；
- **屏蔽 Reparo 热更新**：斩断字节官方在线热修复，防止官方下发热补丁搞模块对抗。

### 2. 📖 番茄免费小说 (`com.dragon.read`)
- **沉浸阅读免广**：去除章节末尾翻页全屏广告与激励视频卡片；
- **VIP 体验注入**：解锁正版小说付费试读与全量排版特权；
- **高品质 AI 音色解锁**：解除听书引擎中的 VIP 专属音色限制；
- **悬浮挂件隐藏**：阻断福利金币、活动福袋等视觉污染。

### 3. 📺 哔哩哔哩 (`tv.danmaku.bili`)
- **秒过开屏广告**：阻断 Splash 广告拉取流程，直入首页；
- **首页卡片广告剔除**：拦截推荐流反序列化数据，净化推广与带货卡片；
- **后台音频播放解锁**：解除播放器切后台或锁屏暂停限制。

---

## 🏗️ 核心架构设计

1. **统一运行时路由器 (`MainRouter.kt`)**：
   - 适配 LSPosed API 101 规范，进入宿主进程后按 `packageName` 秒级路由分发，各应用插件沙盒隔离。
2. **DexKit 全版本通杀底座**：
   - 不依赖脆弱的混淆类名，直接通过 C++ 引擎在内存中对 DEX 做操作码流、常量与签名特征匹配。
3. **中心管理端 App (`MainActivity.kt`)**：
   - 提供直观的 Material 3 卡片控制面板；
   - 支持单应用细粒度开关与一键强停生效（免手动去系统设置退进程）。
4. **跨进程配置持久化 (`ConfigManager.kt`)**：
   - 采用宿主私有媒体存储，零多余权限，无感同步。

---

## 🚀 编译与构建

### 方式一：Android Studio 本地构建
1. 打开 Android Studio，选择 **Open** 并导入当前工程目录；
2. 等待 Gradle Sync 完成；
3. 点击菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)** 即可产出。

### 方式二：命令行构建
```bash
./gradlew assembleRelease
```
产出路径：`app/build/outputs/apk/release/app-release.apk`
