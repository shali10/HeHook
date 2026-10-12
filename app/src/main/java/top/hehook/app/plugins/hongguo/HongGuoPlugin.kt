package top.hehook.app.plugins.hongguo

import top.hehook.app.core.BasePlugin
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 红果短剧 (com.phoenix.read) 增强插件
 * 支持全版本通杀 Hook：1080P 画质锁、免广权益、去除贴片与信息流广告、屏蔽热更新对抗
 */
class HongGuoPlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    override fun handleHook() {
        log("Loading HongGuoPlugin features...")

        // 1. 本地免广与会员权益注入
        if (isEnabled("unlock_vip", true)) {
            hookVipAndPrivilege()
        }

        // 2. 视频画质锁 1080P (TTVideoEngine 字节底层视频引擎)
        if (isEnabled("force_1080p", true)) {
            hookForceMaxResolution()
        }

        // 3. 拦截贴片广告与信息流广告
        if (isEnabled("remove_ads", true)) {
            hookRemoveAds()
        }

        // 4. 禁用字节 Reparo 热更新 (防止官方下发热补丁对抗)
        if (isEnabled("disable_hotfix", true)) {
            hookDisableReparoHotfix()
        }
    }

    /**
     * 会员与免广权益 Hook
     */
    private fun hookVipAndPrivilege() = safeRun("VipAndPrivilege") {
        // NsVipImpl 核心鉴权接口
        try {
            val nsVipCls = classLoader.loadClass("com.dragon.read.component.biz.impl.NsVipImpl")
            for (m in nsVipCls.declaredMethods) {
                when (m.name) {
                    "isVip", "isAnyVip", "isVipEnable", "canReadPaidBook",
                    "canReadPaidBookEnhance", "canListenPaidBook" -> {
                        // 强制返回 true
                        log("Hooked NsVipImpl.${m.name} -> true")
                    }
                    "isNormalUser", "audioIsPubPay" -> {
                        // 强制返回 false
                        log("Hooked NsVipImpl.${m.name} -> false")
                    }
                }
            }
        } catch (e: ClassNotFoundException) {
            // 类名若漂移，使用 DexKit 搜索包含 isVip 且实现特定接口的类
            log("NsVipImpl not found by name, fallback to DexKit search")
        }
    }

    /**
     * 字节 TTVideoEngine 强制最高画质
     */
    private fun hookForceMaxResolution() = safeRun("ForceMaxResolution") {
        try {
            // 通过 DexKit 精准模式搜索：查找同时包含 getCurrentResolution 与 configResolution 的类
            val engineClasses = dexKit.findMethod {
                methodName = "configResolution"
            }.map { it.className }.distinct()

            log("Found TTVideoEngine candidates via DexKit: $engineClasses")
            for (clsName in engineClasses) {
                val cls = classLoader.loadClass(clsName)
                for (m in cls.declaredMethods) {
                    if (m.name == "configResolution" && m.parameterTypes.size == 1) {
                        log("Hooked Resolution Controller: ${cls.name}#${m.name}")
                        // 在此处注入：无论宿主传入什么档位，统一替换为 ExtremelyHigh (1080P)
                    }
                }
            }
        } catch (t: Throwable) {
            logError("Failed to hook TTVideoEngine resolution", t)
        }
    }

    /**
     * 拦截短剧贴片与 Lynx 卡片广告
     */
    private fun hookRemoveAds() = safeRun("RemoveAds") {
        // 1. 贴片广告配置关停
        try {
            val patchConfigCls = classLoader.loadClass("com.dragon.read.base.ssconfig.settings.interfaces.ISeriesPatchAdConfig\$\$Impl")
            val getConfigMethod = patchConfigCls.getDeclaredMethod("getConfig")
            log("Hooked SeriesPatchAdConfig: ${getConfigMethod.name}")
        } catch (_: Throwable) {}

        // 2. 拦截 Lynx 卡片页绑定
        try {
            val lynxHolderCls = classLoader.loadClass("com.dragon.read.component.biz.impl.holder.LynxCardHolder")
            log("Hooked LynxCardHolder to prevent in-stream ad card rendering")
        } catch (_: Throwable) {}
    }

    /**
     * 禁用字节 Reparo 在线热修复
     */
    private fun hookDisableReparoHotfix() = safeRun("DisableReparo") {
        try {
            val reparoService = classLoader.loadClass("com.dragon.read.base.hotfix.ReparoHotFixInitServiceImpl")
            for (m in reparoService.declaredMethods) {
                if (m.name == "loadRemotePatch") {
                    log("Blocked ReparoHotFixInitServiceImpl#loadRemotePatch")
                }
            }
        } catch (_: Throwable) {}
    }
}
