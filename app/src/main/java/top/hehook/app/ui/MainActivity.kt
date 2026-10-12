package top.hehook.app.ui

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import top.hehook.app.R
import top.hehook.app.core.ConfigManager
import top.hehook.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        const val PKG_HONGGUO = "com.phoenix.read"
        const val PKG_FANQIE = "com.dragon.read"
        const val PKG_BILI = "tv.danmaku.bili"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initHeaderActions()
        initHongGuoSettings()
        initFanQieSettings()
        initBiliSettings()
        updateInstalledStatus()
    }

    override fun onResume() {
        super.onResume()
        updateInstalledStatus()
    }

    private fun initHeaderActions() {
        binding.btnAbout.setOnClickListener {
            showAboutDialog()
        }

        binding.btnRestartAll.setOnClickListener {
            restartAllApps()
        }
    }

    private fun updateInstalledStatus() {
        // 红果状态
        val hg = checkAppInstalled(PKG_HONGGUO)
        binding.tvHongGuoStatus.text = hg.second
        if (hg.first) {
            binding.tvHongGuoStatus.setBackgroundResource(R.drawable.bg_chip_status)
            binding.tvHongGuoStatus.setTextColor(getColor(R.color.status_active))
        } else {
            binding.tvHongGuoStatus.setBackgroundColor(getColor(R.color.surface_variant))
            binding.tvHongGuoStatus.setTextColor(getColor(R.color.text_tertiary))
        }

        // 番茄状态
        val fq = checkAppInstalled(PKG_FANQIE)
        binding.tvFanQieStatus.text = fq.second
        if (fq.first) {
            binding.tvFanQieStatus.setBackgroundResource(R.drawable.bg_chip_status)
            binding.tvFanQieStatus.setTextColor(getColor(R.color.status_active))
        } else {
            binding.tvFanQieStatus.setBackgroundColor(getColor(R.color.surface_variant))
            binding.tvFanQieStatus.setTextColor(getColor(R.color.text_tertiary))
        }

        // B站状态
        val bili = checkAppInstalled(PKG_BILI)
        binding.tvBiliStatus.text = bili.second
        if (bili.first) {
            binding.tvBiliStatus.setBackgroundResource(R.drawable.bg_chip_status)
            binding.tvBiliStatus.setTextColor(getColor(R.color.status_active))
        } else {
            binding.tvBiliStatus.setBackgroundColor(getColor(R.color.surface_variant))
            binding.tvBiliStatus.setTextColor(getColor(R.color.text_tertiary))
        }
    }

    private fun checkAppInstalled(packageName: String): Pair<Boolean, String> {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            Pair(true, "已安装 v${pInfo.versionName}")
        } catch (_: PackageManager.NameNotFoundException) {
            Pair(false, "未安装")
        } catch (_: Throwable) {
            Pair(false, "未检测到")
        }
    }

    private fun initHongGuoSettings() {
        with(binding) {
            switchHongGuo1080p.isChecked = ConfigManager.getBoolean(PKG_HONGGUO, "force_1080p", true)
            switchHongGuo1080p.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_HONGGUO, "force_1080p", isChecked)
            }

            switchHongGuoVip.isChecked = ConfigManager.getBoolean(PKG_HONGGUO, "unlock_vip", true)
            switchHongGuoVip.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_HONGGUO, "unlock_vip", isChecked)
            }

            switchHongGuoAds.isChecked = ConfigManager.getBoolean(PKG_HONGGUO, "remove_ads", true)
            switchHongGuoAds.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_HONGGUO, "remove_ads", isChecked)
            }

            switchHongGuoHotfix.isChecked = ConfigManager.getBoolean(PKG_HONGGUO, "disable_hotfix", true)
            switchHongGuoHotfix.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_HONGGUO, "disable_hotfix", isChecked)
            }

            btnRestartHongGuo.setOnClickListener {
                restartPackage(PKG_HONGGUO, "红果短剧")
            }
        }
    }

    private fun initFanQieSettings() {
        with(binding) {
            switchFanQieVip.isChecked = ConfigManager.getBoolean(PKG_FANQIE, "unlock_reader_vip", true)
            switchFanQieVip.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_FANQIE, "unlock_reader_vip", isChecked)
            }

            switchFanQieAds.isChecked = ConfigManager.getBoolean(PKG_FANQIE, "remove_reader_ads", true)
            switchFanQieAds.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_FANQIE, "remove_reader_ads", isChecked)
            }

            switchFanQieTts.isChecked = ConfigManager.getBoolean(PKG_FANQIE, "unlock_tts_timbre", true)
            switchFanQieTts.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_FANQIE, "unlock_tts_timbre", isChecked)
            }

            switchFanQiePendant.isChecked = ConfigManager.getBoolean(PKG_FANQIE, "hide_floating_pendant", true)
            switchFanQiePendant.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_FANQIE, "hide_floating_pendant", isChecked)
            }

            btnRestartFanQie.setOnClickListener {
                restartPackage(PKG_FANQIE, "番茄小说")
            }
        }
    }

    private fun initBiliSettings() {
        with(binding) {
            switchBiliSplash.isChecked = ConfigManager.getBoolean(PKG_BILI, "remove_splash_ad", true)
            switchBiliSplash.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_BILI, "remove_splash_ad", isChecked)
            }

            switchBiliFeedAds.isChecked = ConfigManager.getBoolean(PKG_BILI, "remove_feed_ads", true)
            switchBiliFeedAds.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_BILI, "remove_feed_ads", isChecked)
            }

            switchBiliBgPlay.isChecked = ConfigManager.getBoolean(PKG_BILI, "unlock_background_play", true)
            switchBiliBgPlay.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(PKG_BILI, "unlock_background_play", isChecked)
            }

            btnRestartBili.setOnClickListener {
                restartPackage(PKG_BILI, "哔哩哔哩")
            }
        }
    }

    private fun restartPackage(packageName: String, appName: String) {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(packageName)
        Snackbar.make(binding.root, "已重启 $appName 进程，重新打开即可生效", Snackbar.LENGTH_SHORT).show()
    }

    private fun restartAllApps() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(PKG_HONGGUO)
        am.killBackgroundProcesses(PKG_FANQIE)
        am.killBackgroundProcesses(PKG_BILI)
        Snackbar.make(binding.root, "已一键重启全部受控应用（红果/番茄/B站）", Snackbar.LENGTH_SHORT).show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("关于 HeHook 聚合助手")
            .setMessage(
                """
                【版本】v1.0.2 (Build 102)
                【标准】Modern LibXposed API 101
                【内核】DexKit C++ 2.0.2 字节码模式匹配通杀引擎

                【功能亮点】
                - 🎬 红果短剧：强制 1080P 超清、全域贴片/Lynx 卡片广告截断、免广特权、屏蔽 Reparo 热更新对抗。
                - 📖 番茄小说：官方纯净阅读流、末尾插页净化、AI 听书高品质多音色解锁、悬浮挂件屏蔽。
                - 📺 哔哩哔哩：冷热启动开屏秒过、首页推荐卡片去推广、锁屏后台持续音频播放。

                【特性】
                全版本特征定位，无惧官方日常小版本混淆升级。
                """.trimIndent()
            )
            .setPositiveButton("好的", null)
            .show()
    }
}
