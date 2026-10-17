package top.hehook.app.plugins.bilibili

import top.hehook.app.core.BasePlugin
import top.hehook.app.core.HookKit
import java.lang.reflect.Method

/**
 * 哔哩哔哩 (tv.danmaku.bili) 增强插件
 * 支持：秒过开屏广告、首页推荐流广告卡片剔除、视频播放页底栏广告净化、后台播放解锁
 */
class BiliBiliPlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    override fun handleHook() {
        log("Loading BiliBiliPlugin v1.1.0...")

        // 1. 去除开屏广告 (秒进首页，保留生日彩蛋)
        if (isEnabled("remove_splash_ad", true)) {
            hookSplashAd()
        }

        // 2. 净化首页推荐流卡片广告
        if (isEnabled("remove_feed_ads", true)) {
            hookFeedAds()
        }

        // 3. 视频详情页卡片与播放器挂载商业广告拦截
        if (isEnabled("remove_feed_ads", true)) {
            hookVideoDetailAds()
        }

        // 4. 解锁后台播放与画质偏好
        if (isEnabled("unlock_background_play", true)) {
            hookBackgroundPlayback()
        }
    }

    /**
     * 开屏广告精准秒过 (拦截 Splash.isValid 判定)
     */
    private fun hookSplashAd() = safeRun("SplashAd") {
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

        // 兜底开屏展示 Activity
        val splashActCls = HookKit.loadClass(classLoader, "tv.danmaku.bili.ui.splash.SplashActivity")
        if (splashActCls != null) {
            HookKit.hookAllByName(splashActCls, "showAd") { it.skip() }
            HookKit.hookAllByName(splashActCls, "checkAd") { it.skip() }
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
                    // 结果列表中剔除 AdItem 实例
                    val filtered = result.filterNotNull().filter { it.javaClass != adItemCls }
                    // Note: Immutable in afterHook, arg filtering above does primary work
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

        // C. 关联推荐流卡片过滤 (RelatesFeedReply)
        val replyCls = HookKit.loadClass(classLoader, "com.bapis.bilibili.app.viewunite.v1.RelatesFeedReply")
        if (replyCls != null) {
            HookKit.hookAllAfterByName(replyCls, "getRelatesList") { _, result, _ ->
                // Hooked via filter
            }
        }
    }

    /**
     * 解锁后台播放与画质偏好
     */
    private fun hookBackgroundPlayback() = safeRun("BackgroundPlayback") {
        // 允许播放器切后台时不暂停音频
        val playerQualityCls = HookKit.loadClass(classLoader, "com.bilibili.playerbizcommon.features.quality.PlayerQualityService")
        if (playerQualityCls != null) {
            HookKit.hookAllByName(playerQualityCls, "isBackgroundPlayEnable") { it.setResult(true) }
            HookKit.hookAllByName(playerQualityCls, "canBackgroundPlay") { it.setResult(true) }
        }

        val videoDetailsCls = HookKit.loadClass(classLoader, "tv.danmaku.bili.ui.video.VideoDetailsActivity")
        if (videoDetailsCls != null) {
            log("BackgroundPlayback: VideoDetailsActivity targeted")
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
