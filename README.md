# HeHook

现代化 LSPosed 多应用聚合增强框架，基于 Modern LibXposed (API 101) 构建。
零 SO 依赖、纯单机、零网络、零账号。

## 托管应用

| 应用 | 包名 | 能力 |
|---|---|---|
| 红果短剧 | `com.phoenix.read` | 会员净化 · 1080P · 插播广告清除 · 禁用热修 |
| 番茄小说 | `com.dragon.read` | 阅读免广 · AI 音色解锁 · 挂件净化 |
| 哔哩哔哩 | `tv.danmaku.bili` | 开屏秒过 · 推荐流净化 · 后台播放 |
| 汽水音乐 | `com.luna.music` | 免广畅听 · 音频插播拦截 · 视觉净化 |

## 架构

```
MainRouter (LibXposed API 101 单一入口)
   └── PluginRegistry            应用清单 / 开关定义 / 工厂（唯一事实源）
        └── BasePlugin           各宿主插件
             └── HookKit         反射与拦截套件 ──► HookRuntime（唯一后端）
                  └── HookStats   applied / failed / missing 计数
```

- **入口只有一条**：`META-INF/xposed/java_init.list`。老版 `assets/xposed_init` 已移除——
  那条路径装不上 LibXposed 后端，hook 会全部静默失败，留着比没有更坏。需要 LSPosed 1.9+。
- **注册表单表**：路由分发、管理端卡片、开关项全部由 `PluginRegistry` 派生，新增应用只改一处。
- **可观测**：每次接管宿主都会输出一行 error 级日志
  `HeHook 汇总 <应用> | applied=N failed=N missing=N | 耗时 Nms`，
  在 LSPosed 日志页直接可见。

## 构建

```bash
# 1. 版本只改 version.properties（module.prop 不一致会直接构建失败）
# 2. 本机放好签名密钥与密码
export HEHOOK_KEYSTORE=/path/to/hehook-release.jks
export HEHOOK_STORE_PASSWORD=***
export HEHOOK_KEY_ALIAS=hehook
export HEHOOK_KEY_PASSWORD=***
./gradlew assembleRelease
```

CI 走 `.github/workflows/build.yml`：从仓库 secret 还原密钥（`HEHOOK_KEYSTORE_B64`、
`HEHOOK_STORE_PASSWORD`、`HEHOOK_KEY_ALIAS`、`HEHOOK_KEY_PASSWORD`），
产物名取自 `version.properties`，并在上传前校验签名与版本号。

> 签名密钥与密码**不入库**。仓库历史里曾提交过旧密钥（`app/hehook.jks`），
> 已从工作树移除并更换新密钥；旧密钥应当视为已泄露。

## 诊断

宿主启动后看 LSPosed 日志：

1. `== MainRouter CLASS LOADED ==` — 模块被注入
2. `== onPackageReady == pkg=... plugin=...` — 路由命中
3. `HeHook 汇总 ... applied=N` — 真正挂上几条

`applied=0` 且 `missing` 很大，说明宿主的类名/方法名随版本漂移了，需要重新核对符号，
而不是继续加 hook。
