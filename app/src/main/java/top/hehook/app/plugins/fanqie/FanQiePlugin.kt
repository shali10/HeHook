package top.hehook.app.plugins.fanqie

import top.hehook.app.core.BasePlugin
import top.hehook.app.core.ByteDanceReadEngine
import top.hehook.app.core.HookKit

/**
 * 番茄免费小说 (com.dragon.read) 增强插件
 * 接入 ByteDanceReadEngine 全套核心：AdGate 总闸、PrivilegeManager VIP 全穿透、沉浸阅读免广、横幅拦截与开屏秒进
 * 独家追加：高品质 AI 听书音色全解锁、金币悬浮挂件与福利红点净化
 */
class FanQiePlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    override fun handleHook() {
        log("Loading FanQiePlugin v1.1.0...")

        // 1. 挂载字节阅读平台通用引擎 (AdGate 广告总闸 + PrivilegeManager VIP + 沉浸免广 + 横幅清屏)
        ByteDanceReadEngine(this, isHongGuo = false).installAll()

        // 2. 解锁高品质 AI 朗读听书音色
        if (isEnabled("unlock_tts_timbre", true)) {
            hookTtsTimbre()
        }

        // 3. 隐藏金币红包悬浮挂件与活动弹窗
        if (isEnabled("hide_floating_pendant", true)) {
            hookHidePendant()
        }
    }

    /**
     * 解锁听书高品质 AI 朗读音色限制 (纯反射零 SO 依赖)
     */
    private fun hookTtsTimbre() = safeRun("TtsTimbre") {
        val ttsClasses = listOf(
            "com.dragon.read.reader.tts.model.TTSVoice",
            "com.dragon.read.reader.tts.model.TtsToneModel",
            "com.dragon.read.reader.tts.manager.TTSVoiceManager",
            "com.dragon.read.component.biz.impl.NsVipImpl"
        )
        for (name in ttsClasses) {
            val cls = HookKit.loadClass(classLoader, name)
            if (cls != null) {
                HookKit.hookAllByName(cls, "isVipTimbre") { it.setResult(false) }
                HookKit.hookAllByName(cls, "isPayVoice") { it.setResult(false) }
                HookKit.hookAllByName(cls, "needUnlock") { it.setResult(false) }
                HookKit.hookAllByName(cls, "isToneVip") { it.setResult(false) }
            }
        }
        log("TtsTimbre: VIP timbre unlocks hooked")
    }

    /**
     * 隐藏悬浮金币挂件、活动福袋与主界面赚钱Tab
     */
    private fun hookHidePendant() = safeRun("HidePendant") {
        // A. 悬浮挂件分发服务
        val pendantCls = HookKit.loadClass(classLoader, "com.bytedance.ug.sdk.novel.pendant.PlanPendantServiceImpl")
        if (pendantCls != null) {
            HookKit.hookAllByName(pendantCls, "triggerEvent") { it.skip() }
            HookKit.hookAllByName(pendantCls, "showPendant") { it.skip() }
        }

        // B. 金币挂件展示入口
        val f1Cls = HookKit.loadClass(classLoader, "com.dragon.read.goldcoinbox.pendant.video.f1")
        if (f1Cls != null) {
            HookKit.hookAllByName(f1Cls, "show") { it.skip() }
        }

        // C. 主页底部赚钱/福利 Tab 隐藏
        val mainTabCls = HookKit.loadClass(classLoader, "com.dragon.read.pages.main.m")
        if (mainTabCls != null) {
            HookKit.hookAllByName(mainTabCls, "b") { it.skip() }
        }
        log("HidePendant: Floating pendant and reward tabs suppressed")
    }
}
