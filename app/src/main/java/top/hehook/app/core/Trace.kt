package top.hehook.app.core

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 落地自证通道：同时写 logcat 与磁盘文件。
 *
 * 存在意义：LSPosed 的「模块日志」页签在部分版本/机型上抓不到注入早期的输出，
 * 导致「模块到底有没有进进程」无法确认。这里直接往宿主可写的路径追加文件，
 * 用户用文件管理器即可查看，绕开日志页的不确定性。
 */
object Trace {
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, msg: String) {
        try {
            Log.e(tag, msg)
        } catch (_: Throwable) {}

        val line = "[${fmt.format(Date())}] [$tag] $msg\n"
        for (p in candidates()) {
            try {
                val f = File(p)
                f.parentFile?.mkdirs()
                f.appendText(line)
            } catch (_: Throwable) {
                // 无权限就换下一个落点，绝不抛
            }
        }
    }

    private fun candidates(): List<String> {
        val list = mutableListOf("/sdcard/Download/HeHook_trace.log")
        val pkg = processPackage()
        if (!pkg.isNullOrEmpty()) {
            list.add("/storage/emulated/0/Android/data/$pkg/files/HeHook_trace.log")
        }
        return list
    }

    private fun processPackage(): String? = try {
        val f = File("/proc/self/cmdline")
        if (f.exists()) {
            f.readText().replace("\u0000", "").trim().split(":").firstOrNull()
        } else null
    } catch (_: Throwable) {
        null
    }
}
