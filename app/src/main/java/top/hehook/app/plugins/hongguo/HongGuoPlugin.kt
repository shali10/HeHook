package top.hehook.app.plugins.hongguo

import top.hehook.app.core.BasePlugin
import top.hehook.app.core.ByteDanceReadEngine
import top.hehook.app.core.HookKit

/**
 * 红果短剧 (com.phoenix.read) 增强插件
 * 接入 ByteDanceReadEngine 全套核心：AdGate 总闸、PrivilegeManager VIP 全穿透、OneStop y() 跳过、试看清零与热修防御
 * 独家追加：1080P 超清强锁 (TTVideoEngine) 与 短剧付费判定门禁
 */
class HongGuoPlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    override fun handleHook() {
        log("Loading HongGuoPlugin v1.1.0...")

        // 1. 挂载字节阅读平台通用引擎 (AdGate 广告总闸 + PrivilegeManager VIP + OneStopAd + 试看清零 + 禁用热修)
        ByteDanceReadEngine(this, isHongGuo = true).installAll()

        // 2. 视频画质强锁 1080P (TTVideoEngine)
        if (isEnabled("force_1080p", true)) {
            hookForceMaxResolution()
        }

        // 3. 短剧付费判定门禁 (ma4.q3 / w65.c / t75.b)
        if (isEnabled("unlock_vip", true)) {
            hookShortSeriesPayGate()
        }
    }

    /**
     * 字节 TTVideoEngine 强制最高画质 (1080P / ExtremelyHigh)
     */
    private fun hookForceMaxResolution() = safeRun("ForceMaxResolution") {
        try {
            val resCls = HookKit.loadClass(classLoader, "com.ss.ttvideoengine.Resolution")
            if (resCls != null) {
                var targetRes: Any? = null
                for (f in resCls.fields) {
                    if (f.name.equals("ExtremelyHigh", ignoreCase = true) || f.name.contains("1080")) {
                        targetRes = f.get(null)
                        break
                    }
                }

                if (targetRes != null) {
                    val finalRes = targetRes
                    val engineClasses = listOf(
                        "com.ss.ttvideoengine.TTVideoEngine",
                        "com.ss.ttvideoengine.TTVideoEngineImpl"
                    )
                    for (name in engineClasses) {
                        val cls = HookKit.loadClass(classLoader, name)
                        if (cls != null) {
                            HookKit.hookAllByName(cls, "configResolution") { chain ->
                                val args = chain.args
                                if (args.isNotEmpty()) {
                                    chain.setArg(0, finalRes)
                                }
                            }
                            log("TTVideoEngine: $name#configResolution forced to 1080P")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            logError("Failed to hook TTVideoEngine resolution", t)
        }
    }

    /**
     * 短剧付费判定门禁 (ma4.q3 / w65.c / t75.b)
     */
    private fun hookShortSeriesPayGate() = safeRun("ShortSeriesPayGate") {
        // 短剧付费物料判定 (t75.b a->false, d->true)
        val t75Cls = HookKit.loadClass(classLoader, "t75.b")
        if (t75Cls != null) {
            HookKit.hookAllByName(t75Cls, "a") { it.setResult(false) }
            HookKit.hookAllByName(t75Cls, "d") { it.setResult(true) }
            log("PayGate: t75.b a->false, d->true hooked")
        }

        // 短剧付费管理器 (ma4.q3 l->false)
        val ma4Cls = HookKit.loadClass(classLoader, "ma4.q3")
        if (ma4Cls != null) {
            HookKit.hookAllByName(ma4Cls, "l") { it.setResult(false) }
            HookKit.hookAllByName(ma4Cls, "k") { it.setResult(false) }
            HookKit.hookAllByName(ma4Cls, "j") { it.setResult(false) }
            log("PayGate: ma4.q3 hooked")
        }

        // 短剧付费判定 (w65.c a->false)
        val w65Cls = HookKit.loadClass(classLoader, "w65.c")
        if (w65Cls != null) {
            HookKit.hookAllByName(w65Cls, "a") { it.setResult(false) }
            log("PayGate: w65.c hooked")
        }
    }
}
