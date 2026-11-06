package top.hehook.app.ui

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import top.hehook.app.BuildConfig
import top.hehook.app.R
import top.hehook.app.core.AppSpec
import top.hehook.app.core.ConfigManager
import top.hehook.app.core.Feature
import top.hehook.app.core.PluginRegistry

/**
 * 管理端主界面。
 *
 * v1.5.0 起整页由 [PluginRegistry] 数据驱动、全部在代码里构建：
 * 加一个受控应用不用再改 XML、不用再补四张硬编码卡片。
 */
class MainActivity : AppCompatActivity() {

    private val cardBackgrounds = mutableListOf<Pair<LinearLayout, AppSpec>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildRoot())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ---------------------------------------------------------------- 构建界面

    private fun buildRoot(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(c("background"))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(36))
        }
        scroll.addView(root, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(buildHeader())
        root.addView(buildHero(), lp(top = 18f))

        PluginRegistry.apps.forEach { spec ->
            val card = buildAppCard(spec)
            root.addView(card, lp(top = 14f))
            cardBackgrounds += card to spec
        }

        root.addView(buildFooter(), lp(top = 22f))
        return scroll
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("HeHook", 26f, c("text_primary"), bold = true))
        titles.addView(
            text("按下即生效的聚合增强 · v${BuildConfig.VERSION_NAME}", 12.5f, c("text_tertiary"), topDp = 4),
        )
        row.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(chip("已就绪", c("ok"), ColorUtils.setAlphaComponent(c("ok"), 0x24)))
        return row
    }

    private fun buildHero(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                colors = intArrayOf(
                    ColorUtils.setAlphaComponent(c("accent"), 0x2E),
                    ColorUtils.setAlphaComponent(c("card"), 0xFF),
                )
                orientation = GradientDrawable.Orientation.TL_BR
                setStroke(dp(1), ColorUtils.setAlphaComponent(c("accent"), 0x44))
            }
        }

        val status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        status.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c("ok"))
            }
        }, LinearLayout.LayoutParams(dp(7), dp(7)).apply { rightMargin = dp(9) })
        status.addView(text("LSPosed 模块已注入，宿主启动即接管", 12.5f, c("text_secondary")))
        card.addView(status)

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        stats.addView(statBlock(PluginRegistry.apps.size.toString(), "托管应用"), weightP(1f))
        stats.addView(statBlock(PluginRegistry.featureCount.toString(), "增强策略"), weightP(1f))
        stats.addView(statBlock("101", "LibXposed API"), weightP(1f))
        card.addView(stats, lp(top = 16f))

        val restart = text("一键重启全部受控应用", 13.5f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(13), 0, dp(13))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(c("accent"))
            }
            setOnClickListener { restartAll() }
        }
        card.addView(restart, lp(top = 16f))
        return card
    }

    private fun statBlock(value: String, label: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(value, 22f, c("text_primary"), bold = true))
        box.addView(text(label, 11.5f, c("text_tertiary"), topDp = 3))
        return box
    }

    private fun buildAppCard(spec: AppSpec): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(c("card"))
                setStroke(dp(1), c("line"))
            }
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = c(spec.colorRes)
        val tile = text(spec.letter, 17f, brand, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                setColor(ColorUtils.setAlphaComponent(brand, 0x26))
                setStroke(dp(1), ColorUtils.setAlphaComponent(brand, 0x55))
            }
        }
        head.addView(tile, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(13) })

        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text(spec.name, 15.5f, c("text_primary"), bold = true))
        titles.addView(text(spec.tagline, 11.5f, c("text_tertiary"), topDp = 3))
        head.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))

        val statusChip = chip("检测中", c("text_tertiary"), c("line_soft"))
        statusChip.tag = "status_chip"
        head.addView(statusChip)

        card.addView(head)

        spec.features.forEach { feature ->
            card.addView(buildFeatureRow(spec, feature))
        }

        val divider = View(this).apply { setBackgroundColor(c("line_soft")) }
        card.addView(divider, LinearLayout.LayoutParams(MATCH, dp(1)).apply { topMargin = dp(10) })

        val restart = text("重启 ${spec.name}", 12.5f, c("text_secondary")).apply {
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { restartPackage(spec.pkg, spec.name) }
        }
        card.addView(restart)

        return card
    }

    private fun buildFeatureRow(spec: AppSpec, feature: Feature): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(feature.title, 13.5f, c("text_primary")))
        texts.addView(text(feature.desc, 11f, c("text_tertiary"), topDp = 2))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { topMargin = dp(12) })

        val switch = SwitchCompat(this).apply {
            isChecked = ConfigManager.getBoolean(spec.pkg, feature.key, feature.default)
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(c("accent"), c("text_tertiary"))
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ColorUtils.setAlphaComponent(c("accent"), 0x66), c("line"))
            )
            setOnCheckedChangeListener { _, checked ->
                ConfigManager.setBoolean(spec.pkg, feature.key, checked)
            }
        }
        row.addView(switch, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
        return row
    }

    private fun buildFooter(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(
            text(
                "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · 单入口 LibXposed API 101 · 零 SO 依赖",
                11f, c("text_tertiary")
            )
        )
        box.addView(
            text(
                "老版 XposedBridge 入口已移除：那条路径无法装载拦截后端，只会静默失效。请使用 LSPosed 1.9 及以上。",
                11f, c("text_tertiary"), topDp = 6
            )
        )
        box.addView(
            text("诊断：宿主启动后 LSPosed 日志搜索 HeHook 汇总，可见 applied / failed 计数。", 11f, c("text_tertiary"), topDp = 6)
                .apply { setOnClickListener { showAbout() } }
        )
        return box
    }

    // ---------------------------------------------------------------- 状态

    private fun refreshStatus() {
        cardBackgrounds.forEach { (card, spec) ->
            val chipView = card.findViewWithTag<TextView>("status_chip") ?: return@forEach
            val installed = installedVersion(spec.pkg)
            if (installed != null) {
                chipView.text = "已安装 v$installed"
                chipView.setTextColor(c("ok"))
                chipView.background = GradientDrawable().apply {
                    cornerRadius = dp(999).toFloat()
                    setColor(ColorUtils.setAlphaComponent(c("ok"), 0x22))
                }
            } else {
                chipView.text = "未安装"
                chipView.setTextColor(c("text_tertiary"))
                chipView.background = GradientDrawable().apply {
                    cornerRadius = dp(999).toFloat()
                    setColor(c("line_soft"))
                }
            }
        }
    }

    private fun installedVersion(pkg: String): String? = try {
        packageManager.getPackageInfo(pkg, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Throwable) {
        null
    }

    private fun restartPackage(pkg: String, label: String) {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(pkg)
        toast("已重启 $label，重新打开即可生效")
    }

    private fun restartAll() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        PluginRegistry.apps.forEach { am.killBackgroundProcesses(it.pkg) }
        toast("已重启全部受控应用")
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("HeHook v${BuildConfig.VERSION_NAME}")
            .setMessage(
                """
                【架构】Modern LibXposed API 101 单入口 · 零 SO 依赖 · 纯反射调用
                【注册】应用清单与开关由 PluginRegistry 单表驱动，路由 / 作用域 / 界面同源
                【诊断】每次接管都会输出一行 HeHook 汇总：applied / failed / missing

                【v1.5.0 改动】
                - 修复老入口下拦截后端未初始化、所有 hook 静默失效
                - 移除老版 XposedBridge 入口，只保留现代 API 单入口
                - 全部诊断日志改为 error 级，LSPosed 日志页可见
                - 管理端重做：数据驱动卡片、实时安装状态与逐项开关
                - 签名密钥移出公开仓库，改用独立密钥与新指纹
                """.trimIndent()
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- 工具

    private fun c(res: Int): Int = getColor(res)

    private fun c(name: String): Int = getColor(resources.getIdentifier(name, "color", packageName))

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun text(
        s: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        topDp: Int = 0
    ): TextView = TextView(this).apply {
        text = s
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        if (topDp > 0) setPadding(0, dp(topDp), 0, 0)
    }

    private fun chip(label: String, textColor: Int, bgColor: Int): TextView = TextView(this).apply {
        text = label
        textSize = 11f
        setTextColor(textColor)
        setPadding(dp(10), dp(5), dp(10), dp(5))
        background = GradientDrawable().apply {
            cornerRadius = dp(999).toFloat()
            setColor(bgColor)
        }
    }

    private fun lp(top: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }

    private fun weightP(w: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, WRAP, w)

    private fun space(h: Int): Space = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
