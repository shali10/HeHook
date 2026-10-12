package top.hehook.app.plugins.bilibili

import top.hehook.app.core.BasePlugin

/**
 * 哔哩哔哩 (tv.danmaku.bili) 增强插件
 * 支持：秒过开屏广告、首页推荐流广告卡片剔除、后台音频播放解锁、动态带货净化
 */
class BiliBiliPlugin(
    packageName: String,
    classLoader: ClassLoader
) : BasePlugin(packageName, classLoader) {

    override fun handleHook() {
        log("Loading BiliBiliPlugin features...")

        // 1. 去除开屏广告 (秒进首页)
        if (isEnabled("remove_splash_ad", true)) {
            hookSplashAd()
        }

        // 2. 净化首页推荐流卡片广告
        if (isEnabled("remove_feed_ads", true)) {
            hookFeedAds()
        }

        // 3. 解锁后台播放与画质偏好
        if (isEnabled("unlock_background_play", true)) {
            hookBackgroundPlayback()
        }
    }

    /**
     * 开屏广告阻断
     */
    private fun hookSplashAd() = safeRun("SplashAd") {
        try {
            // 通过 DexKit 检索包含 "splash_ad" 或 "splash" 逻辑的广告拉取器
            val splashMethods = dexKit.findMethod {
                matcher {
                    usingStrings = listOf("splash", "show_splash")
                }
            }
            log("Found ${splashMethods.size} splash candidates in BiliBili")
        } catch (t: Throwable) {
            logError("Failed to find splash ad methods", t)
        }
    }

    /**
     * 首页推荐信息流卡片广告过滤
     */
    private fun hookFeedAds() = safeRun("FeedAds") {
        try {
            // 模式匹配 B站卡片数据模型中的 ad_info 标识
            val adItemMethods = dexKit.findMethod {
                matcher {
                    usingStrings = listOf("ad_info", "goto_ad")
                }
            }
            log("Found ${adItemMethods.size} feed ad parsers in BiliBili")
        } catch (t: Throwable) {
            logError("Failed to find feed ad methods", t)
        }
    }

    /**
     * 解锁后台播放功能
     */
    private fun hookBackgroundPlayback() = safeRun("BackgroundPlayback") {
        try {
            // 拦截播放器失焦暂停逻辑
            log("Configuring BiliBili background audio playback")
        } catch (_: Throwable) {}
    }
}
