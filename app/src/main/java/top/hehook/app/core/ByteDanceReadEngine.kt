package top.hehook.app.core

import android.util.Log

/**
 * 字节跳动阅读平台 (com.dragon.read 体系) 聚合增强引擎
 * 统一赋能：红果短剧 (com.phoenix.read) 与 番茄免费小说 (com.dragon.read)
 * 实装：AdGate 广告总闸、PrivilegeManager VIP 特权全量穿透、OneStop 智能跳过、试看清零与热修防御
 */
class ByteDanceReadEngine(
    private val plugin: BasePlugin,
    private val isHongGuo: Boolean
) {
    private val cl = plugin.classLoader
    private val tag = plugin.tag

    fun installAll() {
        plugin.log("Installing ByteDanceReadEngine (isHongGuo=$isHongGuo)...")

        // 1. 广告位置级与 API 总闸 AdGate
        if (plugin.isEnabled("remove_ads", true) || plugin.isEnabled("remove_reader_ads", true)) {
            installAdGate()
        }

        // 2. VIP 鉴权与特权总管 PrivilegeManager + NsVipImpl
        if (plugin.isEnabled("unlock_vip", true) || plugin.isEnabled("unlock_reader_vip", true)) {
            installVipPrivilege()
        }

        // 3. 一站式短剧广告拦截与卡片过滤 OneStopAd
        if (plugin.isEnabled("remove_ads", true) || plugin.isEnabled("remove_reader_ads", true)) {
            installOneStopAndFeedAds()
        }

        // 4. 试看标记字段清零 TrialDataFlags
        if (plugin.isEnabled("unlock_vip", true) || plugin.isEnabled("unlock_reader_vip", true)) {
            installTrialDataFlags()
        }

        // 5. 禁用字节在线热修复 Reparo (防下发补丁对抗)
        if (plugin.isEnabled("disable_hotfix", true)) {
            installDisableReparo()
        }
    }

    /**
     * 广告位置级总闸 (AdGate)
     */
    private fun installAdGate() = plugin.safeRun("AdGate") {
        // A. NsAdImpl 核心门面拦截
        val nsAdCls = HookKit.loadClass(cl, "com.dragon.read.component.biz.impl.NsAdImpl")
        if (nsAdCls != null) {
            // checkAdAvailable(String, String) -> false
            HookKit.hookAllByName(nsAdCls, "checkAdAvailable") { chain ->
                chain.setResult(false)
            }
            // openOpeningScreenAdActivity -> skip
            HookKit.hookAllByName(nsAdCls, "openOpeningScreenAdActivity") { chain ->
                chain.skip()
            }
            // canRetain -> false
            HookKit.hookAllByName(nsAdCls, "canRetain") { chain ->
                chain.setResult(false)
            }
            plugin.log("AdGate: NsAdImpl checkAdAvailable/openOpeningScreenAdActivity/canRetain hooked")
        }

        // B. IAdAvailableConfig$$Impl 配置拦截 -> 全部可用性字段设为 false
        val availCfgCls = HookKit.loadClass(cl, "com.dragon.read.base.ssconfig.settings.interfaces.IAdAvailableConfig" + "$$Impl")
            ?: HookKit.loadClass(cl, "com.dragon.read.base.ssconfig.settings.interfaces.IAdAvailableConfig")
        if (availCfgCls != null) {
            HookKit.hookAllAfterByName(availCfgCls, "getConfig") { _, result, _ ->
                if (result != null) {
                    HookKit.zeroField(result, "readerAdAvailable")
                    HookKit.zeroField(result, "splashAdAvailable")
                    HookKit.zeroField(result, "shortSeriesAdAvailable")
                    HookKit.zeroField(result, "otherAdExcludeCoinAvailable")
                }
            }
            plugin.log("AdGate: IAdAvailableConfig getConfig field overrides installed")
        }

        // C. 横幅广告工具拦截
        val bannerUtilCls = HookKit.loadClass(cl, "com.dragon.read.ad.banner.manager.BannerAdConfigUtil")
        if (bannerUtilCls != null) {
            HookKit.hookAllByName(bannerUtilCls, "enableBanner") { it.setResult(false) }
            HookKit.hookAllByName(bannerUtilCls, "canRequestBanner") { it.setResult(false) }
            HookKit.hookAllByName(bannerUtilCls, "a") { it.setResult(false) }
            HookKit.hookAllByName(bannerUtilCls, "reachMaxCloseCnt") { it.setResult(true) }
            plugin.log("AdGate: BannerAdConfigUtil hooked")
        }

        // D. 自然流量横幅请求拦截
        val bannerNatCls = HookKit.loadClass(cl, "com.dragon.read.ad.banner.manager.ReaderNaturalFlowBannerRequestManager")
        if (bannerNatCls != null) {
            HookKit.hookAllByName(bannerNatCls, "requestReaderNaturalFlowBannerIfNeed") { it.skip() }
        }

        // E. NsUtilsDependImpl
        val nsUtilsCls = HookKit.loadClass(cl, "com.dragon.read.component.NsUtilsDependImpl")
        if (nsUtilsCls != null) {
            HookKit.hookAllByName(nsUtilsCls, "canRetain") { it.setResult(false) }
        }

        // F. 开屏展示层 OpeningScreenADActivity
        val splashActCls = HookKit.loadClass(cl, "com.dragon.read.ad.openingscreenad.OpeningScreenADActivity")
        if (splashActCls != null) {
            for (m in listOf("showNaturalAdView", "showBrandAdView", "showImcSplashView")) {
                HookKit.hookAllByName(splashActCls, m) { it.skip() }
            }
            plugin.log("AdGate: OpeningScreenADActivity splash views skipped")
        }

        // G. 混淆位置总闸兜底 (tc3.a / p13.a)
        val tc3Cls = HookKit.loadClass(cl, "tc3.a")
        if (tc3Cls != null) {
            HookKit.hookAllByName(tc3Cls, "b") { it.setResult(false) }
            HookKit.hookAllByName(tc3Cls, "d") { it.setResult(false) }
            plugin.log("AdGate: tc3.a position gate hooked")
        }

        val p13Cls = HookKit.loadClass(cl, "p13.a")
        if (p13Cls != null) {
            for (name in listOf("a", "b", "c", "d", "e", "isReaderAdAvailable")) {
                HookKit.hookAllByName(p13Cls, name) { it.setResult(false) }
            }
            plugin.log("AdGate: p13.a tomato view gate hooked")
        }

        // H. 听书广告服务
        val audioAdCls = HookKit.loadClass(cl, "com.dragon.read.ad.banner.impl.HongguoAudioAdServiceImpl")
        if (audioAdCls != null) {
            HookKit.hookAllByName(audioAdCls, "enableRequestAudioPatchAd") { it.setResult(false) }
            HookKit.hookAllByName(audioAdCls, "enableRequestAudioInfoFlowAd") { it.setResult(false) }
        }
        val audioDynCls = HookKit.loadClass(cl, "com.dragon.read.ad.audioad.DynamicAdServiceImpl")
        if (audioDynCls != null) {
            HookKit.hookAllByName(audioDynCls, "preloadLynxViewReal") { it.skip() }
        }
    }

    /**
     * VIP 鉴权与特权总管
     */
    private fun installVipPrivilege() = plugin.safeRun("VipPrivilege") {
        // A. PrivilegeManager 全局特权拦截
        val pmCls = HookKit.loadClass(cl, "com.dragon.read.component.biz.impl.privilege.PrivilegeManager")
        if (pmCls != null) {
            val trueMethods = listOf(
                "isVip", "isAnyVip", "hasReadPaidBookPrivilege", "isNoAd",
                "isForeverNoAd", "isFakeVipActive", "hasVipShortSeriesPrivilege",
                "hasNoAdPrivilege", "hasNoAdFollAllScene", "hasNoAdForShortSeries",
                "hasNoAdReadConsumptionPrivilege", "isHasFansStickerPrivilege",
                "adVipAvailable", "hasPrivilege"
            )
            for (m in trueMethods) {
                HookKit.hookAllByName(pmCls, m) { it.setResult(true) }
            }
            HookKit.hookAllByName(pmCls, "isBookAdFree") { it.setResult(1) }
            plugin.log("VipPrivilege: PrivilegeManager full privileges hooked")
        }

        // B. NsVipImpl 核心鉴权接口
        val nsVipCls = HookKit.loadClass(cl, "com.dragon.read.component.biz.impl.NsVipImpl")
        if (nsVipCls != null) {
            val trueMethods = listOf(
                "isVip", "isAnyVip", "isVipEnable", "canReadPaidBook",
                "canReadPaidBookEnhance", "canListenPaidBook"
            )
            for (m in trueMethods) {
                HookKit.hookAllByName(nsVipCls, m) { it.setResult(true) }
            }
            val falseMethods = listOf("isNormalUser", "audioIsPubPay")
            for (m in falseMethods) {
                HookKit.hookAllByName(nsVipCls, m) { it.setResult(false) }
            }
            plugin.log("VipPrivilege: NsVipImpl hooked")
        }
    }

    /**
     * 一站式短剧广告拦截与卡片过滤 (OneStopAd)
     */
    private fun installOneStopAndFeedAds() = plugin.safeRun("OneStopAndFeedAds") {
        // A. 关键破局点：y() 方法必须返回 "短剧中插免广权益生效" 字符串！
        val hostAdapters = listOf(
            "com.dragon.read.ad.onestop.munion.adapter.MUnionSeriesAdSdkHostAdapter",
            "com.dragon.read.ad.onestop.seriessdk.adapter.j",
            "com.dragon.read.ad.onestop.seriessdk.adapter.SeriesAdSdkHostAdapter"
        )
        for (name in hostAdapters) {
            val cls = HookKit.loadClass(cl, name)
            if (cls != null) {
                HookKit.hookAllByName(cls, "y") { chain ->
                    chain.setResult("短剧中插免广权益生效")
                }
                plugin.log("OneStopAd: HostAdapter $name#y -> '短剧中插免广权益生效'")
            }
        }

        // B. 短剧免广权益类 (m/h/i/j -> true)
        val privCls = HookKit.loadClass(cl, "com.dragon.read.ad.shortseries.privilege.b")
        if (privCls != null) {
            for (m in listOf("m", "h", "i", "j")) {
                HookKit.hookAllByName(privCls, m) { it.setResult(true) }
            }
            plugin.log("OneStopAd: Privilege b hooked (m/h/i/j -> true)")
        }

        // C. 贴片开关与展示口
        val patchCfgCls = HookKit.loadClass(cl, "com.dragon.read.base.ssconfig.settings.interfaces.ISeriesPatchAdConfig" + "$$Impl")
            ?: HookKit.loadClass(cl, "com.dragon.read.base.ssconfig.settings.interfaces.ISeriesPatchAdConfig")
        if (patchCfgCls != null) {
            HookKit.hookAllAfterByName(patchCfgCls, "getConfig") { _, result, _ ->
                if (result != null) {
                    HookKit.zeroField(result, "enable")
                }
            }
        }

        val l4Cls = HookKit.loadClass(cl, "com.dragon.read.component.shortvideo.impl.inject.view.l4")
        if (l4Cls != null) {
            HookKit.hookAllByName(l4Cls, "M0") { it.skip() }
            plugin.log("OneStopAd: l4.M0 patch ad skipped")
        }

        // D. 播放页 Lynx 卡片拦截 (onBind / h4)
        val lynxCls = HookKit.loadClass(cl, "com.dragon.read.component.biz.impl.holder.LynxCardHolder")
        if (lynxCls != null) {
            HookKit.hookAllByName(lynxCls, "onBind") { it.skip() }
            HookKit.hookAllByName(lynxCls, "h4") { it.skip() }
            plugin.log("OneStopAd: LynxCardHolder onBind/h4 skipped")
        }

        // E. 电商带货卡与商品面板拦截
        val ecomBizCls = HookKit.loadClass(cl, "com.dragon.read.component.shortvideo.impl.sameproduct.aiseed.EcomAiProductSeedingBiz")
        if (ecomBizCls != null) {
            HookKit.hookAllByName(ecomBizCls, "D") { it.skip() }
            plugin.log("OneStopAd: EcomAiProductSeedingBiz.D skipped")
        }

        val ecCfgCls = HookKit.loadClass(cl, "com.dragon.read.component.shortvideo.impl.sameproduct.config.IShoppingFeed" + "$$Impl")
        if (ecCfgCls != null) {
            HookKit.hookAllAfterByName(ecCfgCls, "getConfig") { _, result, _ ->
                if (result != null) {
                    HookKit.zeroField(result, "enableShoppingFeed")
                    HookKit.setField(result, "lynxSchema", "")
                }
            }
        }

        // F. 非标广告
        val nsAdProviderCls = HookKit.loadClass(cl, "com.dragon.read.nonstandard.ad.adapter.KmpSeriesNonStandardAdDataProvider")
        if (nsAdProviderCls != null) {
            HookKit.hookAllByName(nsAdProviderCls, "p") { it.skip() }
            HookKit.hookAllByName(nsAdProviderCls, "D") { it.skip() }
        }

        // G. 老版 FeedDrawAdManager
        val oldFeedCls = HookKit.loadClass(cl, "com.dragon.read.ad.feeddraw.FeedDrawAdManager")
        if (oldFeedCls != null) {
            HookKit.hookAllByName(oldFeedCls, "getAd") { it.setResult(null) }
        }
    }

    /**
     * 试看标记字段清零 (TrialDataFlags)
     */
    private fun installTrialDataFlags() = plugin.safeRun("TrialDataFlags") {
        val targets = listOf(
            "com.dragon.read.rpc.model.EpisodeInfo",
            "seriessdk.com.dragon.read.saas.rpc.model.EpisodeInfo",
            "com.dragon.read.rpc.model.VideoData",
            "readersaas.com.dragon.read.saas.rpc.model.VideoData",
            "com.dragon.read.rpc.model.SaaSVideoModelData",
            "seriessdk.com.dragon.read.saas.rpc.model.SaaSVideoModelData",
            "com.dragon.read.rpc.model.VideoModelData",
            "seriessdk.com.dragon.read.saas.rpc.model.VideoModelData",
            "com.dragon.read.rpc.model.VideoPayInfo",
            "com.dragon.read.component.shortvideo.data.saas.video.SaasVideoData",
            "com.dragon.read.component.shortvideo.data.saas.rpcmodel.SaasVideoDirectoryItem"
        )
        for (name in targets) {
            val cls = HookKit.loadClass(cl, name)
            if (cls != null) {
                HookKit.hookAllConstructors(cls) { obj, _ ->
                    HookKit.zeroField(obj, "isPreviewMaterial")
                    HookKit.zeroField(obj, "needUnlock")
                    HookKit.zeroField(obj, "isTrialVideo")
                    HookKit.zeroField(obj, "trialDuration")
                    HookKit.setField(obj, "paid", true)
                }
            }
        }
        plugin.log("TrialDataFlags: Construct-time zero-field hooks installed")
    }

    /**
     * 禁用 Reparo 在线热修复
     */
    private fun installDisableReparo() = plugin.safeRun("DisableReparo") {
        val reparoSvc = HookKit.loadClass(cl, "com.dragon.read.base.hotfix.ReparoHotFixInitServiceImpl")
        if (reparoSvc != null) {
            HookKit.hookAllByName(reparoSvc, "loadRemotePatch") { it.skip() }
        }
        val reparoCfg = HookKit.loadClass(cl, "com.bytedance.reparo.IReparoConfig")
        if (reparoCfg != null) {
            HookKit.hookAllByName(reparoCfg, "enable") { it.setResult(false) }
            HookKit.hookAllByName(reparoCfg, "autoFetchPatch") { it.setResult(false) }
        }
        plugin.log("DisableReparo: Reparo patch download disabled")
    }
}
