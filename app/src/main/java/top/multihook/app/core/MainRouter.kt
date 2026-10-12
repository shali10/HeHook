package top.multihook.app.core

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import top.multihook.app.plugins.bilibili.BiliBiliPlugin
import top.multihook.app.plugins.fanqie.FanQiePlugin
import top.multihook.app.plugins.hongguo.HongGuoPlugin

/**
 * 现代 LibXposed (API 101) 统一入口与运行时路由器
 */
class MainRouter(
    base: XposedInterface,
    param: ModuleLoadedParam
) : XposedModule(base, param) {

    companion object {
        private const val TAG = "MultiHook_Router"
        lateinit var instance: MainRouter
            private set
    }

    init {
        instance = this
        Log.i(TAG, "MultiHook Module Loaded. API version: ${param.apiVersion}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)

        // 仅处理第一进程或主进程，避免无意义注入
        val pkg = param.packageName
        val cl = param.classLoader

        Log.i(TAG, "onPackageLoaded -> $pkg (isFirstPackage: ${param.isFirstPackage})")

        // 核心分发路由
        val plugin: BasePlugin? = when (pkg) {
            "com.phoenix.read" -> HongGuoPlugin(pkg, cl)
            "com.dragon.read" -> FanQiePlugin(pkg, cl)
            "tv.danmaku.bili" -> BiliBiliPlugin(pkg, cl)
            else -> null
        }

        if (plugin != null) {
            Log.i(TAG, "Dispatching to plugin: ${plugin.javaClass.simpleName} for [$pkg]")
            try {
                plugin.handleHook()
            } catch (t: Throwable) {
                Log.e(TAG, "Fatal error executing plugin for $pkg", t)
            }
        }
    }
}
