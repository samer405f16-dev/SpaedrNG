package ir.spaedrng.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val BG = Color.parseColor("#0E1224")
    private val CARD = Color.parseColor("#1A2040")
    private val ACC = Color.parseColor("#7C5CFF")
    private val DIM = Color.parseColor("#9AA3C7")

    private lateinit var connBtn: TextView
    private lateinit var statusTv: TextView
    private lateinit var selTv: TextView
    private lateinit var listBox: LinearLayout

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun tv(t: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun bg(color: Int, r: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
        if (stroke > 0) setStroke(dp(stroke), strokeColor)
    }

    private fun btn(t: String, onClick: () -> Unit) = tv(t, 15f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(14), dp(12), dp(14), dp(12))
        background = bg(ACC, 14); setOnClickListener { onClick() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        window.statusBarColor = BG; window.navigationBarColor = BG
        intro()
    }

    override fun onDestroy() { Core.listener = null; super.onDestroy() }

    // ---------- صفحه ورود ----------
    private fun intro() {
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(BG); setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        l.addView(tv("SpaedrNG", 40f, ACC, true).apply { gravity = Gravity.CENTER })
        l.addView(tv("این یک کلاینت قوی و پرسرعت است\nو به مرور زمان بهش چیز های بیشتری اضافه خواهد شد",
            16f, Color.WHITE).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(32)); setLineSpacing(0f, 1.3f)
        })
        l.addView(btn("ورود به برنامه") { main() }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        l.addView(tv("سازنده: @spaedrNG", 14f, DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0)
            setOnClickListener { openTg() }
        })
        setContentView(l)
    }

    private fun openTg() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/spaedrNG"))) } catch (e: Exception) {}
    }

    // ---------- صفحه اصلی ----------
    private fun main() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(BG)
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(tv("SpaedrNG", 22f, ACC, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        top.addView(tv("@spaedrNG", 13f, DIM).apply { setOnClickListener { openTg() } })
        root.addView(top)

        connBtn = tv("⏻", 54f, Color.WHITE).apply {
            gravity = Gravity.CENTER; setOnClickListener { onConnect() }
        }
        root.addView(connBtn, LinearLayout.LayoutParams(dp(150), dp(150)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(20)
        })
        statusTv = tv("", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(2)) }
        root.addView(statusTv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        selTv = tv("", 13f, DIM).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14)) }
        root.addView(selTv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val row = LinearLayout(this)
        row.addView(btn("➕ افزودن کانفیگ") { addConfigDialog() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        row.addView(btn("➕ افزودن ساب") { addSubDialog() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        root.addView(row)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply { addView(listBox); setPadding(0, dp(12), 0, 0) }
        root.addView(sv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        Core.listener = { refreshStatus() }
        refreshStatus(); refreshList()
    }

    private fun refreshStatus() {
        val (txt, col) = when (Core.status) {
            1 -> "درحال وصل شدن 🟡" to Color.parseColor("#F5B301")
            2 -> "وصل شد 🟢" to Color.parseColor("#22C55E")
            3 -> "وصل نشد 🔴" to Color.parseColor("#EF4444")
            else -> "وصل نیست ⚪️" to Color.parseColor("#6B7399")
        }
        statusTv.text = txt
        connBtn.background = bg(CARD, 75, 4, col)
        selTv.text = if (Store.sel in Store.cfgs.indices) Store.cfgs[Store.sel].name else "کانفیگی انتخاب نشده"
    }

    private fun onConnect() {
        if (Core.status == 1 || Core.status == 2) {
            startService(Intent(this, VpnSvc::class.java).setAction("stop")); return
        }
        if (Store.sel !in Store.cfgs.indices) { toast("اول یک کانفیگ اضافه کن"); return }
        val i = VpnService.prepare(this)
        if (i != null) startActivityForResult(i, 1) else startVpn()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK) startVpn()
    }

    private fun startVpn() {
        Core.set(1)
        startService(Intent(this, VpnSvc::class.java).putExtra("link", Store.cfgs[Store.sel].link))
    }

    // ---------- لیست ----------
    private fun fmt(b: Long): String =
        if (b >= 1L shl 30) String.format(Locale.US, "%.2f GB", b / 1073741824.0)
        else String.format(Locale.US, "%.1f MB", b / 1048576.0)

    private fun volText(s: Sub): String {
        if (s.total < 0) return ""
        var t = if (s.total == 0L) "📊 حجم نامحدود • مصرف ${fmt(s.used)}"
        else "📊 مصرف ${fmt(s.used)} از ${fmt(s.total)} • باقی‌مانده ${fmt(maxOf(0, s.total - s.used))}"
        if (s.expire > 0) {
            val days = (s.expire * 1000 - System.currentTimeMillis()) / 86400000
            t += "\n⏳ " + SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(s.expire * 1000)) +
                if (days >= 0) " (${days} روز مانده)" else " (منقضی شده)"
        }
        return t
    }

    private fun refreshList() {
        listBox.removeAllViews()
        if (Store.cfgs.isEmpty()) {
            listBox.addView(tv("هنوز کانفیگی اضافه نشده", 14f, DIM).apply { gravity = Gravity.CENTER; setPadding(0, dp(30), 0, 0) },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        for (s in Store.subs) {
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = bg(CARD, 14); setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            head.addView(tv("🔗 ${s.name}", 16f, Color.WHITE, true))
            val v = volText(s)
            if (v.isNotEmpty()) head.addView(tv(v, 12f, Color.parseColor("#7CE0A3")).apply { setPadding(0, dp(4), 0, 0) })
            val act = LinearLayout(this).apply { setPadding(0, dp(8), 0, 0) }
            act.addView(tv("🔄 به‌روزرسانی", 13f, ACC, true).apply {
                setPadding(0, 0, dp(18), 0); setOnClickListener { updateSub(s) }
            })
            act.addView(tv("🗑 حذف", 13f, Color.parseColor("#EF4444"), true).apply {
                setOnClickListener {
                    Store.cfgs.removeAll { it.sub == s.id }; Store.subs.remove(s)
                    if (Store.sel !in Store.cfgs.indices) Store.sel = if (Store.cfgs.isEmpty()) -1 else 0
                    Store.save(); refreshList(); refreshStatus()
                }
            })
            head.addView(act)
            listBox.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
            Store.cfgs.filter { it.sub == s.id }.forEach { addCfgRow(it, v.lines().firstOrNull().orEmpty()) }
        }
        val single = Store.cfgs.filter { it.sub == null }
        if (single.isNotEmpty() && Store.subs.isNotEmpty())
            listBox.addView(tv("کانفیگ‌های تکی", 13f, DIM).apply { setPadding(dp(4), dp(14), 0, 0) })
        single.forEach { addCfgRow(it, "") }
    }

    private fun addCfgRow(c: Cfg, vol: String) {
        val idx = Store.cfgs.indexOf(c)
        val on = idx == Store.sel
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(if (on) Color.parseColor("#2B2563") else CARD, 12, if (on) 2 else 0, ACC)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        r.addView(tv((if (on) "✅ " else "") + c.name, 14f, Color.WHITE, on))
        if (vol.isNotEmpty()) r.addView(tv(vol, 11f, DIM))
        r.setOnClickListener {
            if (Core.status == 1 || Core.status == 2) { toast("اول اتصال رو قطع کن"); return@setOnClickListener }
            Store.sel = idx; Store.save(); refreshList(); refreshStatus()
        }
        r.setOnLongClickListener {
            AlertDialog.Builder(this).setMessage("این کانفیگ حذف بشه؟")
                .setPositiveButton("حذف") { _, _ ->
                    Store.cfgs.remove(c)
                    if (Store.sel !in Store.cfgs.indices) Store.sel = if (Store.cfgs.isEmpty()) -1 else 0
                    Store.save(); refreshList(); refreshStatus()
                }.setNegativeButton("نه", null).show()
            true
        }
        listBox.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
    }

    // ---------- دیالوگ‌ها ----------
    private fun input(hint: String, multi: Boolean): EditText = EditText(this).apply {
        this.hint = hint
        inputType = if (multi) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        if (multi) minLines = 3
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }

    private fun addConfigDialog() {
        val e = input("کانفیگ رایگان از کیبورد وارد کنید", true)
        AlertDialog.Builder(this).setTitle("افزودن کانفیگ").setView(e)
            .setPositiveButton("افزودن") { _, _ ->
                val n = Store.addLinks(e.text.toString().lines().map { it.trim() })
                toast(if (n > 0) "$n کانفیگ اضافه شد" else "کانفیگ معتبر نبود")
                refreshList(); refreshStatus()
            }.setNegativeButton("لغو", null).show()
    }

    private fun addSubDialog() {
        val e = input("لینک ساب خود را وارد کنید", false)
        AlertDialog.Builder(this).setTitle("افزودن ساب").setView(e)
            .setPositiveButton("افزودن") { _, _ ->
                val url = e.text.toString().trim()
                if (!url.startsWith("http")) { toast("لینک ساب معتبر نیست"); return@setPositiveButton }
                toast("درحال دریافت…")
                thread {
                    try {
                        val (sub, links) = Store.fetchSub(url, null)
                        runOnUiThread {
                            Store.subs.add(sub)
                            val n = Store.addLinks(links, sub.id)
                            toast("$n کانفیگ اضافه شد"); refreshList(); refreshStatus()
                        }
                    } catch (ex: Exception) { runOnUiThread { toast("دریافت ساب ناموفق بود") } }
                }
            }.setNegativeButton("لغو", null).show()
    }

    private fun updateSub(s: Sub) {
        toast("درحال به‌روزرسانی…")
        thread {
            try {
                val (sub, links) = Store.fetchSub(s.url, s)
                runOnUiThread {
                    val keepSel = Store.cfgs.getOrNull(Store.sel)?.link
                    Store.cfgs.removeAll { it.sub == sub.id }
                    Store.addLinks(links, sub.id)
                    Store.sel = Store.cfgs.indexOfFirst { it.link == keepSel }.let { if (it < 0) (if (Store.cfgs.isEmpty()) -1 else 0) else it }
                    Store.save(); refreshList(); refreshStatus(); toast("به‌روز شد")
                }
            } catch (ex: Exception) { runOnUiThread { toast("به‌روزرسانی ناموفق بود") } }
        }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
