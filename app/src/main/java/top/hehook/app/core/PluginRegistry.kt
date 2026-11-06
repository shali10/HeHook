package top.hehook.app.core

import top.hehook.app.R
import top.hehook.app.plugins.bilibili.BiliBiliPlugin
import top.hehook.app.plugins.fanqie.FanQiePlugin
import top.hehook.app.plugins.hongguo.HongGuoPlugin
import top.hehook.app.plugins.luna.LunaMusicPlugin

/**
 * 单个增强开关（管理端渲染 + 插件侧 isEnabled 读取的唯一来源）
 */
data class Feature(
    val key: String,
    val title: String,
    val desc: String,
    val default: Boolean = true
)

/**
 * 单个受控应用的完整描述。
 * 路由分发、作用域列表、管理端卡片、开关项全部由这一份数据派生，
 * 新增应用只需要在这里加一条，不再有第二处 when(pkg) 要同步。
 */
data class AppSpec(
    val pkg: String,
    val name: String,
    val tagline: String,
    val letter: String,
    val colorRes: Int,
    val features: List<Feature>,
    val factory: (String, ClassLoader) -> BasePlugin
)

object PluginRegistry {

    val apps: List<AppSpec> = listOf(
        AppSpec(
            pkg = "com.phoenix.read",
            name = "红果短剧",
            tagline = "免广 · 1080P · 会员净化",
            letter = "果",
            colorRes = R.color.brand_hongguo,
            features = listOf(
                Feature("unlock_vip", "会员权益", "源头改写 VipInfoModel，跳过付费门禁"),
                Feature("force_1080p", "1080P 超清", "锁定清晰度选择，不再回落标清"),
                Feature("remove_ads", "清除插播广告", "短剧播放中断点与浮层广告净化"),
                Feature("disable_hotfix", "禁用热修复", "阻止官方 Reparo 下发覆盖本地规则")
            ),
            factory = { pkg, cl -> HongGuoPlugin(pkg, cl) }
        ),
        AppSpec(
            pkg = "com.dragon.read",
            name = "番茄小说",
            tagline = "免广 · 听书音色 · 挂件净化",
            letter = "番",
            colorRes = R.color.brand_fanqie,
            features = listOf(
                Feature("unlock_reader_vip", "阅读会员", "PrivilegeManager 权益全穿透"),
                Feature("remove_reader_ads", "阅读免广", "沉浸阅读页广告与横幅拦截"),
                Feature("unlock_tts_timbre", "AI 高品质音色", "解锁 VIP 朗读音色选择"),
                Feature("hide_floating_pendant", "隐藏金币挂件", "悬浮挂件、福利红点与赚钱 Tab")
            ),
            factory = { pkg, cl -> FanQiePlugin(pkg, cl) }
        ),
        AppSpec(
            pkg = "tv.danmaku.bili",
            name = "哔哩哔哩",
            tagline = "大会员 · 开屏秒过 · 后台播放",
            letter = "B",
            colorRes = R.color.brand_bili,
            features = listOf(
                Feature("remove_splash_ad", "开屏广告", "gRPC 回包与等待时间双清零"),
                Feature("remove_feed_ads", "推荐流广告", "首页卡片与详情页推广净化"),
                Feature("unlock_background_play", "后台播放", "退出前台继续播放音轨")
            ),
            factory = { pkg, cl -> BiliBiliPlugin(pkg, cl) }
        ),
        AppSpec(
            pkg = "com.luna.music",
            name = "汽水音乐",
            tagline = "免广畅听 · 音频插播拦截",
            letter = "♪",
            colorRes = R.color.brand_luna,
            features = listOf(
                Feature("unlock_vip", "会员标识", "UserTier 层级锁定与权益实体补全"),
                Feature("remove_audio_ads", "音频插播广告", "剔除播放队列中的插播条目"),
                Feature("remove_visual_ads", "视觉广告", "开屏、发现页推广卡片净化"),
                Feature("disable_hotfix", "禁用热修复", "阻止热补丁覆盖本地规则")
            ),
            factory = { pkg, cl -> LunaMusicPlugin(pkg, cl) }
        )
    )

    val scopeList: List<String> get() = apps.map { it.pkg }

    val featureCount: Int get() = apps.sumOf { it.features.size }

    fun findByPackage(pkg: String?): AppSpec? = apps.firstOrNull { it.pkg == pkg }

    fun createPlugin(pkg: String, classLoader: ClassLoader): BasePlugin? =
        findByPackage(pkg)?.factory?.invoke(pkg, classLoader)
}
