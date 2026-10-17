package top.hehook.app.core

import android.os.Environment
import android.util.Log
import java.io.File
import java.util.Properties

/**
 * 跨进程配置管理器
 * 优先读取宿主私有媒体目录配置文件（隔离性好）
 * 路径: /sdcard/Android/media/<packageName>/multihook.conf
 * 
 * 工业级安全加固：全链路异常全隔离，杜绝任何 SecurityException 导致 Hook 链中断
 */
object ConfigManager {
    private const val TAG = "HeHook_Config"
    private val memoryCache = mutableMapOf<String, String>()

    fun getBoolean(packageName: String, key: String, defaultValue: Boolean = true): Boolean {
        return try {
            val value = getString(packageName, key)
            value?.toBooleanStrictOrNull() ?: defaultValue
        } catch (_: Throwable) {
            defaultValue
        }
    }

    fun setBoolean(packageName: String, key: String, value: Boolean) {
        try {
            setString(packageName, key, value.toString())
        } catch (_: Throwable) {}
    }

    fun getString(packageName: String, key: String): String? {
        return try {
            val cacheKey = "$packageName:$key"
            memoryCache[cacheKey]?.let { return it }

            val props = loadProps(packageName)
            val value = props.getProperty(key)
            if (value != null) {
                memoryCache[cacheKey] = value
            }
            value
        } catch (_: Throwable) {
            null
        }
    }

    fun setString(packageName: String, key: String, value: String) {
        try {
            val cacheKey = "$packageName:$key"
            memoryCache[cacheKey] = value

            val props = loadProps(packageName)
            props.setProperty(key, value)
            saveProps(packageName, props)
        } catch (_: Throwable) {}
    }

    private fun getConfigFile(packageName: String): File? {
        return try {
            val dir = File(Environment.getExternalStorageDirectory(), "Android/media/$packageName")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            File(dir, "multihook.conf")
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadProps(packageName: String): Properties {
        val props = Properties()
        try {
            val file = getConfigFile(packageName) ?: return props
            if (file.exists() && file.canRead()) {
                file.inputStream().use { props.load(it) }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load config for $packageName: ${e.message}")
        }
        return props
    }

    private fun saveProps(packageName: String, props: Properties) {
        try {
            val file = getConfigFile(packageName) ?: return
            file.outputStream().use { props.store(it, "HeHook Configuration") }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to save config for $packageName: ${e.message}")
        }
    }
}
