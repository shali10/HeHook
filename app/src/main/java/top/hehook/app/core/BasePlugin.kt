package top.hehook.app.core

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Executable
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

    // 延迟初始化 DexKit C++ 模式匹配桥接器（安全加载）
    private var _dexKit: DexKitBridge? = null
    val dexKit: DexKitBridge?
        get() {
            if (_dexKit == null) {
                try {
                    System.loadLibrary("dexkit")
                    _dexKit = DexKitBridge.create(classLoader, false)
                } catch (t: Throwable) {
                    Log.w(tag, "DexKit native bridge not available: ${t.message}")
                }
            }
            return _dexKit
        }

    /**
     * 核心生命周期入口
     */
    abstract fun handleHook()

    /**
     * 真正挂载拦截器 (LibXposed 原生 Hook)
     */
    fun hookMethod(method: Member, hooker: (XposedInterface.Chain) -> Any?) {
        if (method is Executable) {
            try {
                MainRouter.instance.hook(method).intercept { chain ->
                    hooker(chain)
                }
                log("Intercepted: ${method.declaringClass.name}#${method.name}")
            } catch (t: Throwable) {
                logError("Failed to intercept ${method.name}", t)
            }
        }
    }

    /**
     * 替换方法返回值（直接返回固定常量，跳过原方法）
     */
    fun replaceMethod(method: Member, returnValue: Any?) {
        hookMethod(method) { returnValue }
    }

    /**
     * 拦截阻断方法执行（用于 void 方法或取消显示）
     */
    fun blockMethod(method: Member) {
        hookMethod(method) { null }
    }

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
            log("Hook block executed: $actionName")
        } catch (t: Throwable) {
            logError("Hook block failed: $actionName", t)
        }
    }
}
