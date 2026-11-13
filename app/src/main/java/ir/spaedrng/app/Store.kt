package ir.spaedrng.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Cfg(val name: String, val link: String, val sub: String?)
data class Sub(val id: String, var name: String, val url: String,
               var used: Long, var total: Long, var expire: Long)

fun b64(s: String): String {
    var x = s.replace(Regex("\\s"), "").replace('-', '+').replace('_', '/')
    while (x.length % 4 != 0) x += "="
    return String(Base64.decode(x, Base64.DEFAULT))
}

object Store {
    lateinit var sp: SharedPreferences
    val cfgs = ArrayList<Cfg>()
    val subs = ArrayList<Sub>()
    var sel = -1

    fun init(c: Context) {
        sp = c.getSharedPreferences("spaedrng", 0)
        cfgs.clear(); subs.clear()
        val a = JSONArray(sp.getString("cfgs", "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            cfgs.add(Cfg(o.getString("n"), o.getString("l"), o.optString("s").ifEmpty { null }))
        }
        val b = JSONArray(sp.getString("subs", "[]"))
        for (i in 0 until b.length()) {
            val o = b.getJSONObject(i)
            subs.add(Sub(o.getString("id"), o.getString("n"), o.getString("u"),
                o.optLong("used", 0), o.optLong("total", -1), o.optLong("exp", 0)))
        }
        sel = sp.getInt("sel", -1)
        if (sel !in cfgs.indices) sel = -1
    }

    fun save() {
        val a = JSONArray()
        cfgs.forEach { a.put(JSONObject().put("n", it.name).put("l", it.link).put("s", it.sub ?: "")) }
        val b = JSONArray()
        subs.forEach {
            b.put(JSONObject().put("id", it.id).put("n", it.name).put("u", it.url)
                .put("used", it.used).put("total", it.total).put("exp", it.expire))
        }
        sp.edit().putString("cfgs", a.toString()).putString("subs", b.toString()).putInt("sel", sel).apply()
    }

    fun addLinks(links: List<String>, subId: String? = null): Int {
        var n = 0
        for (l in links) {
            if (!Parser.valid(l)) continue
            if (cfgs.any { it.link == l && it.sub == subId }) continue
            cfgs.add(Cfg(Parser.name(l), l, subId)); n++
        }
        if (sel < 0 && cfgs.isNotEmpty()) sel = 0
        save()
        return n
    }

    // runs on a background thread
    fun fetchSub(url: String, existing: Sub?): Pair<Sub, List<String>> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "SpaedrNG/1.0")
        val body = c.inputStream.bufferedReader().readText().trim()
        val text = if (body.contains("://")) body else try { b64(body) } catch (e: Exception) { body }
        val links = text.lines().map { it.trim() }.filter { Parser.valid(it) }

        var used = -1L; var total = -1L; var exp = 0L
        c.getHeaderField("subscription-userinfo")?.split(";")?.forEach {
            val kv = it.trim().split("=")
            if (kv.size == 2) {
                val v = kv[1].trim().toDoubleOrNull()?.toLong() ?: 0L
                when (kv[0].trim()) {
                    "upload" -> used = (if (used < 0) 0 else used) + v
                    "download" -> used = (if (used < 0) 0 else used) + v
                    "total" -> total = v
                    "expire" -> exp = v
                }
            }
        }
        var title = c.getHeaderField("profile-title")
        if (title != null && title.startsWith("base64:")) title = try { b64(title.substring(7)) } catch (e: Exception) { null }
        val name = existing?.name ?: title ?: (URL(url).host ?: "Sub")
        val sub = existing ?: Sub(System.currentTimeMillis().toString(), name, url, 0, -1, 0)
        sub.used = if (used < 0) 0 else used
        sub.total = total
        sub.expire = exp
        return Pair(sub, links)
    }
}
