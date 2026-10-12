package top.hehook.app.plugins.hongguo

import top.hehook.app.core.BasePlugin
import java.lang.reflect.Method

/**
 * 红果短剧 (com.phoenix.read) 增强插件
 * 全量实装拦截：免广权益、去除贴片与信息流广告、1080P画质强锁、屏蔽热修对抗
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

        // 2. 视频画质锁 1080P (TTVideoEngine)
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
     * 会员与免广权益 Hook（真实替换返回值）
     */
    private fun hookVipAndPrivilege() = safeRun("VipAndPrivilege") {
        // A. NsVipImpl 核心鉴权接口
        try {
            val nsVipCls = classLoader.loadClass("com.dragon.read.component.biz.impl.NsVipImpl")
            for (m in nsVipCls.declaredMethods) {
                when (m.name) {
                    "isVip", "isAnyVip", "isVipEnable", "canReadPaidBook",
                    "canReadPaidBookEnhance", "canListenPaidBook" -> {
                        replaceMethod(m, true)
                        log("Replaced NsVipImpl.${m.name} -> true")
                    }
                    "isNormalUser", "audioIsPubPay" -> {
                        replaceMethod(m, false)
                        log("Replaced NsVipImpl.${m.name} -> false")
                    }
                }
            }
        } catch (_: Throwable) {
            log("NsVipImpl not found by name, attempting DexKit search")
        }

        // B. 短剧免广权益类 (com.dragon.read.ad.shortseries.privilege.b)
        try {
            val privCls = classLoader.loadClass("com.dragon.read.ad.shortseries.privilege.b")
            for (m in privCls.declaredMethods) {
                if (m.returnType == Boolean::class.javaPrimitiveType && m.parameterTypes.isEmpty()) {
                    replaceMethod(m, true)
                    log("Replaced Privilege.${m.name} -> true")
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * 字节 TTVideoEngine 强制最高画质 (1080P)
     */
    private fun hookForceMaxResolution() = safeRun("ForceMaxResolution") {
        try {
            val resolutionCls = classLoader.loadClass("com.ss.ttvideoengine.Resolution")
            // 提取 1080P (ExtremelyHigh) 枚举常量
            var targetRes: Any? = null
            for (f in resolutionCls.fields) {
                if (f.name.equals("ExtremelyHigh", ignoreCase = true) || f.name.contains("1080")) {
                    targetRes = f.get(null)
                    break
                }
            }

            if (targetRes != null) {
                val finalRes = targetRes
                // Hook TTVideoEngine.configResolution(Resolution)
                val engineClasses = listOf(
                    "com.ss.ttvideoengine.TTVideoEngine",
                    "com.ss.ttvideoengine.TTVideoEngineImpl"
                )
                for (name in engineClasses) {
                    try {
                        val cls = classLoader.loadClass(name)
                        for (m in cls.declaredMethods) {
                            if (m.name == "configResolution" && m.parameterTypes.size == 1) {
                                hookMethod(m) { chain ->
                                    val newArgs = chain.args.toTypedArray()
                                    if (newArgs.isNotEmpty()) {
                                        newArgs[0] = finalRes
                                    }
                                    chain.proceed(newArgs)
                                }
                                log("Hooked configResolution on $name")
                            }
                        }
                    } catch (_: Throwable) {}
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
        // 1. 一站式短剧广告核心拦截 (y 方法拦截不出广告)
        try {
            val sdkAdapterCls = classLoader.loadClass("com.dragon.read.ad.onestop.seriessdk.adapter.j")
            for (m in sdkAdapterCls.declaredMethods) {
                if (m.returnType == Boolean::class.javaPrimitiveType) {
                    replaceMethod(m, false)
                    log("Blocked OneStop SeriesSDK Ad: ${m.name} -> false")
                }
            }
        } catch (_: Throwable) {}

        // 2. 贴片广告展示口拦截 (l4.M0 拦截)
        try {
            val l4Cls = classLoader.loadClass("com.dragon.read.component.shortvideo.impl.inject.view.l4")
            for (m in l4Cls.declaredMethods) {
                blockMethod(m)
                log("Blocked Patch Ad View: ${m.name}")
            }
        } catch (_: Throwable) {}

        // 3. 拦截 Lynx 卡片页绑定 (LynxCardHolder.onBind)
        try {
            val lynxHolderCls = classLoader.loadClass("com.dragon.read.component.biz.impl.holder.LynxCardHolder")
            for (m in lynxHolderCls.declaredMethods) {
                if (m.name == "onBind" || m.name == "h4") {
                    blockMethod(m)
                    log("Blocked LynxCardHolder: ${m.name}")
                }
            }
        } catch (_: Throwable) {}

        // 4. 电商带货卡与商品面板拦截
        try {
            val ecomBizCls = classLoader.loadClass("com.dragon.read.component.shortvideo.impl.sameproduct.aiseed.EcomAiProductSeedingBiz")
            for (m in ecomBizCls.declaredMethods) {
                if (m.name == "D") {
                    blockMethod(m)
                    log("Blocked Ecom Product Seeding Biz.D")
                }
            }
        } catch (_: Throwable) {}

        // 5. 老版信息流广告拦截 (FeedDrawAdManager.getAd -> null)
        try {
            val feedDrawCls = classLoader.loadClass("com.dragon.read.ad.feeddraw.FeedDrawAdManager")
            for (m in feedDrawCls.declaredMethods) {
                if (m.name == "getAd") {
                    replaceMethod(m, null)
                    log("Blocked FeedDrawAdManager.getAd -> null")
                }
            }
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
                    blockMethod(m)
                    log("Blocked ReparoHotFixInitServiceImpl#loadRemotePatch")
                }
            }
        } catch (_: Throwable) {}

        try {
            val reparoCfg = classLoader.loadClass("com.bytedance.reparo.IReparoConfig")
            for (m in reparoCfg.declaredMethods) {
                if (m.name == "enable" || m.name == "autoFetchPatch") {
                    replaceMethod(m, false)
                    log("Disabled Reparo Config: ${m.name} -> false")
                }
            }
        } catch (_: Throwable) {}
    }
}
