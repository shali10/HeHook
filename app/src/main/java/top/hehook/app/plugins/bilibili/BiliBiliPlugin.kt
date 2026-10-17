package top.hehook.app.plugins.bilibili

import top.hehook.app.core.BasePlugin
import top.hehook.app.core.HookKit
import java.lang.reflect.Method

/**
 * 哔哩哔哩 (tv.danmaku.bili) 增强插件 v1.1.1
 * 实装：
 * 1. 开屏广告瞬间秒过 (兼容老版 Splash.isValid + 现代 gRPC SplashListReply 彻底斩首 + SplashActivity 直通)
 * 2. 大会员与画质特权注入 (HookVip 官方 VipUserInfo / VipExtraUserInfo 双通道 + 现代 View VipInfo)
 * 3. 首页推荐流广告卡片剔除 (Pegasus bRPC / JSON 双层净化)
 * 4. 视频播放页底栏与卡片广告净化 (ViewReply / CM)
 * 5. 后台播放解锁 (PlayerQualityService)
 */
class BiliBiliPlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    override fun handleHook() {
        log("Loading BiliBiliPlugin v1.1.1...")

        // 1. 去除开屏广告 (秒进首页，保留生日彩蛋)
        if (isEnabled("remove_splash_ad", true)) {
            hookSplashAd()
        }

        // 2. 解锁大会员特权与画质接口 (对标 HookVip，双通道注入)
        if (isEnabled("unlock_vip", true)) {
            hookVipPrivilege()
        }

        // 3. 净化首页推荐流卡片广告
        if (isEnabled("remove_feed_ads", true)) {
            hookFeedAds()
        }

        // 4. 视频详情页卡片与播放器挂载商业广告拦截
        if (isEnabled("remove_feed_ads", true)) {
            hookVideoDetailAds()
        }

