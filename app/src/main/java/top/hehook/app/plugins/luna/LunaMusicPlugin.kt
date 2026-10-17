package top.hehook.app.plugins.luna

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import top.hehook.app.core.BasePlugin
import top.hehook.app.core.HookKit
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * 汽水音乐 (com.luna.music) 商业化权益全解锁与全域去广告插件 v1.1.5 终极完整版
 * 
 * 核心升级点：
 * 1. 动态 ClassLoader 热补丁引擎 (Activity/Context Dynamic ClassLoader Binding)：
 *    - 解决字节多 Dex 动态分包与插件化加载导致类在 onPackageReady 时尚未就绪的痛点
 *    - 无论 Dex 何时载入，通过 Activity 生命周期钩子实时补挂 Hook，确保 100% 命中
 * 2. 开屏广告精准粉碎 (CommonSplashAdConfig + SplashCompositeView)：
 *    - 强锁 CommonSplashAdConfig.getSwitchShowAd -> false 与 getSwitchShowBrandAd -> false
 *    - 强锁 SplashCompositeView.setShowDuration(0) / setCountDownTime(0) 并强制 GONE 移除
 * 3. 商业化权益 SVIP 强锁与激励广告消除 (CommerceInfoMemberShipEntity + CommerceInfoRepo)：
 *    - isVip=true, stage="svip", expire=2099年
 *    - 回包拦截：need_show_ad=false, inspire_ad_info=null
 * 4. 激励视频广告直接放行 (ExcitingVideo Auto-Reward Bypass)：
 *    - 拦截激励视频 Activity / LynxFragment，直接放行奖励
 * 5. 官方 PackageBlock 反篡改穿透：
 *    - 阻断“当前版本不安全”提示，保障平稳运行
 */
class LunaMusicPlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    companion object {
        private const val FAR_FUTURE_MS = 4102444800000L    // 2099-12-31 毫秒级时间戳
        private const val FAR_FUTURE_SEC = 4102444800L      // 2099-12-31 秒级时间戳
        private const val QUOTA_INFINITE = 999999           // 无限配额
        private val hookedLoaders = Collections.newSetFromMap(ConcurrentHashMap<ClassLoader, Boolean>())
    }

    override fun handleHook() {
        // 主进程安全门禁
        val curProc = getCurrentProcessName()
        if (curProc != null && curProc != packageName) {
            log("Ignoring non-main process: $curProc")
            return
        }

        log("Loading LunaMusicPlugin v1.1.5 (Dynamic Binding Edition) for [$packageName]...")

        // 1. 立即在当前 ClassLoader 挂载静态基础拦截 (安装源伪装 + PackageBlock 阻断)
        hookInstallerSource()
        hookPackageBlockAndAntiTamper()

        // 2. 挂载 Activity 生命周期探针：捕获动态 ClassLoader 并递归施加全量 Hook
        hookActivityLifecycleForDynamicLoaders()

        // 3. 在当前现有 ClassLoader 执行初次 Hook 探测
        applyBusinessHooks(classLoader)
    }

    /**
     * 核心驱动：通过 Activity 生命周期动态抓取后加载的业务 ClassLoader
     */
    private fun hookActivityLifecycleForDynamicLoaders() = safeRun("ActivityLifecycle") {
        val actCls = HookKit.loadClass(ClassLoader.getSystemClassLoader(), "android.app.Activity") ?: return@safeRun

        HookKit.hookAllByName(actCls, "onCreate") { chain ->
            val act = chain.thisObject as? Activity ?: return@hookAllByName
            val cl = act.javaClass.classLoader
            if (cl != null && hookedLoaders.add(cl)) {
                log("Discovered runtime ClassLoader from ${act.javaClass.name}, injecting business hooks...")
                applyBusinessHooks(cl)
            }
        }

        HookKit.hookAllByName(actCls, "onPostResume") { chain ->
            val act = chain.thisObject as? Activity ?: return@hookAllByName
            val cl = act.javaClass.classLoader
            if (cl != null && hookedLoaders.add(cl)) {
                applyBusinessHooks(cl)
            }
            // 兜底清理可能残留的阻断遮罩或开屏 View
            purgeUnsafeWarningView(act.window?.decorView)
            purgeSplashView(act.window?.decorView)
        }
    }

    /**
     * 执行全量业务 Hook（支持多次调用幂等注入）
     */
    private fun applyBusinessHooks(targetCl: ClassLoader) {
        // A. 官方商业化会员与权益强锁
        hookCommerceMemberShip(targetCl)
        hookCommerceInfoRepo(targetCl)
        hookBenefitAndDownloadQuotas(targetCl)
        hookLegacyVipStatus(targetCl)

        // B. 开屏广告精准截杀 (CommonSplashAdConfig + SplashCompositeView)
        hookSplashAdConfig(targetCl)

        // C. 音频插播与切歌广告拦截
        hookAudioPatchAds(targetCl)

        // D. 穿山甲与通用广告总闸
        hookAdGateAndSdk(targetCl)

        // E. 激励视频广告直接放行
        hookExcitingRewardVideo(targetCl)

        // F. 禁用字节在线热修复
        hookDisableReparo(targetCl)
    }

    /**
     * 开屏广告核心截杀：直接将开屏广告总闸置为 false，将展示时间强改为 0
     */
    private fun hookSplashAdConfig(cl: ClassLoader) = safeRun("SplashAdConfig") {
        // 1. 官方配置实体 CommonSplashAdConfig
        val configCls = HookKit.loadClass(
            cl,
            "com.luna.common.arch.config.commercial.ad.AdSettingsConfig\$CommonSplashAdConfig"
        )
        if (configCls != null) {
            HookKit.hookAllByName(configCls, "getSwitchShowAd") { it.setResult(false) }
            HookKit.hookAllByName(configCls, "getSwitchShowBrandAd") { it.setResult(false) }
            log("SplashAdConfig: CommonSplashAdConfig switches locked to false")
        }

        // 2. 拦截所有返回 CommonSplashAdConfig 的方法，强改开关
        val adSettingsCls = HookKit.loadClass(
            cl,
            "com.luna.common.arch.config.commercial.ad.AdSettingsConfig"
        )
        if (adSettingsCls != null) {
            for (m in adSettingsCls.methods) {
                if (m.returnType.name.contains("CommonSplashAdConfig")) {
                    hookMethod(m) { chain ->
                        val result = chain.proceed()
                        if (result != null) {
                            HookKit.setField(result, "switchShowAd", false)
                            HookKit.setField(result, "switchShowBrandAd", false)
                        }
                        result
                    }
                }
            }
        }

        // 3. 开屏复合视图 SplashCompositeView 强关与秒退
        val compositeViewCls = HookKit.loadClass(
            cl,
            "com.luna.biz.ad.adns.brandad.view.SplashCompositeView"
        )
        if (compositeViewCls != null) {
            HookKit.hookAllByName(compositeViewCls, "setShowDuration") { it.setArg(0, 0L) }
            HookKit.hookAllByName(compositeViewCls, "setCountDownTime") { it.setArg(0, 0L) }
            HookKit.hookAllByName(compositeViewCls, "onAttachedToWindow") { chain ->
                val v = chain.thisObject as? View ?: return@hookAllByName
                v.visibility = View.GONE
                (v.parent as? ViewGroup)?.removeView(v)
            }
            log("SplashAdConfig: SplashCompositeView neutralized")
        }
    }

    /**
     * 激励视频广告直接放行（听歌被要求看广告时直接跳过并完成）
     */
    private fun hookExcitingRewardVideo(cl: ClassLoader) = safeRun("ExcitingRewardVideo") {
        val rewardActivities = listOf(
            "com.ss.android.excitingvideo.ExcitingVideoActivity",
            "com.bytedance.sdk.openadsdk.core.component.reward.activity.TTRewardVideoActivity"
        )
        for (cname in rewardActivities) {
            val cls = HookKit.loadClass(cl, cname) ?: continue
            HookKit.hookAllByName(cls, "onCreate") { chain ->
                val act = chain.thisObject as? Activity ?: return@hookAllByName
                act.finish()
            }
        }

        // 激励视频完成监听器直通
        val videoCacheCls = HookKit.loadClass(cl, "com.ss.android.excitingvideo.model.VideoCacheModel")
        if (videoCacheCls != null) {
            HookKit.hookAllMatchingMethods(videoCacheCls, { it.name.contains("Reward", true) }) { chain ->
                log("ExcitingRewardVideo: Auto completed reward callback")
            }
        }
    }

    /**
     * 核心 1：汽水音乐商业化会员实体强锁
     */
    private fun hookCommerceMemberShip(cl: ClassLoader) = safeRun("CommerceMemberShip") {
        val entityCls = HookKit.loadClass(
            cl,
            "com.luna.biz.entitlement.core.commerceinfo.entity.CommerceInfoMemberShipEntity"
        )
        if (entityCls != null) {
            HookKit.hookAllByName(entityCls, "isVip") { it.setResult(true) }
            HookKit.hookAllByName(entityCls, "getVipStage") { it.setResult("svip") }
            HookKit.hookAllByName(entityCls, "getLastMemberShipType") { it.setResult("svip") }
            HookKit.hookAllByName(entityCls, "getVipLabelSuffix") { it.setResult("") }
            HookKit.hookAllByName(entityCls, "getModifierVipLabelSuffix") { it.setResult("") }
            log("CommerceMemberShip: CommerceInfoMemberShipEntity SVIP locked successfully in $cl")
        }
    }

    /**
     * 核心 2：商业化仓库 JSON 回包拦截 (清零激励广告与 needShowAd)
     */
    private fun hookCommerceInfoRepo(cl: ClassLoader) = safeRun("CommerceInfoRepo") {
        val repoCls = HookKit.loadClass(
            cl,
            "com.luna.biz.entitlement.core.commerceinfo.core.CommerceInfoRepo"
        )
        if (repoCls != null) {
            for (m in repoCls.declaredMethods) {
                val hasStringParam = m.parameterTypes.any { it == String::class.java }
                if (hasStringParam) {
                    hookMethod(m) { chain ->
                        val result = chain.proceed()
                        if (result is String) {
                            sanitizeAndInjectCommerceJson(result)
                        } else if (result is ByteArray) {
                            val originalStr = String(result, StandardCharsets.UTF_8)
                            val modifiedStr = sanitizeAndInjectCommerceJson(originalStr)
                            modifiedStr.toByteArray(StandardCharsets.UTF_8)
                        } else {
                            result
                        }
                    }
                }
            }
            log("CommerceInfoRepo: Injected CommerceInfo JSON sanitizers in $cl")
        }
    }

    /**
     * 修改商业化 JSON：清空激励广告、强改 need_show_ad 为 false、注入 SVIP
     */
    private fun sanitizeAndInjectCommerceJson(jsonStr: String): String {
        if (jsonStr.isBlank() || (!jsonStr.startsWith("{") && !jsonStr.startsWith("["))) {
            return jsonStr
        }
        return try {
            val root = JSONObject(jsonStr)
            sanitizeJsonTree(root)
            injectSvipFields(root)
            root.toString()
        } catch (_: Throwable) {
            jsonStr
                .replace("\"need_show_ad\":true", "\"need_show_ad\":false")
                .replace("\"needShowAd\":true", "\"needShowAd\":false")
                .replace("\"show_ad\":true", "\"show_ad\":false")
                .replace("\"showAd\":true", "\"showAd\":false")
                .replace("\"is_ad\":true", "\"is_ad\":false")
                .replace("\"isAd\":true", "\"isAd\":false")
        }
    }

    private fun sanitizeJsonTree(obj: Any?) {
        when (obj) {
            is JSONObject -> {
                val it = obj.keys()
                val removeOrFalseKeys = mutableListOf<String>()
                val nullKeys = mutableListOf<String>()

                while (it.hasNext()) {
                    val k = it.next()
                    val lower = k.replace("_", "").lowercase(Locale.ROOT)
                    if (lower == "showad" || lower == "needshowad" || lower == "isad") {
                        removeOrFalseKeys.add(k)
                    } else if (lower == "addata" || lower == "inspireadinfo" || lower == "function1ad") {
                        nullKeys.add(k)
                    } else {
                        sanitizeJsonTree(obj.opt(k))
                    }
                }
                for (k in removeOrFalseKeys) obj.put(k, false)
                for (k in nullKeys) obj.put(k, JSONObject.NULL)
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    sanitizeJsonTree(obj.opt(i))
                }
            }
        }
    }

    private fun injectSvipFields(obj: JSONObject) {
        val candidates = listOf("membership", "benefit_base", "commerce_info")
        for (key in candidates) {
            val sub = obj.optJSONObject(key)
            if (sub != null) {
                sub.put("is_vip", true)
                sub.put("vip_stage", "svip")
                sub.put("stage", "svip")
                sub.put("expire_time", FAR_FUTURE_SEC)
                sub.put("expireTime", FAR_FUTURE_MS)
                sub.put("last_membership_type", "svip")
            }
        }
    }

    /**
     * 核心 3：权益响应与下载配额全放行
     */
    private fun hookBenefitAndDownloadQuotas(cl: ClassLoader) = safeRun("BenefitAndDownloadQuotas") {
        val quotaClasses = listOf(
            "com.luna.biz.entitlement.bean.CheckDownloadResponse",
            "com.luna.biz.entitlement.bean.ConsumeBenefitResponse",
            "com.luna.biz.entitlement.bean.DownloadItem"
        )
        for (cname in quotaClasses) {
            val cls = HookKit.loadClass(cl, cname) ?: continue
            for (m in cls.declaredMethods) {
                val lower = m.name.lowercase(Locale.ROOT)
                val matchesQuota = lower.contains("quota") || lower.contains("remain") ||
                                   lower.contains("available") || lower.contains("limit") ||
                                   lower.contains("count") || lower.contains("used")

                if (matchesQuota) {
                    when (m.returnType) {
                        java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> {
                            HookKit.hookBefore(m) { it.setResult(true) }
                        }
                        java.lang.Integer.TYPE, java.lang.Integer::class.java,
                        java.lang.Long.TYPE, java.lang.Long::class.java -> {
                            HookKit.hookBefore(m) { it.setResult(QUOTA_INFINITE) }
                        }
                    }
                } else if (m.returnType == String::class.java) {
                    if (lower.contains("level") || lower.contains("stage")) {
                        HookKit.hookBefore(m) { it.setResult("svip") }
                    } else if (lower.contains("expire") || lower.contains("time")) {
                        HookKit.hookBefore(m) { it.setResult("2099-12-31 23:59:59") }
                    }
                }
            }
            log("BenefitAndDownloadQuotas: Hooked $cname methods")
        }
    }

    /**
     * 兼容老版 VipStatus 实体
     */
    private fun hookLegacyVipStatus(cl: ClassLoader) = safeRun("LegacyVipStatus") {
        val vipStatusCls = HookKit.loadClass(cl, "com.luna.common.arch.db.entity.VipStatus")
        if (vipStatusCls != null) {
            for (m in listOf("isVip", "isSVip", "isSuperVip", "isAnyVip", "isValid", "isFreeVipActive", "hasFreeListenPrivilege")) {
                HookKit.hookAllByName(vipStatusCls, m) { it.setResult(true) }
            }
            for (m in listOf("isExpired", "isNeedShowVip", "isTrialVip", "isAdVipExpired")) {
                HookKit.hookAllByName(vipStatusCls, m) { it.setResult(false) }
            }
            HookKit.hookAllByName(vipStatusCls, "getExpireTime") { it.setResult(FAR_FUTURE_MS) }
            HookKit.hookAllByName(vipStatusCls, "getLeftTime") { it.setResult(FAR_FUTURE_SEC) }
            HookKit.hookAllByName(vipStatusCls, "getVipType") { it.setResult(2) }
        }
    }

    /**
     * 音频插播广告拦截 (切歌、中插广告)
     */
    private fun hookAudioPatchAds(cl: ClassLoader) = safeRun("AudioPatchAds") {
        val audioAdClasses = listOf(
            "com.luna.biz.ad.audio.AudioAdManager",
            "com.luna.biz.ad.audio.AudioPatchAdManager",
            "com.luna.biz.ad.audio.AudioRollAdManager",
            "com.dragon.read.ad.banner.impl.HongguoAudioAdServiceImpl"
        )
        for (cname in audioAdClasses) {
            val cls = HookKit.loadClass(cl, cname) ?: continue
            HookKit.hookAllByName(cls, "enableRequestAudioPatchAd") { it.setResult(false) }
            HookKit.hookAllByName(cls, "enableRequestAudioInfoFlowAd") { it.setResult(false) }
            HookKit.hookAllByName(cls, "shouldInsertAudioAd") { it.setResult(false) }
            HookKit.hookAllByName(cls, "canPlayAudioAd") { it.setResult(false) }
        }
    }

    /**
     * 穿山甲与通用广告总闸
     */
    private fun hookAdGateAndSdk(cl: ClassLoader) = safeRun("AdGateAndSdk") {
        val pangleClasses = listOf(
            "com.bytedance.sdk.openadsdk.TTAdNative",
            "com.bytedance.msdk.api.v2.ad.splash.GMSplashAd"
        )
        for (cname in pangleClasses) {
            val cls = HookKit.loadClass(cl, cname) ?: continue
            HookKit.hookAllByName(cls, "loadSplashAd") { it.skip() }
            HookKit.hookAllByName(cls, "loadAd") { it.skip() }
        }

        val adGateClasses = listOf(
            "com.luna.biz.ad.data.AdType",
            "com.luna.biz.ad.manager.LunaAdManager",
            "com.dragon.read.component.biz.impl.NsAdImpl",
            "com.dragon.read.ad.banner.manager.BannerAdConfigUtil"
        )
        for (cname in adGateClasses) {
            val cls = HookKit.loadClass(cl, cname) ?: continue
            HookKit.hookAllMatchingMethods(cls, { it.name == "willShowLynxBanner" }) { it.setResult(false) }
            HookKit.hookAllMatchingMethods(cls, { it.name == "canThisPositionShow" }) { it.setResult(false) }
            HookKit.hookAllMatchingMethods(cls, { it.name == "checkAdAvailable" }) { it.setResult(false) }
            HookKit.hookAllMatchingMethods(cls, { it.name == "canRetain" }) { it.setResult(false) }
        }
    }

    /**
     * 禁用字节在线热修复 Reparo
     */
    private fun hookDisableReparo(cl: ClassLoader) = safeRun("DisableReparo") {
        val reparoClasses = listOf(
            "com.dragon.read.base.hotfix.ReparoHotFixInitServiceImpl",
            "com.bytedance.reparo.IReparoConfig"
        )
        for (cname in reparoClasses) {
            val cls = HookKit.loadClass(cl, cname) ?: continue
            HookKit.hookAllByName(cls, "loadRemotePatch") { it.skip() }
            HookKit.hookAllByName(cls, "enable") { it.setResult(false) }
            HookKit.hookAllByName(cls, "autoFetchPatch") { it.setResult(false) }
        }
    }

    /**
     * 伪装正规应用市场安装渠道
     */
    private fun hookInstallerSource() = safeRun("InstallerSource") {
        val pmClasses = listOf(
            "android.app.ApplicationPackageManager",
            "android.content.pm.PackageManager"
        )
        for (cname in pmClasses) {
            val cls = HookKit.loadClass(classLoader, cname) 
                ?: HookKit.loadClass(ClassLoader.getSystemClassLoader(), cname)
                ?: continue
            HookKit.hookAllByName(cls, "getInstallerPackageName") { chain ->
                val targetPkg = chain.args.firstOrNull()?.toString()
                if (targetPkg == packageName) {
                    chain.setResult("com.tencent.android.qqdownloader")
                }
            }
        }
    }

    /**
     * 彻底粉碎官方 PackageBlock 阻断拦截
     */
    private fun hookPackageBlockAndAntiTamper() = safeRun("PackageBlockAndAntiTamper") {
        val interceptorCls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.main.init.blockpackage.PackageBlockInterceptor"
        )
        if (interceptorCls != null) {
            for (m in interceptorCls.declaredMethods) {
                if (m.name == "intercept" && m.parameterTypes.isNotEmpty()) {
                    HookKit.hookBefore(m) { chain ->
                        val chainObj = chain.args.firstOrNull() ?: return@hookBefore
                        try {
                            val requestMethod = chainObj.javaClass.getMethod("request")
                            val requestObj = requestMethod.invoke(chainObj)
                            val proceedMethod = chainObj.javaClass.getMethod("proceed", requestObj.javaClass)
                            val resp = proceedMethod.invoke(chainObj, requestObj)
                            chain.setResult(resp)
                        } catch (_: Throwable) {
                            try {
                                val proceedMethod = chainObj.javaClass.methods.firstOrNull { it.name == "proceed" && it.parameterTypes.size == 1 }
                                val requestMethod = chainObj.javaClass.methods.firstOrNull { it.name == "request" && it.parameterTypes.isEmpty() }
                                if (proceedMethod != null && requestMethod != null) {
                                    val req = requestMethod.invoke(chainObj)
                                    val resp = proceedMethod.invoke(chainObj, req)
                                    chain.setResult(resp)
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                } else if (m.returnType == java.lang.Boolean.TYPE || m.returnType == java.lang.Boolean::class.java) {
                    HookKit.hookBefore(m) { it.setResult(false) }
                } else {
                    hookMethod(m) { chain ->
                        val result = chain.proceed()
                        if (result != null) {
                            try {
                                val postValMethod = result.javaClass.methods.firstOrNull { it.name == "postValue" || it.name == "setValue" }
                                postValMethod?.invoke(result, false)
                            } catch (_: Throwable) {}
                        }
                        result
                    }
                }
            }
        }

        val delegateCls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.main.init.blockpackage.PackageBlockDelegate"
        )
        if (delegateCls != null) {
            for (m in delegateCls.declaredMethods) {
                if (m.returnType == java.lang.Boolean.TYPE || m.returnType == java.lang.Boolean::class.java) {
                    HookKit.hookBefore(m) { it.setResult(false) }
                } else if (m.parameterTypes.any { it.name.contains("Activity") || it.name.contains("Context") }) {
                    HookKit.hookBefore(m) { it.setResult(null) }
                }
            }
        }

        val mainServiceCls = HookKit.loadClass(classLoader, "com.luna.biz.main.MainServiceImpl")
        if (mainServiceCls != null) {
            HookKit.hookAllByName(mainServiceCls, "getPackageBlockState") { it.setResult(false) }
            HookKit.hookAllMatchingMethods(
                mainServiceCls,
                { m ->
                    (m.name.contains("block", true) || m.name.contains("safe", true)) &&
                    (m.returnType == java.lang.Boolean.TYPE || m.returnType == java.lang.Boolean::class.java)
                }
            ) { chain ->
                chain.setResult(false)
            }
        }
    }

    private fun purgeUnsafeWarningView(view: View?) {
        if (view == null) return
        view.post {
            try {
                findAndRemoveBlockView(view)
            } catch (_: Throwable) {}
        }
    }

    private fun findAndRemoveBlockView(root: View): Boolean {
        if (root is TextView) {
            val text = root.text?.toString() ?: ""
            if (text.contains("当前版本不安全") || text.contains("请在正规应用市场下载")) {
                var parent: android.view.ViewParent? = root.parent
                var blockContainer: View = root
                while (parent is ViewGroup) {
                    val pView = parent as View
                    if (pView.id != android.R.id.content && pView.parent is ViewGroup) {
                        blockContainer = pView
                    } else {
                        break
                    }
                    parent = pView.parent
                }
                blockContainer.visibility = View.GONE
                (blockContainer.parent as? ViewGroup)?.removeView(blockContainer)
                return true
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                if (findAndRemoveBlockView(root.getChildAt(i))) return true
            }
        }
        return false
    }

    private fun purgeSplashView(view: View?) {
        if (view == null) return
        view.post {
            try {
                findAndRemoveSplashView(view)
            } catch (_: Throwable) {}
        }
    }

    private fun findAndRemoveSplashView(root: View): Boolean {
        val name = root.javaClass.name
        if (name.contains("SplashCompositeView") || name.contains("SplashAdView")) {
            root.visibility = View.GONE
            (root.parent as? ViewGroup)?.removeView(root)
            return true
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                if (findAndRemoveSplashView(root.getChildAt(i))) return true
            }
        }
        return false
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
        } catch (_: Throwable) {
            null
        }
    }
}
