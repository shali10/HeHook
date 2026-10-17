# HeHook - 现代化 LSPosed 多应用聚合增强框架

[![Release](https://img.shields.io/github/v/release/shali10/HeHook?color=blue&label=Release)](https://github.com/shali10/HeHook/releases)
[![API](https://img.shields.io/badge/LibXposed-API%20101-brightgreen)](https://github.com/libxposed)
[![Build](https://img.shields.io/github/actions/workflow/status/shali10/HeHook/build.yml?label=Build)](https://github.com/shali10/HeHook/actions)

基于 **Modern LibXposed (API 101)** 规范构建的工业级 Android Hook 聚合框架。以一个模块无感接管多款主流流媒体、阅读与工具类应用，坚持「零 SO 依赖、纯单机、零反射损耗、全版本通杀」为核心工程原则。

---

## 🎯 聚合应用支持矩阵

| 目标应用领域 | 作用域 / 代表包名 | 核心注入与增强能力 | 当前状态 |
|---|---|---|---|
| **短剧流媒体** | `com.phoenix.read` | 强制 1080P 超清强锁、全域中插免广、Lynx/带货卡片剔除、屏蔽 Reparo 热修复对抗 | 🟢 深度稳定 (无损丝滑) |
| **电子阅读 & 听书** | `com.dragon.read` | 沉浸阅读全屏免广、AI 高品质多音色解锁、悬浮金币挂件与活动弹窗净化 | 🟢 深度稳定 (无损丝滑) |
| **长视频与弹幕平台** | `tv.danmaku.bili` | 现代 gRPC 开屏秒杀、双通道大会员接口特权注入、首页 Pegasus 推荐流净化、后台常驻播放 | 🟢 深度稳定 (冷启动直通) |
| **更多应用生态** | 多应用扩展中 | 基于 DexKit 模式匹配与协议层拦截管线持续收敛接入 | 🚀 持续扩充 |

---

## 🏗️ 核心架构与工程演进

1. **统一运行时路由器 (`MainRouter.kt`)**：
   - 适配现代 LSPosed API 101 规范，进入宿主进程后按 `packageName` 秒级路由分发，各应用插件沙盒隔离，零互扰。
2. **源头构造参数劫持引擎 (`HookKit.kt`)**：
   - 废除列表高频数据模型的暴力反射，在对象初始化阶段一次性重写高位凭据与特权状态，拦截开销从 $O(N)$ 降至 $O(1)$，彻底根治滑动掉帧与卡顿。
3. **协议层清障与语义总闸**：
   - 深入 gRPC 传输协议回包层将广告下发与等待秒数双清零，配合展示层门禁直通，实现开屏零感知秒进；
   - 废除模糊单字母 Hook，收敛至具备强业务语义的全局门禁，杜绝误伤宿主 UI 布局与测量死循环。
4. **确定性交付与签名锁死 (`hehook.jks`)**：
   - 内置专属永久密钥库（有效期至 2054 年，支持 V1+V2+V3 全规范签名），杜绝 CI 瞬态环境证书漂移，未来全量版本终身支持直接覆盖更新。
5. **中心管理端 App (`MainActivity.kt`)**：
   - 提供直观的 Material 3 卡片控制面板；
   - 支持单应用细粒度开关与一键强停生效（免手动去系统设置退进程）。

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
（已自动完成 4KB 内存页对齐与永久正式签名）
