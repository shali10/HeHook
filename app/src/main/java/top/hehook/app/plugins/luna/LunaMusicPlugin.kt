package top.hehook.app.plugins.luna

import android.app.Application
import android.os.Build
import android.util.Log
import top.hehook.app.core.BasePlugin
import top.hehook.app.core.HookKit
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * 汽水音乐 (com.luna.music) 商业化权益全解锁与全域去广告插件 v1.1.3
 * 
 * 融合 MoonHook 核心架构精髓 + 纯本地零云控单机复刻：
 * 1. 严格主进程隔离 (Process Gate)：仅注入主进程 com.luna.music，坚决避开 :ad 与沙盒进程，彻底杜绝黑屏与状态错乱
 * 2. 商业化会员实体强锁 (CommerceInfoMemberShipEntity)：伪装 SVIP、isVip=true、阶段锁死 svip
 * 3. 商业化 JSON 权益直注 (CommerceInfoRepo)：地毯式斩断 needShowAd / inspireAdInfo(激励广告)
 * 4. 权益与下载配额全放行 (ConsumeBenefitResponse / DownloadItem / CheckDownloadResponse)：配额 999999，有效期至 2099 年
 * 5. 切歌音频插播广告与音频贴片彻底阻断 (AudioPatchAd)
 * 6. 穿山甲开屏广告根源跳过 (Pangle loadSplashAd) + 官方商店安装源伪装
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
        // 核心第 0 步：主进程安全门禁（避开黑屏的核心铁律）
        val curProc = getCurrentProcessName()
        if (curProc != null && curProc != packageName) {
            log("Ignoring non-main process: $curProc (avoids crash/black-screen)")
            return
        }

        log("Loading LunaMusicPlugin v1.1.3 (Commerce Entitlement Engine) for [$packageName]...")

        // 1. 伪装正规应用市场安装渠道
        hookInstallerSource()

        // 2. 商业化会员实体强锁 (CommerceInfoMemberShipEntity + 兼容老版 VipStatus)
        if (isEnabled("unlock_vip", true)) {
            hookCommerceMemberShip()
            hookCommerceInfoRepo()
            hookBenefitAndDownloadQuotas()
            hookLegacyVipStatus()
        }

        // 3. 拦截音频插播与切歌广告 (Audio Patch Ad)
        if (isEnabled("remove_audio_ads", true)) {
            hookAudioPatchAds()
        }

        // 4. 全域视觉广告与开屏广告阻断 (零黑屏、无损直通)
        if (isEnabled("remove_visual_ads", true)) {
            hookAdGateAndSplash()
        }

        // 5. 禁用字节 Reparo 在线热修复
        if (isEnabled("disable_hotfix", true)) {
            hookDisableReparo()
        }
    }

    /**
     * 获取当前进程名（兼顾各 Android API 版本）
     */
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

    /**
     * 伪装正规应用市场安装渠道 (绕过第三方来源安全自检)
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
     * 核心 1：汽水音乐当前最新商业化会员实体强锁
     */
    private fun hookCommerceMemberShip() = safeRun("CommerceMemberShip") {
        val entityCls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.entitlement.core.commerceinfo.entity.CommerceInfoMemberShipEntity"
        )
        if (entityCls != null) {
            HookKit.hookAllByName(entityCls, "isVip") { it.setResult(true) }
            HookKit.hookAllByName(entityCls, "getVipStage") { it.setResult("svip") }
            HookKit.hookAllByName(entityCls, "getLastMemberShipType") { it.setResult("svip") }
            HookKit.hookAllByName(entityCls, "getVipLabelSuffix") { it.setResult("") }
            HookKit.hookAllByName(entityCls, "getModifierVipLabelSuffix") { it.setResult("") }
            log("CommerceMemberShip: CommerceInfoMemberShipEntity SVIP locked successfully")
        } else {
            log("CommerceMemberShip: CommerceInfoMemberShipEntity not found in current dex")
        }
    }

    /**
     * 核心 2：商业化仓库 JSON 回包拦截 (彻底清零激励广告与 needShowAd)
     */
    private fun hookCommerceInfoRepo() = safeRun("CommerceInfoRepo") {
        val repoCls = HookKit.loadClass(
            classLoader,
            "com.luna.biz.entitlement.core.commerceinfo.core.CommerceInfoRepo"
        )
        if (repoCls != null) {
            for (m in repoCls.declaredMethods) {
                // 拦截入参带 String 的解析方法
                val hasStringParam = m.parameterTypes.any { it == String::class.java }
                if (hasStringParam) {
                    HookKit.hookAfter(m) { _, result, chain ->
                        if (result is String) {
                            chain.setResult(sanitizeAndInjectCommerceJson(result))
                        } else if (result is ByteArray) {
                            val originalStr = String(result, StandardCharsets.UTF_8)
                            val modifiedStr = sanitizeAndInjectCommerceJson(originalStr)
                            chain.setResult(modifiedStr.toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                }
            }
            log("CommerceInfoRepo: Injected CommerceInfo JSON sanitizers")
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
            // 正则兜底快速替换关键广告标记
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
            log("BenefitAndDownloadQuotas: Hooked $cname methods")
        }
    }

    /**
     * 兼容老版 VipStatus 实体
     */
    private fun hookLegacyVipStatus() = safeRun("LegacyVipStatus") {
        val vipStatusCls = HookKit.loadClass(classLoader, "com.luna.common.arch.db.entity.VipStatus")
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
     * 广告位置总闸 (AdGate) 与开屏广告阻断 (杜绝黑屏)
     */
    private fun hookAdGateAndSplash() = safeRun("AdGateAndSplash") {
        // A. 穿山甲 SDK (Pangle) 开屏广告全量跳过
        val pangleClasses = listOf(
            "com.bytedance.sdk.openadsdk.TTAdNative",
            "com.bytedance.msdk.api.v2.ad.splash.GMSplashAd"
        )
        for (cname in pangleClasses) {
            val cls = HookKit.loadClass(classLoader, cname) ?: continue
            HookKit.hookAllByName(cls, "loadSplashAd") { it.skip() }
            HookKit.hookAllByName(cls, "loadAd") { it.skip() }
        }

        // B. 字节通用广告总闸与可用性
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

        // C. 开屏展示层仅跳过广告视图绘制，绝不暴力 finish Activity（避免黑屏）
        val splashClasses = listOf(
            "com.luna.biz.splash.SplashActivity",
            "com.luna.music.splash.SplashActivity",
            "com.ss.android.ugc.aweme.splash.SplashActivity",
            "com.dragon.read.ad.openingscreenad.OpeningScreenADActivity"
        )
        for (cname in splashClasses) {
            val cls = HookKit.loadClass(classLoader, cname) ?: continue
            HookKit.hookAllByName(cls, "showAd") { it.skip() }
            HookKit.hookAllByName(cls, "showNaturalAdView") { it.skip() }
            HookKit.hookAllByName(cls, "showBrandAdView") { it.skip() }
            HookKit.hookAllByName(cls, "showImcSplashView") { it.skip() }
            HookKit.hookAllByName(cls, "checkAd") { it.skip() }
        }
    }

    /**
     * 禁用字节在线热修复 Reparo (防止下发补丁对抗)
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
}
