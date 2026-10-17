package top.hehook.app.core

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 现代 LibXposed 纯反射与拦截工具套件
 * 零 SO 依赖，异常全量隔离，确保宿主稳健运行
 */
object HookKit {
    private const val TAG = "HeHook_HookKit"
    var module: XposedModule? = null

    private val classCache = ConcurrentHashMap<String, Class<*>?>()
    private val nullClassMarker = Any::class.java

    fun init(xposedModule: XposedModule) {
        module = xposedModule
    }

    fun isReady(): Boolean = module != null

    fun loadClass(classLoader: ClassLoader?, className: String?): Class<*>? {
        if (className.isNullOrBlank()) return null
        if (classCache[className] === nullClassMarker) return null
        classCache[className]?.let { return it }

        val found = doLoadClass(classLoader, className)
        if (found != null) {
            classCache[className] = found
        } else {
            classCache[className] = nullClassMarker
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
        var result: Any? = null
        var newArgs: Array<Any?>? = null

        override fun skip() {
            skipped = true
        }

        override fun setResult(result: Any?) {
            this.skipped = true
            this.hasResult = true
            this.result = result
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

    fun hookBefore(
        method: Method?,
        callback: (Chain) -> Unit
    ): Boolean {
        if (method == null || !isReady()) return false
        val mod = module ?: return false
        return try {
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val ctx = ChainImpl(chain)
                try {
                    callback(ctx)
                } catch (t: Throwable) {
                    Log.w(TAG, "Exception in hookBefore callback for ${method.name}: ${t.message}")
                }
                if (ctx.skipped) {
                    if (ctx.hasResult) ctx.result else defaultValue(method.returnType)
                } else {
                    val na = ctx.newArgs
                    if (na != null) chain.proceed(na) else chain.proceed()
                }
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook method ${method.name}", t)
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
            false
        }
    }

    fun hookAllByName(
        cls: Class<*>?,
        methodName: String,
        callback: (Chain) -> Unit
    ): Int {
        if (cls == null || !isReady()) return 0
        var count = 0
        try {
            for (m in cls.declaredMethods) {
                if (m.name == methodName) {
                    if (hookBefore(m, callback)) {
                        count++
                    }
                }
            }
        } catch (_: Throwable) {}
        return count
    }

    fun hookAllAfterByName(
        cls: Class<*>?,
        methodName: String,
        afterCallback: (thisObj: Any?, result: Any?, args: List<Any?>) -> Unit
    ): Int {
        if (cls == null || !isReady()) return 0
        val mod = module ?: return 0
        var count = 0
        try {
            for (m in cls.declaredMethods) {
                if (m.name == methodName) {
                    m.isAccessible = true
                    try {
                        mod.hook(m).intercept { chain ->
                            val res = chain.proceed()
                            try {
                                afterCallback(chain.thisObject, res, chain.args ?: emptyList())
                            } catch (t: Throwable) {
                                Log.w(TAG, "Exception in hookAllAfterByName: ${t.message}")
                            }
                            res
                        }
                        count++
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
        return count
    }

    fun hookAllConstructors(
        cls: Class<*>?,
        afterCallback: (thisObj: Any?, args: List<Any?>) -> Unit
    ): Int {
        if (cls == null || !isReady()) return 0
        val mod = module ?: return 0
        var count = 0
        try {
            for (c in cls.declaredConstructors) {
                c.isAccessible = true
                try {
                    mod.hook(c).intercept { chain ->
                        val res = chain.proceed()
                        try {
                            afterCallback(chain.thisObject, chain.args ?: emptyList())
                        } catch (t: Throwable) {
                            Log.w(TAG, "Exception in hookAllConstructors: ${t.message}")
                        }
                        res
                    }
                    count++
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
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
            } catch (_: Throwable) {
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
            } catch (_: Throwable) {
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
