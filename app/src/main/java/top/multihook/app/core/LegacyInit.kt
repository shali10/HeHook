package top.multihook.app.core

import android.util.Log
import top.multihook.app.plugins.bilibili.BiliBiliPlugin
import top.multihook.app.plugins.fanqie.FanQiePlugin
import top.multihook.app.plugins.hongguo.HongGuoPlugin
import java.lang.reflect.Method

/**
 * 兼容老版 XposedBridge (assets/xposed_init) 的反射入口
 */
class LegacyInit {
    companion object {
        private const val TAG = "MultiHook_Legacy"
    }

    // 由老框架反射调用 handleLoadPackage
    fun handleLoadPackage(lpparam: Any) {
        try {
            val pkgField = lpparam.javaClass.getField("packageName")
            val clField = lpparam.javaClass.getField("classLoader")

            val pkg = pkgField.get(lpparam) as? String ?: return
            val cl = clField.get(lpparam) as? ClassLoader ?: return

            val plugin: BasePlugin? = when (pkg) {
                "com.phoenix.read" -> HongGuoPlugin(pkg, cl)
                "com.dragon.read" -> FanQiePlugin(pkg, cl)
                "tv.danmaku.bili" -> BiliBiliPlugin(pkg, cl)
                else -> null
            }

            plugin?.let {
                Log.i(TAG, "Legacy Xposed Hooking $pkg with ${it.javaClass.simpleName}")
                it.handleHook()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "LegacyInit dispatch failed", t)
        }
    }
}
