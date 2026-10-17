package top.hehook.app.plugins.luna

import android.app.Application
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import top.hehook.app.core.BasePlugin
import top.hehook.app.core.HookKit
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * 汽水音乐 (com.luna.music) 商业化权益全解锁与全域去广告插件 v1.1.6 稳定纯净版
 * 
 * 稳健原则（彻底根治闪退）：
 * 1. 物理移除所有全局 Activity.onCreate/onPostResume 拦截，杜绝主线程生命周期干扰
 * 2. 物理移除 onAttachedToWindow 中的 removeView 破坏性布局修改，杜绝 ConcurrentModification 崩溃
 * 3. 构造器参数劫持 + Getter 强锁：双管齐下兼顾反射与字段直读
 * 4. 商业化权益双引擎：同时覆盖新版 CommerceInfo 架构与经典 VipStatus 架构
 * 5. 开屏广告精准静默：拦截 CommonSplashAdConfig 与穿山甲总闸，不碰底层 Window
 * 6. 官方 PackageBlock 阻断放行，消除“当前版本不安全”提示
 */
class LunaMusicPlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    companion object {
        private const val FAR_FUTURE_MS = 4102444800000L    // 2099-12-31 毫秒级时间戳
        private const val FAR_FUTURE_SEC = 4102444800L      // 2099-12-31 秒级时间戳
        private const val QUOTA_INFINITE = 999999           // 无限配额
    }

    override fun handleHook() {
        val curProc = getCurrentProcessName()
        if (curProc != null && curProc != packageName) {
            log("Ignoring non-main process: $curProc")
            return
        }

        log("Loading LunaMusicPlugin v1.1.6 (Ultra Stable Edition) for [$packageName]...")

        // 1. 安装来源伪装 (应用宝)
        hookInstallerSource()

        // 2. 官方 PackageBlock 反篡改穿透
        hookPackageBlockAndAntiTamper()

        // 3. 核心 VIP 会员双引擎强锁 (VipStatus 构造器劫持 + CommerceInfo)
        if (isEnabled("unlock_vip", true)) {
            hookVipStatusEntity()
            hookCommerceMemberShip()
            hookCommerceInfoRepo()
            hookBenefitAndDownloadQuotas()
        }

        // 4. 开屏广告总闸静默拦截 (CommonSplashAdConfig + Pangle SDK)
        if (isEnabled("remove_visual_ads", true)) {
            hookSplashAd()
            hookAdGateAndSdk()
        }

        // 5. 切歌与音频插播广告拦截
        if (isEnabled("remove_audio_ads", true)) {
            hookAudioPatchAds()
        }

        // 6. 禁用字节在线热修复
        if (isEnabled("disable_hotfix", true)) {
            hookDisableReparo()
        }
    }

    /**
     * 核心 1：经典 VipStatus 实体构造器劫持与 Getter 强锁 (双保险)
     */
    private fun hookVipStatusEntity() = safeRun("VipStatusEntity") {
        val vipStatusCls = HookKit.loadClass(classLoader, "com.luna.common.arch.db.entity.VipStatus")
        if (vipStatusCls != null) {
            // A. 构造器劫持：一旦实例化直接修改其私有/公有字段
            HookKit.hookAllConstructors(vipStatusCls) { chain ->
                val obj = chain.thisObject ?: return@hookAllConstructors
                HookKit.setField(obj, "isVip", true)
                HookKit.setField(obj, "isSVip", true)
                HookKit.setField(obj, "isSuperVip", true)
                HookKit.setField(obj, "vipType", 2) // 2: SVIP
                HookKit.setField(obj, "expireTime", FAR_FUTURE_MS)
                HookKit.setField(obj, "leftTime", FAR_FUTURE_SEC)
            }

            // B. Getter 方法拦截
            for (m in listOf("isVip", "isSVip", "isSuperVip", "isAnyVip", "isValid", "isFreeVipActive", "hasFreeListenPrivilege")) {
                HookKit.hookAllByName(vipStatusCls, m) { it.setResult(true) }
            }
            for (m in listOf("isExpired", "isNeedShowVip", "isTrialVip", "isAdVipExpired")) {
                HookKit.hookAllByName(vipStatusCls, m) { it.setResult(false) }
            }
            HookKit.hookAllByName(vipStatusCls, "getExpireTime") { it.setResult(FAR_FUTURE_MS) }
            HookKit.hookAllByName(vipStatusCls, "getLeftTime") { it.setResult(FAR_FUTURE_SEC) }
            HookKit.hookAllByName(vipStatusCls, "getVipType") { it.setResult(2) }
            log("VipStatusEntity: Hooked VipStatus entity successfully")
        }

        // 补充业务层 VipUser / VipInfo
        val extraVipClasses = listOf(
            "com.luna.biz.vip.model.VipInfo",
            "com.luna.biz.vip.data.VipUser",
            "com.luna.biz.vip.data.VipStatusData",
            "com.luna.biz.user.model.VipUserInfo"
        )
        for (cname in extraVipClasses) {
            val cls = HookKit.loadClass(classLoader, cname) ?: continue
            HookKit.hookAllByName(cls, "isVip") { it.setResult(true) }
            HookKit.hookAllByName(cls, "isSVip") { it.setResult(true) }
            HookKit.hookAllByName(cls, "getExpireTime") { it.setResult(FAR_FUTURE_MS) }
            HookKit.hookAllByName(cls, "getVipType") { it.setResult(2) }
        }
    }

    /**
     * 核心 2：新版商业化会员实体强锁 (CommerceInfoMemberShipEntity)
     */
    private fun hookCommerceMemberShip() = safeRun("CommerceMemberShip") {
        val entityCls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.entitlement.core.commerceinfo.entity.CommerceInfoMemberShipEntity"
        )
        if (entityCls != null) {
            HookKit.hookAllConstructors(entityCls) { chain ->
                val obj = chain.thisObject ?: return@hookAllConstructors
                HookKit.setField(obj, "isVip", true)
                HookKit.setField(obj, "vipStage", "svip")
                HookKit.setField(obj, "lastMemberShipType", "svip")
            }
            HookKit.hookAllByName(entityCls, "isVip") { it.setResult(true) }
            HookKit.hookAllByName(entityCls, "getVipStage") { it.setResult("svip") }
            HookKit.hookAllByName(entityCls, "getLastMemberShipType") { it.setResult("svip") }
            HookKit.hookAllByName(entityCls, "getVipLabelSuffix") { it.setResult("") }
            HookKit.hookAllByName(entityCls, "getModifierVipLabelSuffix") { it.setResult("") }
            log("CommerceMemberShip: Hooked CommerceInfoMemberShipEntity")
        }
    }

    /**
     * 核心 3：商业化仓库 JSON 回包拦截 (清零激励广告与 needShowAd)
     */
    private fun hookCommerceInfoRepo() = safeRun("CommerceInfoRepo") {
        val repoCls = HookKit.loadClass(
            classLoader,
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
            log("CommerceInfoRepo: Hooked CommerceInfoRepo")
        }
    }

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
     * 核心 4：权益响应与下载配额全放行
     */
    private fun hookBenefitAndDownloadQuotas() = safeRun("BenefitAndDownloadQuotas") {
        val quotaClasses = listOf(
            "com.luna.biz.entitlement.bean.CheckDownloadResponse",
            "com.luna.biz.entitlement.bean.ConsumeBenefitResponse",
            "com.luna.biz.entitlement.bean.DownloadItem"
        )
        for (cname in quotaClasses) {
            val cls = HookKit.loadClass(classLoader, cname) ?: continue
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
        }
    }

    /**
     * 核心 5：开屏广告静默拦截 (CommonSplashAdConfig + SplashCompositeView)
     */
    private fun hookSplashAd() = safeRun("SplashAd") {
        // A. 开屏配置开关
        val configCls = HookKit.loadClass(
            classLoader,
            "com.luna.common.arch.config.commercial.ad.AdSettingsConfig\$CommonSplashAdConfig"
        )
        if (configCls != null) {
            HookKit.hookAllByName(configCls, "getSwitchShowAd") { it.setResult(false) }
            HookKit.hookAllByName(configCls, "getSwitchShowBrandAd") { it.setResult(false) }
        }

        // B. 拦截所有返回 CommonSplashAdConfig 的方法，强锁字段
        val adSettingsCls = HookKit.loadClass(
            classLoader,
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

        // C. 开屏视图时长清零与隐藏 (绝对不在 onAttachedToWindow 暴力 removeView，只设 GONE)
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
    }

    /**
     * 音频插播与切歌广告拦截
     */
    private fun hookAudioPatchAds() = safeRun("AudioPatchAds") {
        val audioAdClasses = listOf(
            "com.luna.biz.ad.audio.AudioAdManager",
            "com.luna.biz.ad.audio.AudioPatchAdManager",
            "com.luna.biz.ad.audio.AudioRollAdManager",
            "com.dragon.read.ad.banner.impl.HongguoAudioAdServiceImpl"
        )
        for (cname in audioAdClasses) {
            val cls = HookKit.loadClass(classLoader, cname) ?: continue
            HookKit.hookAllByName(cls, "enableRequestAudioPatchAd") { it.setResult(false) }
            HookKit.hookAllByName(cls, "enableRequestAudioInfoFlowAd") { it.setResult(false) }
            HookKit.hookAllByName(cls, "shouldInsertAudioAd") { it.setResult(false) }
            HookKit.hookAllByName(cls, "canPlayAudioAd") { it.setResult(false) }
        }
    }

    /**
     * 穿山甲与通用广告总闸
     */
    private fun hookAdGateAndSdk() = safeRun("AdGateAndSdk") {
        val pangleClasses = listOf(
            "com.bytedance.sdk.openadsdk.TTAdNative",
            "com.bytedance.msdk.api.v2.ad.splash.GMSplashAd"
        )
        for (cname in pangleClasses) {
            val cls = HookKit.loadClass(classLoader, cname) ?: continue
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
            val cls = HookKit.loadClass(classLoader, cname) ?: continue
            HookKit.hookAllMatchingMethods(cls, { it.name == "willShowLynxBanner" }) { it.setResult(false) }
            HookKit.hookAllMatchingMethods(cls, { it.name == "canThisPositionShow" }) { it.setResult(false) }
            HookKit.hookAllMatchingMethods(cls, { it.name == "checkAdAvailable" }) { it.setResult(false) }
            HookKit.hookAllMatchingMethods(cls, { it.name == "canRetain" }) { it.setResult(false) }
        }
    }

    /**
     * 禁用字节在线热修复 Reparo
     */
    private fun hookDisableReparo() = safeRun("DisableReparo") {
        val reparoClasses = listOf(
            "com.dragon.read.base.hotfix.ReparoHotFixInitServiceImpl",
            "com.bytedance.reparo.IReparoConfig"
        )
        for (cname in reparoClasses) {
            val cls = HookKit.loadClass(classLoader, cname) ?: continue
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
     * 官方 PackageBlock 阻断拦截穿透
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
