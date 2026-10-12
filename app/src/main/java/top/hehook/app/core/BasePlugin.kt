package top.hehook.app.core

import android.content.Context
import android.util.Log
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * 业务子插件基类
 * 每个目标 App 对应一个单独的 Plugin 实现
 */
abstract class BasePlugin(
    val packageName: String,
    val classLoader: ClassLoader
) {
    val tag: String = "HeHook_${javaClass.simpleName}"
    var appContext: Context? = null

    // 延迟初始化 DexKit C++ 模式匹配桥接器
    private var _dexKit: DexKitBridge? = null
    val dexKit: DexKitBridge
        get() {
            if (_dexKit == null) {
                // 加载目标宿主 apk 的 dexkit
                System.loadLibrary("dexkit")
                _dexKit = DexKitBridge.create(classLoader, false)
            }
            return _dexKit!!
        }

    /**
     * 核心生命周期入口
     */
    abstract fun handleHook()

    /**
     * 读取自身配置开关
     */
    fun isEnabled(key: String, default: Boolean = true): Boolean {
        return ConfigManager.getBoolean(packageName, key, default)
    }

    fun log(message: String) {
        Log.i(tag, "[$packageName] $message")
    }

    fun logError(message: String, throwable: Throwable? = null) {
        Log.e(tag, "[$packageName] $message", throwable)
    }

    /**
     * 安全反射执行
     */
    inline fun safeRun(actionName: String, block: () -> Unit) {
        try {
            block()
            log("Hook success: $actionName")
        } catch (t: Throwable) {
            logError("Hook failed: $actionName", t)
        }
    }
}