        // 5. 解锁后台播放与画质偏好
        if (isEnabled("unlock_background_play", true)) {
            hookBackgroundPlayback()
        }
    }

    /**
     * 大会员特权与画质注入 (HookVip 官方精髓 + 现代 gRPC VipInfo 双通道)
     */
    private fun hookVipPrivilege() = safeRun("VipPrivilege") {
        val expireTime = 113143670061000L // 2099+ 年

        // A. 经典账户信息库 VipUserInfo
        val vipUserCls = HookKit.loadClass(classLoader, "com.bilibili.lib.accountinfo.model.VipUserInfo")
        if (vipUserCls != null) {
            HookKit.hookAllByName(vipUserCls, "isEffectiveVip") { it.setResult(true) }
            HookKit.hookAllByName(vipUserCls, "getVipType") { it.setResult(2) } // 2: 年度大会员
            HookKit.hookAllByName(vipUserCls, "getVipStatus") { it.setResult(1) } // 1: 正常
            HookKit.hookAllByName(vipUserCls, "getVipDueDate") { it.setResult(expireTime) }
            log("VipPrivilege: VipUserInfo hooked (isEffectiveVip -> true)")
        }

        // B. 经典账户扩展信息库 VipExtraUserInfo (HookVip 核心)
        val vipExtraCls = HookKit.loadClass(classLoader, "com.bilibili.lib.accountinfo.model.VipExtraUserInfo")
        if (vipExtraCls != null) {
            HookKit.hookAllByName(vipExtraCls, "isEffectiveVip") { it.setResult(true) }
            HookKit.hookAllByName(vipExtraCls, "getVipType") { it.setResult(2) }
            HookKit.hookAllByName(vipExtraCls, "getVipStatus") { it.setResult(1) }
            HookKit.hookAllByName(vipExtraCls, "getVipDueDate") { it.setResult(expireTime) }
            log("VipPrivilege: VipExtraUserInfo hooked (isEffectiveVip -> true)")
        }

        // C. 现代视频流 ViewReply 专属 VipInfo (放行 1080P60 / 4K / 杜比视界)
        val viewVipClasses = listOf(
            "com.bapis.bilibili.app.view.v1.VipInfo",
            "com.bapis.bilibili.app.viewunite.v1.VipInfo"
        )
        for (cname in viewVipClasses) {
            val cls = HookKit.loadClass(classLoader, cname) ?: continue
            HookKit.hookAllByName(cls, "getStatus") { it.setResult(1) }
            HookKit.hookAllByName(cls, "getType") { it.setResult(2) }
            HookKit.hookAllByName(cls, "getDueDate") { it.setResult(expireTime) }
            log("VipPrivilege: $cname hooked")
        }
    }

    /**
     * 开屏广告瞬间秒过 (老版 isValid + 现代 gRPC SplashListReply 斩首 + 展示 Activity 阻断)
     */
    private fun hookSplashAd() = safeRun("SplashAd") {
        // A. 现代 gRPC 开屏回包斩首 (直接让客户端拿到 0 广告、0 秒等待)
        val splashReplyCls = HookKit.loadClass(classLoader, "com.bapis.bilibili.app.splash.v1.SplashListReply")
        if (splashReplyCls != null) {
            HookKit.hookAllByName(splashReplyCls, "getShowList") { it.setResult(emptyList<Any>()) }
            HookKit.hookAllByName(splashReplyCls, "getShowCount") { it.setResult(0) }
            HookKit.hookAllByName(splashReplyCls, "getMaxWaitSec") { it.setResult(0) }
            HookKit.hookAllByName(splashReplyCls, "hasShow") { it.setResult(false) }
            log("SplashAd: gRPC SplashListReply zeroed out (no ads, 0s wait)")
        }

        // B. 经典 Splash.isValid 判定 (HookVip 兼容老版，同时保留生日彩蛋)
        val splashCls = HookKit.loadClass(classLoader, "tv.danmaku.bili.ui.splash.ad.model.Splash")
        if (splashCls != null) {
            var isBirthMethod: Method? = null
            try {
                isBirthMethod = splashCls.getDeclaredMethod("isBirthSplash")
                isBirthMethod.isAccessible = true
            } catch (_: Throwable) {}

            HookKit.hookAllByName(splashCls, "isValid") { chain ->
                val thisObj = chain.thisObject
                var isBirth = false
                if (thisObj != null && isBirthMethod != null) {
                    try {
                        isBirth = isBirthMethod.invoke(thisObj) == true
                    } catch (_: Throwable) {}
                }
                if (!isBirth) {
                    chain.setResult(false)
                }
            }
            log("SplashAd: Splash.isValid hooked -> false (keeps birthday splash)")
        }

        // C. 开屏展示 Activity 与展示门禁
        val splashActCls = HookKit.loadClass(classLoader, "tv.danmaku.bili.ui.splash.SplashActivity")
        if (splashActCls != null) {
            HookKit.hookAllByName(splashActCls, "showAd") { it.skip() }
            HookKit.hookAllByName(splashActCls, "checkAd") { it.skip() }
        }

        val splashMgrCls = HookKit.loadClass(classLoader, "tv.danmaku.bili.ui.splash.ad.SplashAdManager")
        if (splashMgrCls != null) {
            HookKit.hookAllByName(splashMgrCls, "isAdAvailable") { it.setResult(false) }
            HookKit.hookAllByName(splashMgrCls, "canShow") { it.setResult(false) }
        }
    }

    /**
     * 首页推荐信息流卡片广告过滤 (Protobuf / bRPC 拦截)
     */
    private fun hookFeedAds() = safeRun("FeedAds") {
        // A. bRPC 响应列表卡片过滤
        val converterCls = HookKit.loadClass(classLoader, "com.bilibili.pegasus.utils.BrpcRespConverterKt")
        val adItemCls = HookKit.loadClass(classLoader, "com.bilibili.pegasus.api.modelv2.AdItem")
        if (converterCls != null) {
            HookKit.hookAllByName(converterCls, "a") { chain ->
                val list = chain.args.firstOrNull() as? List<*>
                if (list != null) {
                    val filtered = list.filterNotNull().filter { item ->
                        !isBiliAdCard(item)
                    }
                    chain.setArg(0, filtered)
                }
            }
            HookKit.hookAllAfterByName(converterCls, "a") { _, result, _ ->
                if (result is List<*> && adItemCls != null) {
                    val filtered = result.filterNotNull().filter { it.javaClass != adItemCls }
                }
            }
            log("FeedAds: BrpcRespConverterKt#a card ad filter installed")
        }

        // B. 经典 JSON 流卡片广告拦截
        val parserCls = HookKit.loadClass(classLoader, "com.bilibili.pegasus.api.BaseTMApiParser")
        if (parserCls != null) {
            HookKit.hookAllByName(parserCls, "e") { chain ->
                val raw = chain.args.firstOrNull()
                if (raw is List<*>) {
                    val filtered = raw.filterNotNull().filter { item ->
                        !isJsonAdItem(item)
                    }
                    chain.setArg(0, filtered)
                }
            }
            log("FeedAds: BaseTMApiParser#e raw JSON ad filter installed")
        }
    }

    /**
     * 视频详情页与播放器下方广告拦截
     */
    private fun hookVideoDetailAds() = safeRun("VideoDetailAds") {
        // A. ViewReply 播放响应
        val viewReplyCls = HookKit.loadClass(classLoader, "com.bapis.bilibili.app.view.v1.ViewReply")
        if (viewReplyCls != null) {
            HookKit.hookAllByName(viewReplyCls, "getCmsList") { it.setResult(emptyList<Any>()) }
            HookKit.hookAllByName(viewReplyCls, "getCmsCount") { it.setResult(0) }
            HookKit.hookAllByName(viewReplyCls, "hasCmUnderPlayer") { it.setResult(false) }
            log("VideoDetailAds: ViewReply CM ads hooked")
        }

        // B. CM 广告组件
        val cmCls = HookKit.loadClass(classLoader, "com.bapis.bilibili.app.viewunite.v1.CM")
        if (cmCls != null) {
            HookKit.hookAllByName(cmCls, "hasCmUnderPlayer") { it.setResult(false) }
        }
    }

    /**
     * 解锁后台播放与画质偏好
     */
    private fun hookBackgroundPlayback() = safeRun("BackgroundPlayback") {
        val playerQualityCls = HookKit.loadClass(classLoader, "com.bilibili.playerbizcommon.features.quality.PlayerQualityService")
        if (playerQualityCls != null) {
            HookKit.hookAllByName(playerQualityCls, "isBackgroundPlayEnable") { it.setResult(true) }
            HookKit.hookAllByName(playerQualityCls, "canBackgroundPlay") { it.setResult(true) }
            log("BackgroundPlayback: PlayerQualityService hooked")
        }
    }

    private fun isBiliAdCard(item: Any): Boolean {
        try {
            var m = item.javaClass.methods.firstOrNull { it.name == "getItemCase" }
            if (m != null) {
                val caseVal = m.invoke(item)
                val caseName = caseVal?.toString() ?: ""
                if (caseName.contains("AD_") || caseName.contains("_AD") || caseName == "CM") {
                    return true
                }
            }
            m = item.javaClass.methods.firstOrNull { it.name == "hasCm" }
            if (m != null && m.invoke(item) == true) {
                return true
            }
            m = item.javaClass.methods.firstOrNull { it.name == "getCardGoto" }
            if (m != null) {
                val cg = m.invoke(item)?.toString() ?: ""
                if (cg.contains("ad") || cg.contains("banner") || cg.contains("cm")) {
                    return true
                }
            }
            m = item.javaClass.methods.firstOrNull { it.name == "getGoto" }
            if (m != null && m.invoke(item)?.toString() == "ad") {
                return true
            }
        } catch (_: Throwable) {}
        return false
    }

    private fun isJsonAdItem(item: Any): Boolean {
        try {
            val str = item.toString()
            if (str.contains("\"goto\":\"ad\"") || str.contains("\"ad_info\"")) {
                return true
            }
        } catch (_: Throwable) {}
        return false
    }
}
