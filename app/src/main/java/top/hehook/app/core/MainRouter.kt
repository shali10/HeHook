package top.hehook.app.core

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import top.hehook.app.plugins.bilibili.BiliBiliPlugin
import top.hehook.app.plugins.fanqie.FanQiePlugin
import top.hehook.app.plugins.hongguo.HongGuoPlugin
import top.hehook.app.plugins.luna.LunaMusicPlugin

/**
 * 现代 LibXposed (API 101) 统一入口与运行时路由器
 */
class MainRouter : XposedModule() {

    companion object {
        private const val TAG = "HeHook_Router"
        lateinit var instance: MainRouter
            private set
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        instance = this
        HookKit.init(this)
        Log.e(TAG, "== HeHook Module Loaded into process ==")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)

        val pkg = param.packageName
        val cl = param.classLoader

        Log.e(TAG, "== HeHook onPackageReady == pkg=$pkg first=${param.isFirstPackage} cl=${cl.javaClass.name}")

        // 核心分发路由：根据宿主包名，精确分发给各自独立的子模块
        val plugin: BasePlugin? = when (pkg) {
            "com.phoenix.read" -> HongGuoPlugin(pkg, cl)
            "com.dragon.read" -> FanQiePlugin(pkg, cl)
            "tv.danmaku.bili" -> BiliBiliPlugin(pkg, cl)
            "com.luna.music" -> LunaMusicPlugin(pkg, cl)
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
