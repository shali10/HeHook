package top.multihook.app.core

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.util.Properties

/**
 * 跨进程配置管理器
 * 优先读取宿主私有媒体目录配置文件（无需多余权限，隔离性好）
 * 路径: /sdcard/Android/media/<packageName>/multihook.conf
 */
object ConfigManager {
    private const val TAG = "MultiHook_Config"
    private val memoryCache = mutableMapOf<String, String>()

    fun getBoolean(packageName: String, key: String, defaultValue: Boolean = true): Boolean {
        val value = getString(packageName, key)
        return value?.toBooleanStrictOrNull() ?: defaultValue
    }

    fun setBoolean(packageName: String, key: String, value: Boolean) {
        setString(packageName, key, value.toString())
    }

    fun getString(packageName: String, key: String): String? {
        val cacheKey = "$packageName:$key"
        memoryCache[cacheKey]?.let { return it }

        val props = loadProps(packageName)
        val value = props.getProperty(key)
        if (value != null) {
            memoryCache[cacheKey] = value
        }
        return value
    }

    fun setString(packageName: String, key: String, value: String) {
        val cacheKey = "$packageName:$key"
        memoryCache[cacheKey] = value

        val props = loadProps(packageName)
        props.setProperty(key, value)
        saveProps(packageName, props)
    }

    private fun getConfigFile(packageName: String): File {
        val dir = File(Environment.getExternalStorageDirectory(), "Android/media/$packageName")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "multihook.conf")
    }

    private fun loadProps(packageName: String): Properties {
        val props = Properties()
        val file = getConfigFile(packageName)
        if (file.exists() && file.canRead()) {
            try {
                file.inputStream().use { props.load(it) }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load config for $packageName", e)
            }
        }
        return props
    }

    private fun saveProps(packageName: String, props: Properties) {
        val file = getConfigFile(packageName)
        try {
            file.outputStream().use { props.store(it, "MultiHook Configuration") }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save config for $packageName", e)
        }
    }
}
