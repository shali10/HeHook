package top.hehook.app.core

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 现代 LibXposed 纯反射与拦截工具套件
 * 零 SO 依赖，异常全量隔离，确保宿主稳健运行
 *
 * v1.5.0 改造：
 * - 后端从 `MainRouter.instance` 单例改为 [HookRuntime]，老入口 / 单元测试都不会再踩 lateinit
 * - 每个 hook 成功 / 失败都进 [HookStats]，入口直接打汇总，不再「看着挂上了其实一条没生效」
 * - 失败统一 Log.e，LSPosed 日志页可见
 */
object HookKit {
    private const val TAG = "HeHook_HookKit"

    private val classCache = ConcurrentHashMap<String, Class<*>?>()
    private val nullClassMarker = Any::class.java

    fun init(xposedModule: XposedModule) {
        HookRuntime.install(xposedModule)
    }

    fun isReady(): Boolean = HookRuntime.isReady()

    fun loadClass(classLoader: ClassLoader?, className: String?): Class<*>? {
        if (className.isNullOrBlank()) return null
        if (classCache[className] === nullClassMarker) return null
        classCache[className]?.let { return it }

        val found = doLoadClass(classLoader, className)
        if (found != null) {
            classCache[className] = found
        } else {
            classCache[className] = nullClassMarker
            HookStats.missing.incrementAndGet()
        }
        return found
    }

    private fun doLoadClass(classLoader: ClassLoader?, className: String): Class<*>? {
        if (classLoader != null) {
            try {
                return Class.forName(className, false, classLoader)
            } catch (_: Throwable) {}
        }
        try {
            return Class.forName(className, false, ClassLoader.getSystemClassLoader())
        } catch (_: Throwable) {}
        try {
            val ccl = Thread.currentThread().contextClassLoader
            if (ccl != null && ccl != classLoader) {
                return Class.forName(className, false, ccl)
            }
        } catch (_: Throwable) {}
        return null
    }

    interface Chain {
        val raw: XposedInterface.Chain
        val thisObject: Any?
        val args: List<Any?>
        fun skip()
        fun setResult(result: Any?)
        fun setArg(index: Int, value: Any?)
    }

    class ChainImpl(override val raw: XposedInterface.Chain) : Chain {
        override val thisObject: Any? get() = raw.thisObject
        override val args: List<Any?> get() = raw.args ?: emptyList()
        var skipped: Boolean = false
        var hasResult: Boolean = false
        var customResult: Any? = null
        var newArgs: Array<Any?>? = null

        override fun skip() {
            skipped = true
        }

        override fun setResult(result: Any?) {
            this.skipped = true
            this.hasResult = true
            this.customResult = result
        }

        override fun setArg(index: Int, value: Any?) {
            if (newArgs == null) {
                val current = raw.args ?: emptyList<Any?>()
                newArgs = current.toTypedArray()
            }
            if (index in 0 until (newArgs?.size ?: 0)) {
                newArgs!![index] = value
            }
        }
    }

    fun hookBefore(method: Method?, callback: (Chain) -> Unit): Boolean {
        if (method == null || !isReady()) return false
        return try {
            method.isAccessible = true
            HookRuntime.hook(method) { chain ->
                val ctx = ChainImpl(chain)
                try {
                    callback(ctx)
                } catch (t: Throwable) {
                    Log.e(TAG, "hookBefore 回调异常 ${method.name}", t)
                }
                if (ctx.skipped) {
                    if (ctx.hasResult) ctx.customResult else defaultValue(method.returnType)
                } else {
                    val na = ctx.newArgs
                    if (na != null) chain.proceed(na) else chain.proceed()
                }
            }
        } catch (t: Throwable) {
            HookStats.failed.incrementAndGet()
            Log.e(TAG, "hookBefore 失败 ${method.name}", t)
            false
        }
    }

    fun hookBeforeExact(
        cls: Class<*>?,
        methodName: String,
        paramTypes: Array<Class<*>>,
        callback: (Chain) -> Unit
    ): Boolean {
        if (cls == null || !isReady()) return false
        return try {
            val m = cls.getDeclaredMethod(methodName, *paramTypes)
            hookBefore(m, callback)
        } catch (t: Throwable) {
            HookStats.failed.incrementAndGet()
            Log.e(TAG, "hookBeforeExact 找不到 ${cls.name}#$methodName", t)
            false
        }
    }

    fun hookAllByName(cls: Class<*>?, methodName: String, callback: (Chain) -> Unit): Int {
        if (cls == null) return 0
        var count = 0
        try {
            for (m in cls.declaredMethods) {
                if (m.name == methodName && hookBefore(m, callback)) count++
            }
        } catch (t: Throwable) {
            Log.e(TAG, "hookAllByName 异常 ${cls.name}#$methodName", t)
        }
        if (count == 0) HookStats.failed.incrementAndGet()
        return count
    }

