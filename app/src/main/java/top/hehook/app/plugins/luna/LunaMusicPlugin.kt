package top.hehook.app.plugins.luna

import android.app.Application
import android.os.Build
import android.util.Log
import android.view.View
import top.hehook.app.core.BasePlugin
import top.hehook.app.core.HookKit

/**
 * 汽水音乐 (com.luna.music) v1.1.7 —— 基于官方 19.1.0 真实符号重写
 *
 * 全部符号均来自 32-dex / 31.4 万类 APK 的静态解剖验证，
 * 废弃 v1.1.6 中臆测的 AudioPatchAdManager / AudioAdManager /
 * enableRequestAudioPatchAd / hasFreeListenPrivilege 等不存在符号。
 *
 * 关键认知修正：
 * 1. com.luna.common.arch.db.entity.VipStatus 是 5 值枚举
 *    (FREE / FREE_VIP / VIP / SVIP / UNKNOWN_VIP)，不是数据实体；
 *    往里写 isVip/vipType/expireTime 字段全部无效，只能 hook 枚举本身的
 *    isVip()/isSVip()。
 * 2. 真正的播放权益载体是 CommerceInfoPlayEntitlementEntity：
 *    expireAt(到期) / videoCompleteAcquiredTimeMs(看视频所得时长) /
 *    rewardTasksFinished(已完成任务数)，配合 RewardFinishedTaskInfo 的
 *    fullDay / allFull / minutesObtained 构成「看广告换听歌时长」门禁。
 * 3. PlayEntitlementImpl.b() 等价于 RewardFinishedTaskInfo.getFullDay()，
 *    强锁 true 即让客户端判定「今日额度已满」，不再弹激励广告。
 * 4. 移除 PackageBlockInterceptor 的反射 proceed（会破坏 OkHttp 调用链，
 *    导致权益接口请求异常、VIP 状态始终为空——这正是 v1.1.6 无效果的死因之一）。
 */
class LunaMusicPlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    companion object {
        private const val FAR_FUTURE_MS = 4102444800000L    // 2099-12-31
        private const val BIG_INT = 9999
        private const val BIG_MINUTES = 99999

        private const val PKG_ENTITY = "com.luna.biz.entitlement.core.commerceinfo.entity"
        private const val PLAY_ENTITY = "$PKG_ENTITY.CommerceInfoPlayEntitlementEntity"
        private const val REWARD_INFO = "$PKG_ENTITY.RewardFinishedTaskInfo"
        private const val COMMERCE_MEMBER = "$PKG_ENTITY.CommerceInfoMemberShipEntity"

