package top.hehook.app.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import top.hehook.app.core.ConfigManager
import top.hehook.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initHongGuoSettings()
        initFanQieSettings()
        initBiliSettings()
    }

    private fun initHongGuoSettings() {
        val pkg = "com.phoenix.read"
        with(binding) {
            switchHongGuo1080p.isChecked = ConfigManager.getBoolean(pkg, "force_1080p", true)
            switchHongGuo1080p.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "force_1080p", isChecked)
            }

            switchHongGuoVip.isChecked = ConfigManager.getBoolean(pkg, "unlock_vip", true)
            switchHongGuoVip.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "unlock_vip", isChecked)
            }

            switchHongGuoAds.isChecked = ConfigManager.getBoolean(pkg, "remove_ads", true)
            switchHongGuoAds.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "remove_ads", isChecked)
            }

            switchHongGuoHotfix.isChecked = ConfigManager.getBoolean(pkg, "disable_hotfix", true)
            switchHongGuoHotfix.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "disable_hotfix", isChecked)
            }

            btnRestartHongGuo.setOnClickListener {
                restartPackage(pkg, "红果短剧")
            }
        }
    }

    private fun initFanQieSettings() {
        val pkg = "com.dragon.read"
        with(binding) {
            switchFanQieVip.isChecked = ConfigManager.getBoolean(pkg, "unlock_reader_vip", true)
            switchFanQieVip.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "unlock_reader_vip", isChecked)
            }

            switchFanQieAds.isChecked = ConfigManager.getBoolean(pkg, "remove_reader_ads", true)
            switchFanQieAds.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "remove_reader_ads", isChecked)
            }

            switchFanQieTts.isChecked = ConfigManager.getBoolean(pkg, "unlock_tts_timbre", true)
            switchFanQieTts.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "unlock_tts_timbre", isChecked)
            }

            switchFanQiePendant.isChecked = ConfigManager.getBoolean(pkg, "hide_floating_pendant", true)
            switchFanQiePendant.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "hide_floating_pendant", isChecked)
            }

            btnRestartFanQie.setOnClickListener {
                restartPackage(pkg, "番茄小说")
            }
        }
    }

    private fun initBiliSettings() {
        val pkg = "tv.danmaku.bili"
        with(binding) {
            switchBiliSplash.isChecked = ConfigManager.getBoolean(pkg, "remove_splash_ad", true)
            switchBiliSplash.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "remove_splash_ad", isChecked)
            }

            switchBiliFeedAds.isChecked = ConfigManager.getBoolean(pkg, "remove_feed_ads", true)
            switchBiliFeedAds.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "remove_feed_ads", isChecked)
            }

            switchBiliBgPlay.isChecked = ConfigManager.getBoolean(pkg, "unlock_background_play", true)
            switchBiliBgPlay.setOnCheckedChangeListener { _, isChecked ->
                ConfigManager.setBoolean(pkg, "unlock_background_play", isChecked)
            }

            btnRestartBili.setOnClickListener {
                restartPackage(pkg, "哔哩哔哩")
            }
        }
    }

    private fun restartPackage(packageName: String, appName: String) {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(packageName)
        Toast.makeText(this, "已强退 $appName，重新打开即可生效", Toast.LENGTH_SHORT).show()
    }
}
