package top.hehook.app.plugins.luna

import android.app.Application
import android.os.Build
import android.util.Log
import android.view.View
import top.hehook.app.core.BasePlugin
import top.hehook.app.core.HookKit
import top.hehook.app.core.Trace

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
        /** 服务端「版本被 block」状态码：PackageBlockInterceptor 判定 0xf427e 才弹窗 */
        private const val BLOCK_CODE = 0xf427e

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
        // 用 Log.e 强制可见：LSPosed 日志页默认保留 error 级，避免被过滤漏掉
        Trace.log(tag, "== Luna ENTER == proc=$curProc pkg=$packageName sdk=${Build.VERSION.SDK_INT}")

        if (curProc != null && curProc.isNotEmpty() && curProc != packageName) {
            Trace.log(tag, "== skip non-main process: $curProc ==")
            return
        }

        log("Loading LunaMusicPlugin v1.2.0 (Diag Edition) for [$packageName]...")

        // 0. 安装来源伪装 —— 必须最先执行，晚于客户端自检就会弹「当前版本不安全」
        hookInstallerSource()

        // ── VIP / 权益相关 hook 已整体移除（v1.4.0）──────────────────
        // 实测结论：汽水音乐音源由服务端按账号权益签发。本地改写
        // PlayEntitlement / RewardStage / UserTier 只会让界面判定与服务端
        // 实际授权脱节，表现为「提示开通会员」或播放时「网络错误」。
        // 现版本只保留去广告与反拦截能力，播放行为完全回到官方原版。
        // 相关方法体仍保留在文件内（hookRewardTaskInfo 等），需要时再挂。

        if (isEnabled("remove_visual_ads", true)) {
            hookSplashAd()
        }

        hookPackageBlockState()
        hookPackageBlockStatus()
        hookBlockUiSuppress()
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
        // e() 是 AdRewardController.G() 判定阶段的数据源，拉满即视为「今日奖励已满」
        n += hookExact(cls, "e", "java.lang.Integer", BIG_MINUTES)

        log("PlayEntitlementImpl: $n hooked (b/f)")
    }

    // ── 3.5 广告奖励阶段强锁（免看广告畅听的核心开关）────────────────
    /*
     * 实证链路：
     *   AdRewardController.G() = IPlayEntitlementCenter.e() 判断阶段
     *     0        → AD_REWARD_TIME_NULL
     *     部分     → AD_REWARD_TIME_HALF
     *     满       → AD_REWARD_TIME_FULL
     *   M()/N() 均为 (G() == AD_REWARD_TIME_FULL)
     * 把 G() 直接锁成 FULL，客户端即认为今日广告奖励已领满，不再弹激励广告。
     */
    private fun hookRewardStage() = safeRun("RewardStage") {
        var n = 0

        val fullStage = try {
            val stageCls = HookKit.loadClass(
                classLoader,
                "com.luna.biz.ad.data.reward.RewardStageType\$AD_REWARD_TIME_FULL"
            )
            stageCls?.getField("d")?.get(null)
        } catch (_: Throwable) {
            null
        }

        val ctlCls = HookKit.loadClass(classLoader, "com.luna.biz.ad.controller.AdRewardController")
        if (ctlCls != null) {
            if (fullStage != null) {
                n += hookExact(
                    ctlCls, "G", "com.luna.biz.ad.data.reward.RewardStageType", fullStage
                )
            }
            n += hookTypedFlag(ctlCls, "M", true)
            n += hookTypedFlag(ctlCls, "N", true)
        }

        Trace.log(tag, "RewardStage: $n hooked (stage=" + (fullStage != null) + ")")
    }

    // ── 4. VipStatus 枚举判定 ───────────────────────────────────────
    private fun hookVipStatusEnum() = safeRun("VipStatusEnum") {
        val cls = HookKit.loadClass(classLoader, VIP_STATUS) ?: return@safeRun
        var n = 0
        n += hookTypedFlag(cls, "isVip", true)
        n += hookTypedFlag(cls, "isSVip", true)
        log("VipStatus: $n enum methods hooked")
    }

    // ── 4.5 用户层级总开关：UserTier.T() → svip_user ──────────────────
    /*
     * 实证：MemberShipCenterImpl.isVip() 的实现是
     *   String tier = UserTier.q.T();
     *   if (tier.isEmpty()) return false;
     *   switch (tier.hashCode()) { case "vip_user"/"svip_user"/"slim_vip_user"/"free_vip_user" -> true }
     * 合法取值仅五种：free_user / free_vip_user / slim_vip_user / vip_user / svip_user。
     * 锁成最高档 svip_user，所有依赖 UserTier 的会员判定会一起变真，
     * 比逐个 hook isVip()/j()/k() 可靠得多。
     */
    private fun hookUserTier() = safeRun("UserTier") {
        val cls = HookKit.loadClass(classLoader, "com.luna.common.arch.config.UserTier")
        var n = 0
        if (cls != null) {
            n += hookExact(cls, "T", "java.lang.String", "svip_user")
        }
        Trace.log(tag, "UserTier: $n hooked (tier=svip_user)")
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
        n += hookExact(configCls, "getSwitchShowAd", "int", 0)
        n += hookExact(configCls, "getSwitchShowBrandAd", "int", 0)

        // 冷启动广告策略模式归零（0 = 不走优化模式，直接无广告）
        n += hookExact(configCls, "getColdSplashOptMode", "int", 0)

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

    // ── 0. 安装来源伪装（19.1.0 只查 getInstallerPackageName，无 getInstallSourceInfo）──
    private fun hookInstallerSource() = safeRun("InstallerSource") {
        var n = 0
        val candidates = listOf(
            "android.app.ApplicationPackageManager",
            "android.content.pm.PackageManager"
        )
        for (cname in candidates) {
            val cls = try {
                Class.forName(cname)
            } catch (_: Throwable) {
                continue
            }
            for (m in cls.declaredMethods) {
                if (m.name != "getInstallerPackageName") continue
                if (m.parameterTypes.size != 1) continue
                if (m.parameterTypes[0] != String::class.java) continue
                try {
                    m.isAccessible = true
                    if (HookKit.hookBefore(m) { chain ->
                            val pkg = chain.args.firstOrNull() as? String
                            if (pkg == null || pkg == packageName) {
                                chain.setResult("com.tencent.android.qqdownloader")
                            }
                        }) n++
                } catch (_: Throwable) {}
            }
        }
        Trace.log(tag, "InstallerSource: $n hooked (spoof=qqdownloader)")
    }

    // ── 9. 服务端 block 状态码消毒（核心：1000062 → 0）──────────────
    /*
     * 静态实证：
     *   PackageBlockInterceptor.intercept() 里
     *     if (statusCode == 0xf427e) blockLiveData.postValue(true)
     *   0xf427e = 1000062，是服务端下发的"版本不安全"判定码。
     *   判定在云端，本地只能把这个码在响应体里改掉。
     */
    private fun hookPackageBlockStatus() = safeRun("PackageBlockStatus") {
        var n = 0

        // A. 全局消毒：任何写入 statusCode 的路径，只要值等于判定码就改成 0
        val respCls = HookKit.loadClass(classLoader, "com.luna.common.arch.net.BaseResponseNoKeep")
        if (respCls != null) {
            for (m in respCls.declaredMethods) {
                if (m.name != "setStatusCode") continue
                if (m.parameterTypes.size != 1) continue
                val pt = m.parameterTypes[0]
                if (pt != java.lang.Integer.TYPE && pt != java.lang.Integer::class.java) continue
                if (HookKit.hookBefore(m) { chain ->
                        val v = chain.args.firstOrNull() as? Int
                        if (v == BLOCK_CODE) chain.setArg(0, 0)
                    }) n++
            }
        }

        // B. 判定点兜底：拦截器返回前直接清洗响应体
        val icls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.main.init.blockpackage.PackageBlockInterceptor"
        )
        if (icls != null) {
            for (m in icls.declaredMethods) {
                if (m.name != "intercept" || m.parameterTypes.size != 1) continue
                try {
                    hookMethod(m) { chain ->
                        val resp = chain.proceed()
                        try {
                            val body = resp?.javaClass?.getMethod("body")?.invoke(resp)
                            if (body != null) {
                                val sc = body.javaClass
                                    .getMethod("getStatusCode")
                                    .invoke(body) as? Int
                                if (sc != null && sc != 0) {
                                    Trace.log(tag, "PB intercept code=$sc")
                                }
                                if (sc == BLOCK_CODE) {
                                    body.javaClass
                                        .getMethod("setStatusCode", java.lang.Integer.TYPE)
                                        .invoke(body, 0)
                                    Trace.log(tag, "PB blockCode neutralized -> 0")
                                }
                            }
                        } catch (_: Throwable) {}
                        resp
                    }
                    n++
                } catch (_: Throwable) {}
            }
        }

        Trace.log(tag, "PackageBlockStatus: $n hooked (blockCode=$BLOCK_CODE)")
    }

    // ── 10. UI 层兜底：Observer 收到的 block 布尔值直接改 false ────────
    /*
     * 实证链路：
     *   PackageBlockInterceptor.postValue(true)
     *     → fh.b.onChanged(Object)  { check-cast Boolean; booleanValue() }
     *     → PackageBlockDelegate.e(delegate, Z)  → inflate 遮罩 ViewStub
     * 直接在这两处把布尔值改 false，遮罩永远不 inflate，
     * 不受服务端状态码变化、混淆名变动影响。
     */
    private fun hookBlockUiSuppress() = safeRun("BlockUiSuppress") {
        var n = 0

        // A. 业务侧回调：参数 (PackageBlockDelegate, boolean) 的静态方法
        val dcls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.main.init.blockpackage.PackageBlockDelegate"
        )
        if (dcls != null) {
            for (m in dcls.declaredMethods) {
                if (m.parameterTypes.size != 2) continue
                if (m.parameterTypes[0].name != dcls.name) continue
                val t = m.parameterTypes[1]
                if (t != java.lang.Boolean.TYPE && t != java.lang.Boolean::class.java) continue
                if (HookKit.hookBefore(m) { chain -> chain.setArg(1, false) }) n++
            }
        }

        // B. 混淆 Observer 兜底（19.1.0 实测为 fh.b）
        for (cname in listOf("fh.b", "fh.c")) {
            val obsCls = HookKit.loadClass(classLoader, cname) ?: continue
            for (m in obsCls.declaredMethods) {
                if (m.name != "onChanged" || m.parameterTypes.size != 1) continue
                if (HookKit.hookBefore(m) { chain -> chain.setArg(0, false) }) n++
            }
        }

        Trace.log(tag, "BlockUiSuppress: $n hooked")
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
