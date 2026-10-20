package top.hehook.app.plugins.luna

import android.app.Application
import android.os.Build
import android.util.Log
import android.view.View
import top.hehook.app.core.BasePlugin
import top.hehook.app.core.HookKit

/**
 * 汽水音乐 (com.luna.music) v1.1.8 —— 类型守卫保守版
 *
 * v1.1.7 闪退根因分析（不靠猜，逐条排除）：
 * a) Xposed 在 hook 返回处的类型不匹配崩溃发生在框架层拆箱/校验，
 *    不受回调内 try-catch 保护 —— 因此每个 setResult 必须先校验真实返回类型；
 * b) getTasksToFullDay 被置 0，业务侧一旦做「已完成 / 满额所需」除法即 ArithmeticException；
 * c) getDailyRewardTasks 被清空成空列表，下游取 [0] 即 IndexOutOfBounds；
 * d) MemberShipCenterImpl.b()/i() 语义未确证（疑似服务器时间/剩余秒数），
 *    强设 2099 会让倒计时算出巨大负数，故本版全部移除；
 * e) PlayEntitlementCenter 属接口委托包装，签名未逐条确证，本版只留 Impl。
 *
 * 保留的全部是「返回类型已确证 + 语义已由 smali 反查确认」的安全项。
 */
class LunaMusicPlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    companion object {
        private const val FAR_FUTURE_MS = 4102444800000L
        private const val BIG_INT = 9999
        private const val BIG_MINUTES = 99999

        private const val PKG_ENTITY = "com.luna.biz.entitlement.core.commerceinfo.entity"
        private const val PLAY_ENTITY = "$PKG_ENTITY.CommerceInfoPlayEntitlementEntity"
        private const val REWARD_INFO = "$PKG_ENTITY.RewardFinishedTaskInfo"
        private const val COMMERCE_MEMBER = "$PKG_ENTITY.CommerceInfoMemberShipEntity"