        private const val PLAY_IMPL =
            "com.luna.biz.entitlement.abilities.playentitlement.PlayEntitlementImpl"
        private const val PLAY_CENTER =
            "com.luna.biz.entitlement.abilities.playentitlement.PlayEntitlementCenter"
        private const val MEMBER_IMPL =
            "com.luna.biz.entitlement.abilities.membership.MemberShipCenterImpl"
        private const val MEMBER_CENTER =
            "com.luna.biz.entitlement.abilities.membership.MemberShipCenter"
        private const val VIP_STATUS = "com.luna.common.arch.db.entity.VipStatus"
    }

    override fun handleHook() {
        val curProc = getCurrentProcessName()
        if (curProc != null && curProc != packageName) {
            log("Ignoring non-main process: $curProc")
            return
        }

        log("Loading LunaMusicPlugin v1.1.7 (Real-Symbol Rebuild) for [$packageName]...")

        if (isEnabled("unlock_vip", true)) {
            hookPlayEntitlementEntity()
            hookRewardTaskInfo()
            hookPlayEntitlementImpl()
            hookMemberShipCenter()
            hookVipStatusEnum()
            hookCommerceMemberShip()
        }

        if (isEnabled("remove_visual_ads", true)) {
            hookSplashAd()
        }

        hookPackageBlockState()
    }

    // ── 1. 播放权益实体：到期时间 / 已获时长 / 任务完成数 ──────────────
    private fun hookPlayEntitlementEntity() = safeRun("PlayEntitlementEntity") {
        val cls = HookKit.loadClass(classLoader, PLAY_ENTITY) ?: return@safeRun

        HookKit.hookAllByName(cls, "getExpireAt") { it.setResult(FAR_FUTURE_MS) }
        HookKit.hookAllByName(cls, "getVideoCompleteAcquiredTimeMs") {
            it.setResult(listOf(FAR_FUTURE_MS))
        }
        HookKit.hookAllByName(cls, "getRewardTasksTimeMs") {
            it.setResult(listOf(FAR_FUTURE_MS))
        }
        HookKit.hookAllByName(cls, "getRewardTasksFinished") { it.setResult(BIG_INT) }
        HookKit.hookAllByName(cls, "getNeedRefreshRewardTime") { it.setResult(false) }
        HookKit.hookAllByName(cls, "getDailyRewardTasks") { it.setResult(emptyList<Any>()) }

        log("PlayEntitlementEntity: expireAt/acquiredTime/tasksFinished hooked")
    }

    // ── 2. 奖励任务状态：今日满额 / 已得分钟数 ────────────────────────
    private fun hookRewardTaskInfo() = safeRun("RewardFinishedTaskInfo") {
        val cls = HookKit.loadClass(classLoader, REWARD_INFO) ?: return@safeRun

        HookKit.hookAllByName(cls, "getFullDay") { it.setResult(true) }
        HookKit.hookAllByName(cls, "getAllFull") { it.setResult(true) }
        HookKit.hookAllByName(cls, "getMinutesObtained") { it.setResult(BIG_MINUTES) }
        HookKit.hookAllByName(cls, "getDaysObtained") { it.setResult(BIG_INT) }
        HookKit.hookAllByName(cls, "getDaysFinished") { it.setResult(BIG_INT) }
        HookKit.hookAllByName(cls, "getTasksFinished") { it.setResult(BIG_INT) }
        HookKit.hookAllByName(cls, "getTasksToFullDay") { it.setResult(0) }
        HookKit.hookAllByName(cls, "getEffectTime") { it.setResult(FAR_FUTURE_MS) }

        log("RewardFinishedTaskInfo: fullDay/allFull/minutesObtained hooked")
    }

    // ── 3. 播放权益中心实现（混淆名按 smali 语义精确映射）──────────────
    //    a() Boolean = needRefreshRewardTime
    //    b() Boolean = RewardFinishedTaskInfo.getFullDay()  ← 核心
    //    c() Integer = getDaysObtained()
    //    d() Integer = getRewardTasksFinished()
    //    e() Integer = 另一已得计数
    //    f() Long    = getExpireAt()
    private fun hookPlayEntitlementImpl() = safeRun("PlayEntitlementImpl") {
        for (name in listOf(PLAY_IMPL, PLAY_CENTER)) {
            val cls = HookKit.loadClass(classLoader, name) ?: continue
            hookNoArg(cls, "a", false)
            hookNoArg(cls, "b", true)
            hookNoArg(cls, "c", BIG_INT)
            hookNoArg(cls, "d", BIG_INT)
            hookNoArg(cls, "e", BIG_INT)
            hookNoArg(cls, "f", FAR_FUTURE_MS)
        }
        log("PlayEntitlementImpl: a~f hooked")
    }

    private fun hookNoArg(cls: Class<*>?, methodName: String, value: Any) {
        if (cls == null) return
        try {
            val m = cls.getDeclaredMethod(methodName)
            HookKit.hookBefore(m) { it.setResult(value) }
        } catch (_: Throwable) {
            // 方法不存在则跳过，不制造任何异常
        }
    }

    // ── 4. 会员中心实现：isVip / 层级 / 到期时间 ──────────────────────
    private fun hookMemberShipCenter() = safeRun("MemberShipCenter") {
        val svip = resolveVipStatus("SVIP")

        for (name in listOf(MEMBER_IMPL, MEMBER_CENTER)) {
            val cls = HookKit.loadClass(classLoader, name) ?: continue

            if (svip != null) {
                try {
                    val m = cls.getDeclaredMethod("a")
                    if (m.returnType.name == VIP_STATUS) {
                        HookKit.hookBefore(m) { it.setResult(svip) }
                    }
                } catch (_: Throwable) {}
            }

            HookKit.hookAllByName(cls, "isVip") { it.setResult(true) }
            hookNoArg(cls, "j", true)
            hookNoArg(cls, "k", true)
            hookNoArg(cls, "s", true)
            hookNoArg(cls, "b", FAR_FUTURE_MS)
            hookNoArg(cls, "i", FAR_FUTURE_MS)
        }
        log("MemberShipCenter: isVip=true, tier=SVIP")
    }

    /** 反射取 VipStatus 枚举实例（SVIP / VIP / FREE_VIP） */
    private fun resolveVipStatus(name: String): Any? {
        return try {
            val cls = HookKit.loadClass(classLoader, VIP_STATUS) ?: return null
            cls.getField(name).get(null)
        } catch (_: Throwable) {
            null
        }
    }

    // ── 5. VipStatus 枚举方法 ────────────────────────────────────────
    private fun hookVipStatusEnum() = safeRun("VipStatusEnum") {
        val cls = HookKit.loadClass(classLoader, VIP_STATUS) ?: return@safeRun
        HookKit.hookAllByName(cls, "isVip") { it.setResult(true) }
        HookKit.hookAllByName(cls, "isSVip") { it.setResult(true) }
        log("VipStatus: enum isVip/isSVip locked true")
    }

    // ── 6. 商业化会员实体（真实字段）────────────────────────────────
    private fun hookCommerceMemberShip() = safeRun("CommerceMemberShip") {
        val cls = HookKit.loadClass(classLoader, COMMERCE_MEMBER) ?: return@safeRun

        HookKit.hookAllConstructors(cls) { obj, _ ->
            if (obj != null) {
                HookKit.setField(obj, "isVip", true)
                HookKit.setField(obj, "vipStage", "svip")
                HookKit.setField(obj, "lastMemberShipType", "svip")
                HookKit.setField(obj, "expireTimeMax", FAR_FUTURE_MS)
                HookKit.setField(obj, "inGracePeriod", false)
                HookKit.setField(obj, "isAboutToExpired", false)
            }
        }

        HookKit.hookAllByName(cls, "isVip") { it.setResult(true) }
        HookKit.hookAllByName(cls, "getVipStage") { it.setResult("svip") }
        HookKit.hookAllByName(cls, "getLastMemberShipType") { it.setResult("svip") }
        HookKit.hookAllByName(cls, "getVipLabelSuffix") { it.setResult("") }
        HookKit.hookAllByName(cls, "getModifierVipLabelSuffix") { it.setResult("") }
        HookKit.hookAllByName(cls, "getExpireTimeMax") { it.setResult(FAR_FUTURE_MS) }
        HookKit.hookAllByName(cls, "getInGracePeriod") { it.setResult(false) }
        HookKit.hookAllByName(cls, "isAboutToExpired") { it.setResult(false) }

        log("CommerceMemberShip: isVip/vipStage/expireTimeMax hooked")
    }

    // ── 7. 开屏广告（方法名已静态验证存在）──────────────────────────
    private fun hookSplashAd() = safeRun("SplashAd") {
        val configCls = HookKit.loadClass(
            classLoader,
            "com.luna.common.arch.config.commercial.ad.AdSettingsConfig\$CommonSplashAdConfig"
        )
        if (configCls != null) {
            HookKit.hookAllByName(configCls, "getSwitchShowAd") { it.setResult(false) }
            HookKit.hookAllByName(configCls, "getSwitchShowBrandAd") { it.setResult(false) }
        }

        val compositeViewCls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.ad.adns.brandad.view.SplashCompositeView"
        )
        if (compositeViewCls != null) {
            HookKit.hookAllByName(compositeViewCls, "setShowDuration") { it.setArg(0, 0L) }
            HookKit.hookAllByName(compositeViewCls, "setCountDownTime") { it.setArg(0, 0L) }
            HookKit.hookAllByName(compositeViewCls, "setVisibility") { chain ->
                chain.setArg(0, View.GONE)
            }
        }

        val pangle = HookKit.loadClass(classLoader, "com.bytedance.sdk.openadsdk.TTAdNative")
        if (pangle != null) {
            HookKit.hookAllByName(pangle, "loadSplashAd") { it.skip() }
        }

        log("SplashAd: config + compositeView + Pangle gate hooked")
    }

    // ── 8. PackageBlock 状态放行（只强锁，不再反射 proceed）─────────
    private fun hookPackageBlockState() = safeRun("PackageBlockState") {
        val mainServiceCls = HookKit.loadClass(classLoader, "com.luna.biz.main.MainServiceImpl")
        if (mainServiceCls != null) {
            HookKit.hookAllByName(mainServiceCls, "getPackageBlockState") { it.setResult(false) }
        }

        val delegateCls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.main.init.blockpackage.PackageBlockDelegate"
        )
        if (delegateCls != null) {
            for (m in delegateCls.declaredMethods) {
                if (m.returnType == java.lang.Boolean.TYPE || m.returnType == java.lang.Boolean::class.java) {
                    HookKit.hookBefore(m) { it.setResult(false) }
                }
            }
        }
        log("PackageBlock: state released (no chain reflection)")
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