    fun hookAllAfterByName(
        cls: Class<*>?,
        methodName: String,
        afterCallback: (thisObj: Any?, result: Any?, args: List<Any?>) -> Unit
    ): Int {
        if (cls == null || !isReady()) return 0
        var count = 0
        try {
            for (m in cls.declaredMethods) {
                if (m.name != methodName) continue
                m.isAccessible = true
                val ok = HookRuntime.hook(m) { chain ->
                    val res = chain.proceed()
                    try {
                        afterCallback(chain.thisObject, res, chain.args ?: emptyList())
                    } catch (t: Throwable) {
                        Log.e(TAG, "hookAllAfterByName 回调异常 ${m.name}", t)
                    }
                    res
                }
                if (ok) count++
            }
        } catch (t: Throwable) {
            Log.e(TAG, "hookAllAfterByName 异常 ${cls.name}#$methodName", t)
        }
        if (count == 0) HookStats.failed.incrementAndGet()
        return count
    }

    fun hookAllConstructors(cls: Class<*>?, afterCallback: (thisObj: Any?, args: List<Any?>) -> Unit): Int {
        if (cls == null || !isReady()) return 0
        var count = 0
        try {
            for (c in cls.declaredConstructors) {
                c.isAccessible = true
                val ok = HookRuntime.hook(c) { chain ->
                    val res = chain.proceed()
                    try {
                        afterCallback(chain.thisObject, chain.args ?: emptyList())
                    } catch (t: Throwable) {
                        Log.e(TAG, "构造器回调异常 ${cls.name}", t)
                    }
                    res
                }
                if (ok) count++
            }
        } catch (t: Throwable) {
            Log.e(TAG, "hookAllConstructors 异常 ${cls.name}", t)
        }
        if (count == 0) HookStats.failed.incrementAndGet()
        return count
    }

    /** 拦截构造函数并在执行前修改入参 */
    fun hookAllConstructorsBefore(cls: Class<*>?, callback: (Constructor<*>, Chain) -> Unit): Int {
        if (cls == null || !isReady()) return 0
        var count = 0
        try {
            for (c in cls.declaredConstructors) {
                c.isAccessible = true
                val ok = HookRuntime.hook(c) { chain ->
                    val ctx = ChainImpl(chain)
                    try {
                        callback(c, ctx)
                    } catch (t: Throwable) {
                        Log.e(TAG, "构造器前置回调异常 ${cls.name}", t)
                    }
                    val na = ctx.newArgs
                    if (na != null) chain.proceed(na) else chain.proceed()
                }
                if (ok) count++
            }
        } catch (t: Throwable) {
            Log.e(TAG, "hookAllConstructorsBefore 异常 ${cls.name}", t)
        }
        if (count == 0) HookStats.failed.incrementAndGet()
        return count
    }

    /** 条件匹配拦截类中的方法 */
    fun hookAllMatchingMethods(cls: Class<*>?, matcher: (Method) -> Boolean, callback: (Chain) -> Unit): Int {
        if (cls == null || !isReady()) return 0
        var count = 0
        try {
            for (m in cls.declaredMethods) {
                if (matcher(m) && hookBefore(m, callback)) count++
            }
        } catch (t: Throwable) {
            Log.e(TAG, "hookAllMatchingMethods 异常 ${cls.name}", t)
        }
        return count
    }

    fun zeroField(obj: Any?, fieldName: String) {
        if (obj == null) return
        var curr: Class<*>? = obj.javaClass
        while (curr != null) {
            try {
                val f = curr.getDeclaredField(fieldName)
                f.isAccessible = true
                when (f.type) {
                    Boolean::class.javaPrimitiveType -> if (f.getBoolean(obj)) f.setBoolean(obj, false)
                    Long::class.javaPrimitiveType -> if (f.getLong(obj) != 0L) f.setLong(obj, 0L)
                    Int::class.javaPrimitiveType -> if (f.getInt(obj) != 0) f.setInt(obj, 0)
                    List::class.java -> f.set(obj, null)
                    else -> {}
                }
                return
            } catch (_: NoSuchFieldException) {
                curr = curr.superclass
            } catch (t: Throwable) {
                return
            }
        }
    }

    fun setField(obj: Any?, fieldName: String, value: Any?) {
        if (obj == null) return
        var curr: Class<*>? = obj.javaClass
        while (curr != null) {
            try {
                val f = curr.getDeclaredField(fieldName)
                f.isAccessible = true
                f.set(obj, value)
                return
            } catch (_: NoSuchFieldException) {
                curr = curr.superclass
            } catch (t: Throwable) {
                return
            }
        }
    }

    private fun defaultValue(type: Class<*>?): Any? {
        if (type == null || type == Void.TYPE) return null
        return when (type) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Short::class.javaPrimitiveType -> 0.toShort()
            Byte::class.javaPrimitiveType -> 0.toByte()
            Char::class.javaPrimitiveType -> '\u0000'
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            else -> null
        }
    }
}
