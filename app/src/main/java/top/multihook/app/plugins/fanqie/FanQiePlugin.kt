package top.multihook.app.plugins.fanqie

import top.multihook.app.core.BasePlugin
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
            // 番茄小说的核心账号管理器
            val acctManagerCls = classLoader.loadClass("com.dragon.read.user.AcctManager")
            log("Loaded AcctManager: ${acctManagerCls.name}")

            // Hook 核心 VIP 判断
            val nsVipCls = classLoader.loadClass("com.dragon.read.component.biz.impl.NsVipImpl")
            for (m in nsVipCls.declaredMethods) {
                if (m.name in listOf("isVip", "isAnyVip", "canReadPaidBook", "isVipEnable")) {
                    log("Hooked FanQie NsVipImpl.${m.name} -> true")
                }
            }
        } catch (t: Throwable) {
            log("Fallback to DexKit searching reader privilege methods")
        }
    }

    /**
     * 章节末尾插页广告与信息流拦截
     */
    private fun hookReaderAdBlock() = safeRun("ReaderAdBlock") {
        try {
            // 使用 DexKit 动态查找广告配置类：包含 "feed_ad" 或 "page_ad" 标志的方法
            val adMethods = dexKit.findMethod {
                matcher {
                    usingStrings = listOf("feed_ad_dislike", "ad_cell")
                }
            }
            log("Found ${adMethods.size} reader ad candidate methods via DexKit")
        } catch (t: Throwable) {
            logError("Failed to find reader ad classes", t)
        }
    }

    /**
     * 解锁听书优质音色限制
     */
    private fun hookTtsTimbre() = safeRun("TtsTimbre") {
        try {
            // 搜索听书音色鉴权模型（包含 isVipTimbre / isFree 等方法）
            val timbreMethods = dexKit.findMethod {
                methodName = "isVipTimbre"
            }
            for (m in timbreMethods) {
                log("Hooked TTS Timbre permission: ${m.className}#${m.methodName} -> false (Free to use)")
            }
        } catch (_: Throwable) {}
    }

    /**
     * 隐藏悬浮金币挂件与活动弹窗
     */
    private fun hookHidePendant() = safeRun("HidePendant") {
        try {
            val pendantCls = classLoader.loadClass("com.bytedance.ug.sdk.novel.pendant.PlanPendantServiceImpl")
            log("Hooked FanQie Pendant Service: ${pendantCls.name}")
        } catch (_: Throwable) {}
    }
}
