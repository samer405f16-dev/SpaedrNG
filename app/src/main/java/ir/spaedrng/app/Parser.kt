package ir.spaedrng.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

object Parser {
    private val schemes = listOf("vless://", "vmess://", "trojan://", "ss://")
    fun valid(l: String) = schemes.any { l.startsWith(it) }

    fun name(link: String): String = try {
        if (link.startsWith("vmess://")) JSONObject(b64(link.substring(8))).optString("ps", "vmess")
        else {
            val i = link.indexOf('#')
            if (i >= 0 && i < link.length - 1) URLDecoder.decode(link.substring(i + 1), "UTF-8")
            else link.substringBefore("://").uppercase()
        }
    } catch (e: Exception) { "Config" }

    private fun query(u: Uri): Map<String, String> {
        val m = HashMap<String, String>()
        for (k in u.queryParameterNames) m[k] = u.getQueryParameter(k) ?: ""
        return m
    }

    private fun stream(p: Map<String, String>, addr: String): JSONObject {
        var net = p["type"].orEmpty().ifEmpty { "tcp" }
        if (net == "h2") net = "http"
        if (net == "splithttp") net = "xhttp"
        val s = JSONObject().put("network", net)
        val host = p["host"].orEmpty()
        val path = p["path"].orEmpty()
        when (net) {
            "ws" -> {
                val w = JSONObject().put("path", path.ifEmpty { "/" })
                if (host.isNotEmpty()) w.put("headers", JSONObject().put("Host", host))
                s.put("wsSettings", w)
            }
            "httpupgrade" -> s.put("httpupgradeSettings",
                JSONObject().put("path", path.ifEmpty { "/" }).put("host", host))
            "xhttp" -> s.put("xhttpSettings",
                JSONObject().put("path", path.ifEmpty { "/" }).put("host", host).put("mode", p["mode"] ?: "auto"))
            "grpc" -> s.put("grpcSettings", JSONObject().put("serviceName", p["serviceName"].orEmpty()))
        }
        val sni = p["sni"].orEmpty()
        val fp = p["fp"].orEmpty()
        when (p["security"]) {
            "tls" -> {
                val t = JSONObject().put("serverName", sni.ifEmpty { host.ifEmpty { addr } })
                if (fp.isNotEmpty()) t.put("fingerprint", fp)
                val al = p["alpn"].orEmpty()
                if (al.isNotEmpty()) t.put("alpn", JSONArray(al.split(",")))
                if (p["allowInsecure"] == "1" || p["allowInsecure"] == "true") t.put("allowInsecure", true)
                s.put("security", "tls").put("tlsSettings", t)
            }
            "reality" -> s.put("security", "reality").put("realitySettings",
                JSONObject().put("serverName", sni).put("fingerprint", fp.ifEmpty { "chrome" })
                    .put("publicKey", p["pbk"].orEmpty()).put("shortId", p["sid"].orEmpty())
                    .put("spiderX", p["spx"].orEmpty()))
        }
        return s
    }

    private fun outbound(proto: String, settings: JSONObject, stream: JSONObject) =
        JSONObject().put("tag", "proxy").put("protocol", proto)
            .put("settings", settings).put("streamSettings", stream)

    private fun vless(link: String): JSONObject {
        val u = Uri.parse(link); val p = query(u); val h = u.host!!
        val user = JSONObject().put("id", u.userInfo).put("encryption", p["encryption"] ?: "none")
            .put("flow", p["flow"].orEmpty())
        val st = JSONObject().put("vnext", JSONArray().put(
            JSONObject().put("address", h).put("port", u.port).put("users", JSONArray().put(user))))
        return outbound("vless", st, stream(p, h))
    }

    private fun vmess(link: String): JSONObject {
        val j = JSONObject(b64(link.substring(8)))
        val h = j.getString("add")
        val p = hashMapOf(
            "type" to j.optString("net", "tcp"), "host" to j.optString("host"),
            "path" to j.optString("path"), "sni" to j.optString("sni"),
            "alpn" to j.optString("alpn"), "fp" to j.optString("fp"),
            "security" to if (j.optString("tls") == "tls") "tls" else "none")
        val user = JSONObject().put("id", j.getString("id"))
            .put("alterId", j.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", j.optString("scy").ifEmpty { "auto" })
        val st = JSONObject().put("vnext", JSONArray().put(
            JSONObject().put("address", h).put("port", j.optString("port").toInt())
                .put("users", JSONArray().put(user))))
        return outbound("vmess", st, stream(p, h))
    }

    private fun trojan(link: String): JSONObject {
        val u = Uri.parse(link); val p = HashMap(query(u)); val h = u.host!!
        if (!p.containsKey("security")) p["security"] = "tls"
        val st = JSONObject().put("servers", JSONArray().put(
            JSONObject().put("address", h).put("port", u.port).put("password", u.userInfo)))
        return outbound("trojan", st, stream(p, h))
    }

    private fun ss(link: String): JSONObject {
        val body = link.removePrefix("ss://").substringBefore('#').substringBefore('?')
        val cred: String; val hp: String
        if (body.contains('@')) {
            val ui = body.substringBefore('@')
            cred = if (ui.contains(':')) URLDecoder.decode(ui, "UTF-8") else b64(ui)
            hp = body.substringAfterLast('@')
        } else {
            val d = b64(body)
            cred = d.substringBeforeLast('@'); hp = d.substringAfterLast('@')
        }
        val st = JSONObject().put("servers", JSONArray().put(
            JSONObject().put("address", hp.substringBeforeLast(':'))
                .put("port", hp.substringAfterLast(':').toInt())
                .put("method", cred.substringBefore(':')).put("password", cred.substringAfter(':'))))
        return outbound("shadowsocks", st, JSONObject().put("network", "tcp"))
    }

    fun toXray(link: String): String {
        val ob = when {
            link.startsWith("vless://") -> vless(link)
            link.startsWith("vmess://") -> vmess(link)
            link.startsWith("trojan://") -> trojan(link)
            link.startsWith("ss://") -> ss(link)
            else -> throw Exception("unsupported")
        }
        val inbound = JSONObject().put("tag", "socks").put("listen", "127.0.0.1").put("port", 10808)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            .put("sniffing", JSONObject().put("enabled", true)
                .put("destOverride", JSONArray().put("http").put("tls")))
        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8")))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", JSONArray().put(ob)
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom")))
            .toString()
    }
}
