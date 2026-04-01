package com.vesaa.mytv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * 解析本机可用于局域网访问的 IPv4，优先当前默认网络的地址。
 */
object LanIpResolver {

    @SuppressLint("MissingPermission")
    fun lanIPv4Candidates(context: Context): List<String> {
        val ordered = LinkedHashSet<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            if (network != null) {
                cm.getLinkProperties(network)?.linkAddresses?.forEach { la ->
                    val a = la.address
                    if (!a.isLoopbackAddress && a is Inet4Address) {
                        a.hostAddress?.takeIf { it.isNotBlank() }?.let { ordered.add(it) }
                    }
                }
            }
        }

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val rest = mutableListOf<Pair<Int, String>>()
            for (nif in interfaces) {
                if (nif.isLoopback || !nif.isUp) continue
                val score = interfaceSortScore(nif.name)
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr.isLoopbackAddress || addr !is Inet4Address) continue
                    addr.hostAddress?.takeIf { it.isNotBlank() }?.let { ip ->
                        if (!ordered.contains(ip)) {
                            rest.add(score to ip)
                        }
                    }
                }
            }
            rest.sortedBy { it.first }.map { it.second }.forEach { ordered.add(it) }
        } catch (_: Exception) {
        }

        return ordered.toList()
    }

    /** 越小越优先：以太网、Wi‑Fi 常见命名靠前 */
    private fun interfaceSortScore(name: String): Int {
        val n = name.lowercase()
        return when {
            n.startsWith("eth") -> 0
            n.startsWith("en") -> 1
            n.startsWith("wlan") -> 2
            n.startsWith("wifi") -> 2
            n.startsWith("ap") -> 3
            n.startsWith("rndis") -> 4
            else -> 10
        }
    }
}
