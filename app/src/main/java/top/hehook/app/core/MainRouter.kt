package top.hehook.app.core

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * 现代 LibXposed (API 101) 唯一入口。
 *
 * v1.5.0 起移除老版 XposedBridge 入口（assets/xposed_init + LegacyInit）：
 * 那条路径下 LibXposed 后端根本装不上，所有 hook 都会静默失败，留着比没有更坏。
 * 需要兼容的设备请使用 LSPosed 1.9+（原生支持 API 101）。
 */
class MainRouter : XposedModule() {

    companion object {
        private const val TAG = "HeHook_Router"

        init {
            // 类静态初始化即打点：比任何回调都早，用于区分「类没加载」与「回调没触发」
            Trace.log(TAG, "== MainRouter CLASS LOADED ==")
        }
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        HookKit.init(this)
        HookStats.reset()
        Trace.log(TAG, "== HeHook Module Loaded into process ==")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)

        val pkg = param.packageName
        val cl = param.classLoader
        val spec = PluginRegistry.findByPackage(pkg)

        Trace.log(TAG, "== onPackageReady == pkg=$pkg plugin=${spec?.name ?: "未托管"}")

        if (spec == null) return

        HookStats.reset()
        val startedAt = System.currentTimeMillis()

        val plugin = PluginRegistry.createPlugin(pkg, cl)
        if (plugin == null) {
            Log.e(TAG, "插件构造失败：$pkg，检查 PluginRegistry 中 ${spec.name} 的 factory")
            return
        }

        try {
            plugin.handleHook()
        } catch (t: Throwable) {
            Log.e(TAG, "执行 ${spec.name} 插件异常", t)
        }

        val cost = System.currentTimeMillis() - startedAt
        // 汇总走 Log.e：LSPosed 日志页默认只显示 error 级别
        Log.e(TAG, "HeHook 汇总 ${spec.name} | ${HookStats.summary(pkg)} | 耗时 ${cost}ms")
    }
}
