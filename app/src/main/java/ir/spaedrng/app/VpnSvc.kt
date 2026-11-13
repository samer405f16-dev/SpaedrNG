package ir.spaedrng.app

import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

// status: 0 = وصل نیست, 1 = درحال وصل شدن, 2 = وصل شد, 3 = وصل نشد
object Core {
    @Volatile var status = 0
    var listener: (() -> Unit)? = null
    fun set(s: Int) {
        status = s
        Handler(Looper.getMainLooper()).post { listener?.invoke() }
    }
}

class VpnSvc : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var proc: Process? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopAll(0); return START_NOT_STICKY }
        val link = intent?.getStringExtra("link") ?: return START_NOT_STICKY
        thread { start(link) }
        return START_NOT_STICKY
    }

    private fun start(link: String) {
        stopAll(1)
        Core.set(1)
        running = true
        try {
            val cfg = File(filesDir, "xray.json")
            cfg.writeText(Parser.toXray(link))
            val xray = File(applicationInfo.nativeLibraryDir, "libxray.so")
            proc = ProcessBuilder(xray.path, "run", "-c", cfg.path)
                .directory(filesDir).redirectErrorStream(true).start()
            val p = proc!!
            thread { try { val buf = ByteArray(4096); while (p.inputStream.read(buf) >= 0) {} } catch (e: Exception) {} }

            var ok = false
            for (i in 0 until 40) {
                if (!p.isAlive) break
                try {
                    Socket().use { it.connect(InetSocketAddress("127.0.0.1", 10808), 200); ok = true }
                } catch (e: Exception) { Thread.sleep(150) }
                if (ok) break
            }
            if (!ok) throw Exception("core failed")

            val b = Builder().setSession("SpaedrNG").setMtu(1500)
                .addAddress("26.26.26.1", 30).addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1").addDnsServer("8.8.8.8")
            b.addDisallowedApplication(packageName) // core traffic bypasses the VPN (no loop)
            tun = b.establish() ?: throw Exception("vpn denied")

            val y = File(filesDir, "tun.yml")
            y.writeText("tunnel:\n  mtu: 1500\nsocks5:\n  port: 10808\n  address: 127.0.0.1\n  udp: 'udp'\n")
            TProxyService.TProxyStartService(y.absolutePath, tun!!.fd)
            Core.set(2)
        } catch (e: Throwable) {
            stopAll(3)
        }
    }

    private fun stopAll(final: Int) {
        try { if (running) TProxyService.TProxyStopService() } catch (e: Throwable) {}
        try { tun?.close() } catch (e: Exception) {}
        tun = null
        try { proc?.destroy() } catch (e: Exception) {}
        proc = null
        running = false
        if (final != 1) { Core.set(final); stopSelf() }
    }

    override fun onRevoke() { stopAll(0) }
    override fun onDestroy() { stopAll(1); super.onDestroy() }
}