        private const val PLAY_IMPL =
            "com.luna.biz.entitlement.abilities.playentitlement.PlayEntitlementImpl"
        private const val MEMBER_IMPL =
            "com.luna.biz.entitlement.abilities.membership.MemberShipCenterImpl"
        private const val VIP_STATUS = "com.luna.common.arch.db.entity.VipStatus"
    }

    override fun handleHook() {
        val curProc = getCurrentProcessName()
        if (curProc != null && curProc != packageName) {
            log("Ignoring non-main process: $curProc")
            return
        }

        log("Loading LunaMusicPlugin v1.1.8 (Type-Guard Edition) for [$packageName]...")

        if (isEnabled("unlock_vip", true)) {
            hookRewardTaskInfo()
            hookPlayEntitlementEntity()
            hookPlayEntitlementImpl()
            hookVipStatusEnum()
            hookMemberShipVip()
            hookCommerceMemberShip()
        }

        if (isEnabled("remove_visual_ads", true)) {
            hookSplashAd()
        }

        hookPackageBlockState()
    }

    // ── 类型守卫工具：按真实返回类型给值，签名不符直接跳过 ──────────────
    private fun hookTypedFlag(cls: Class<*>?, methodName: String, boolVal: Boolean): Int {
        if (cls == null) return 0
        var n = 0
        try {
            for (m in cls.declaredMethods) {
                if (m.name != methodName || m.parameterTypes.isNotEmpty()) continue
                val rt = m.returnType
                val v: Any = when {
                    rt == java.lang.Boolean.TYPE || rt == java.lang.Boolean::class.java -> boolVal
                    rt == java.lang.Integer.TYPE || rt == java.lang.Integer::class.java ->
                        if (boolVal) BIG_INT else 0
                    rt == java.lang.Long.TYPE || rt == java.lang.Long::class.java ->
                        if (boolVal) FAR_FUTURE_MS else 0L
                    else -> continue
                }
                if (HookKit.hookBefore(m) { it.setResult(v) }) n++
            }
        } catch (_: Throwable) {}
        return n
    }

    /** 精确返回类型强锁：returnType.name 必须完全等于 expected 才挂 */
    private fun hookExact(cls: Class<*>?, methodName: String, expected: String, value: Any): Int {
        if (cls == null) return 0
        var n = 0
        try {
            for (m in cls.declaredMethods) {
                if (m.name != methodName) continue
                if (m.parameterTypes.isNotEmpty()) continue
                if (m.returnType.name != expected) continue
                if (HookKit.hookBefore(m) { it.setResult(value) }) n++
            }
        } catch (_: Throwable) {}
        return n
    }

    // ── 1. 奖励任务状态 ─────────────────────────────────────────────
    private fun hookRewardTaskInfo() = safeRun("RewardFinishedTaskInfo") {
        val cls = HookKit.loadClass(classLoader, REWARD_INFO) ?: return@safeRun

        var n = 0
        n += hookExact(cls, "getFullDay", "java.lang.Boolean", true)
        n += hookExact(cls, "getAllFull", "java.lang.Boolean", true)
        n += hookExact(cls, "getMinutesObtained", "java.lang.Integer", BIG_MINUTES)
        n += hookExact(cls, "getDaysObtained", "java.lang.Integer", BIG_INT)
        n += hookExact(cls, "getTasksFinished", "java.lang.Integer", BIG_INT)

        log("RewardFinishedTaskInfo: $n hooked")
    }

    // ── 2. 播放权益实体 ─────────────────────────────────────────────
    private fun hookPlayEntitlementEntity() = safeRun("PlayEntitlementEntity") {
        val cls = HookKit.loadClass(classLoader, PLAY_ENTITY) ?: return@safeRun

        var n = 0
        n += hookExact(cls, "getExpireAt", "java.lang.Long", FAR_FUTURE_MS)
        n += hookExact(
            cls, "getVideoCompleteAcquiredTimeMs", "java.util.List",
            listOf(FAR_FUTURE_MS)
        )
        n += hookExact(cls, "getRewardTasksFinished", "java.lang.Integer", BIG_INT)
        n += hookExact(cls, "getNeedRefreshRewardTime", "java.lang.Boolean", false)

        log("PlayEntitlementEntity: $n hooked")
    }

    // ── 3. 播放权益实现：只碰 b()（=getFullDay）与 f()（=getExpireAt）──
    private fun hookPlayEntitlementImpl() = safeRun("PlayEntitlementImpl") {
        val cls = HookKit.loadClass(classLoader, PLAY_IMPL) ?: return@safeRun

        var n = 0
        n += hookExact(cls, "b", "java.lang.Boolean", true)
        n += hookExact(cls, "f", "java.lang.Long", FAR_FUTURE_MS)

        log("PlayEntitlementImpl: $n hooked (b/f)")
    }

    // ── 4. VipStatus 枚举判定 ───────────────────────────────────────
    private fun hookVipStatusEnum() = safeRun("VipStatusEnum") {
        val cls = HookKit.loadClass(classLoader, VIP_STATUS) ?: return@safeRun
        var n = 0
        n += hookTypedFlag(cls, "isVip", true)
        n += hookTypedFlag(cls, "isSVip", true)
        log("VipStatus: $n enum methods hooked")
    }

    // ── 5. 会员中心：只锁 isVip() 与 a()（返回 VipStatus）─────────────
    private fun hookMemberShipVip() = safeRun("MemberShipCenter") {
        val cls = HookKit.loadClass(classLoader, MEMBER_IMPL) ?: return@safeRun

        var n = 0
        n += hookTypedFlag(cls, "isVip", true)

        val svip = resolveVipStatus("SVIP")
        if (svip != null) {
            n += hookExact(cls, "a", VIP_STATUS, svip)
        }

        log("MemberShipCenter: $n hooked (isVip / a=SVIP)")
    }

    private fun resolveVipStatus(name: String): Any? {
        return try {
            val cls = HookKit.loadClass(classLoader, VIP_STATUS) ?: return null
            cls.getField(name).get(null)
        } catch (_: Throwable) {
            null
        }
    }

    // ── 6. 商业化会员实体（v1.1.6 已验证不崩）────────────────────────
    private fun hookCommerceMemberShip() = safeRun("CommerceMemberShip") {
        val cls = HookKit.loadClass(classLoader, COMMERCE_MEMBER) ?: return@safeRun

        HookKit.hookAllConstructors(cls) { obj, _ ->
            if (obj != null) {
                HookKit.setField(obj, "isVip", true)
                HookKit.setField(obj, "vipStage", "svip")
                HookKit.setField(obj, "lastMemberShipType", "svip")
                HookKit.setField(obj, "inGracePeriod", false)
                HookKit.setField(obj, "isAboutToExpired", false)
            }
        }

        var n = 0
        n += hookExact(cls, "isVip", "java.lang.Boolean", true)
        n += hookExact(cls, "getVipStage", "java.lang.String", "svip")
        n += hookExact(cls, "getLastMemberShipType", "java.lang.String", "svip")
        n += hookExact(cls, "getVipLabelSuffix", "java.lang.String", "")
        n += hookExact(cls, "getModifierVipLabelSuffix", "java.lang.String", "")
        n += hookExact(cls, "getInGracePeriod", "java.lang.Boolean", false)
        n += hookExact(cls, "isAboutToExpired", "java.lang.Boolean", false)

        log("CommerceMemberShip: $n hooked")
    }

    // ── 7. 开屏广告 ─────────────────────────────────────────────────
    private fun hookSplashAd() = safeRun("SplashAd") {
        var n = 0
        val configCls = HookKit.loadClass(
            classLoader,
            "com.luna.common.arch.config.commercial.ad.AdSettingsConfig\$CommonSplashAdConfig"
        )
        n += hookExact(configCls, "getSwitchShowAd", "java.lang.Boolean", false)
        n += hookExact(configCls, "getSwitchShowBrandAd", "java.lang.Boolean", false)

        val compositeViewCls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.ad.adns.brandad.view.SplashCompositeView"
        )
        if (compositeViewCls != null) {
            for (m in compositeViewCls.declaredMethods) {
                if (m.name != "setShowDuration" && m.name != "setCountDownTime") continue
                if (m.parameterTypes.size != 1) continue
                if (m.parameterTypes[0] != java.lang.Long.TYPE && m.parameterTypes[0] != java.lang.Long::class.java) continue
                if (HookKit.hookBefore(m) { it.setArg(0, 0L) }) n++
            }
        }

        val pangle = HookKit.loadClass(classLoader, "com.bytedance.sdk.openadsdk.TTAdNative")
        if (pangle != null) {
            n += hookTypedFlag(pangle, "loadSplashAd", false)
        }

        log("SplashAd: $n hooked")
    }

    // ── 8. PackageBlock 状态放行 ────────────────────────────────────
    private fun hookPackageBlockState() = safeRun("PackageBlockState") {
        val mainServiceCls = HookKit.loadClass(classLoader, "com.luna.biz.main.MainServiceImpl")
        var n = 0
        n += hookTypedFlag(mainServiceCls, "getPackageBlockState", false)

        val delegateCls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.main.init.blockpackage.PackageBlockDelegate"
        )
        if (delegateCls != null) {
            for (m in delegateCls.declaredMethods) {
                if (m.returnType == java.lang.Boolean.TYPE || m.returnType == java.lang.Boolean::class.java) {
                    if (HookKit.hookBefore(m) { it.setResult(false) }) n++
                }
            }
        }
        log("PackageBlockState: $n hooked")
    }

    private fun getCurrentProcessName(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                val atCls = Class.forName("android.app.ActivityThread")
                val m = atCls.getDeclaredMethod("currentProcessName")
                m.isAccessible = true
                m.invoke(null) as? String
            }
        } catch (t: Throwable) {
            Log.w(tag, "getCurrentProcessName failed: ${t.message}")
            null
        }
    }
}
