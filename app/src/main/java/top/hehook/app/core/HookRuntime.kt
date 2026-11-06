package top.hehook.app.core

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Member
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hook 计数与统一落日志。
 *
 * 诊断铁律：LSPosed 日志页默认只显示 error 级，所以入口、汇总、失败一律走 Log.e；
 * 只要有一处用 Log.i，用户截图过来就会是空的。
 */
object HookStats {
    val applied = AtomicInteger(0)
    val failed = AtomicInteger(0)
    val missing = AtomicInteger(0)

    fun reset() {
        applied.set(0)
        failed.set(0)
        missing.set(0)
    }

    fun summary(pkg: String): String =
        "applied=${applied.get()} failed=${failed.get()} missing=${missing.get()} [$pkg]"
}

/**
 * 当前进程的 LibXposed 后端。
 *
 * 之前插件直接引用 `MainRouter.instance`（lateinit），在老版入口下从未初始化，
 * 结果每一次 hook 都在 try/catch 里静默失败。现在后端由入口显式安装，
 * 没装就直接报错计数，不再假装成功。
 */
object HookRuntime {
    @Volatile
    private var module: XposedModule? = null

    fun install(m: XposedModule) {
        module = m
    }

    fun isReady(): Boolean = module != null

    fun hook(member: Member, interceptor: (io.github.libxposed.api.XposedInterface.Chain) -> Any?): Boolean {
        val m = module ?: run {
            Trace.log("HeHook_Runtime", "后端未安装，无法 hook ${member.name}")
            HookStats.failed.incrementAndGet()
            return false
        }
        return try {
            m.hook(member).intercept { chain -> interceptor(chain) }
            HookStats.applied.incrementAndGet()
            true
        } catch (t: Throwable) {
            HookStats.failed.incrementAndGet()
            Trace.log("HeHook_Runtime", "hook 失败 ${member.declaringClass.name}#${member.name}: $t")
            false
        }
    }
}
