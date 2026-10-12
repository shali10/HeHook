package top.hehook.app.plugins.fanqie

import top.hehook.app.core.BasePlugin
import java.lang.reflect.Method

/**
 * 番茄免费小说 (com.dragon.read) 增强插件
 * 支持：免广沉浸阅读、去章节末尾广告、解锁高品质听书音色、屏蔽金币悬浮挂件
 */
class FanQiePlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    override fun handleHook() {
        log("Loading FanQiePlugin features...")

        // 1. 全局免广与 VIP 权限注入
        if (isEnabled("unlock_reader_vip", true)) {
            hookReaderVip()
        }

        // 2. 拦截章节末尾广告与开屏广告
        if (isEnabled("remove_reader_ads", true)) {
            hookReaderAdBlock()
        }

        // 3. 解锁高品质 AI 朗读音色
        if (isEnabled("unlock_tts_timbre", true)) {
            hookTtsTimbre()
        }

        // 4. 隐藏金币红包悬浮挂件
        if (isEnabled("hide_floating_pendant", true)) {
            hookHidePendant()
        }
    }

    /**
     * VIP 身份与免广阅读权限
     */
    private fun hookReaderVip() = safeRun("ReaderVip") {
        try {
            val nsVipCls = classLoader.loadClass("com.dragon.read.component.biz.impl.NsVipImpl")
            for (m in nsVipCls.declaredMethods) {
                when (m.name) {
                    "isVip", "isAnyVip", "canReadPaidBook", "isVipEnable", "canReadPaidBookEnhance" -> {
                        replaceMethod(m, true)
                        log("Replaced FanQie NsVipImpl.${m.name} -> true")
                    }
                    "isNormalUser", "audioIsPubPay" -> {
                        replaceMethod(m, false)
                        log("Replaced FanQie NsVipImpl.${m.name} -> false")
                    }
                }
            }
        } catch (_: Throwable) {
            log("NsVipImpl not found, trying fallback")
        }
    }

    /**
     * 章节末尾插页广告与信息流拦截
     */
    private fun hookReaderAdBlock() = safeRun("ReaderAdBlock") {
        try {
            val feedDrawCls = classLoader.loadClass("com.dragon.read.ad.feeddraw.FeedDrawAdManager")
            for (m in feedDrawCls.declaredMethods) {
                if (m.name == "getAd") {
                    replaceMethod(m, null)
                    log("Blocked FanQie FeedDrawAdManager.getAd -> null")
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * 解锁听书优质音色限制
     */
    private fun hookTtsTimbre() = safeRun("TtsTimbre") {
        try {
            val timbreMethods = dexKit.findMethod {
                matcher {
                    name = "isVipTimbre"
                }
            }
            for (m in timbreMethods) {
                val methodInstance = m.getMethodInstance(classLoader)
                replaceMethod(methodInstance, false)
                log("Replaced isVipTimbre -> false for ${m.className}")
            }
        } catch (_: Throwable) {}
    }

    /**
     * 隐藏悬浮金币挂件与活动弹窗
     */
    private fun hookHidePendant() = safeRun("HidePendant") {
        try {
            val pendantCls = classLoader.loadClass("com.bytedance.ug.sdk.novel.pendant.PlanPendantServiceImpl")
            for (m in pendantCls.declaredMethods) {
                if (m.name == "triggerEvent") {
                    blockMethod(m)
                    log("Blocked FanQie Pendant triggerEvent")
                }
            }
        } catch (_: Throwable) {}
    }
}
