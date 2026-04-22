package com.vesaa.mytv.ui.screens.leanback.video.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * 自定义 UDP/组播 DataSource，针对 IPTV 场景优化：
 * - 使用 [MulticastSocket] 显式加入组播组
 * - 将 SO_RCVBUF 设为 [socketBufferSize]（默认 2MB），防止高码率 TS 流因默认缓冲区太小而丢包
 * - 支持 udp://host:port 与 rtp://host:port 格式
 *
 * ExoPlayer 内置 [UdpDataSource] 的默认 socket 缓冲区仅 128-256KB，
 * 对 6-15Mbps 的 IPTV 组播流来说 ~100ms 就会溢出。
 */
@UnstableApi
class MulticastUdpDataSource(
    private val socketBufferSize: Int = DEFAULT_SOCKET_BUFFER_SIZE,
    private val socketTimeoutMs: Int = DEFAULT_SOCKET_TIMEOUT_MS,
) : BaseDataSource(/* isNetwork = */ true) {

    companion object {
        /** 2MB socket 接收缓冲区 */
        const val DEFAULT_SOCKET_BUFFER_SIZE = 2 * 1024 * 1024
        /** socket 读超时 8 秒 */
        const val DEFAULT_SOCKET_TIMEOUT_MS = 8_000
        private const val PACKET_SIZE = 65536
    }

    private var socket: MulticastSocket? = null
    private var multicastAddress: InetAddress? = null
    private var packet: DatagramPacket? = null
    private var packetRemaining = 0
    private var opened = false

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val host = uri.host ?: throw IOException("Missing host in URI: $uri")
        val port = uri.port.let { if (it > 0) it else throw IOException("Missing port in URI: $uri") }
        val address = InetAddress.getByName(host)

        val ms = MulticastSocket(null)
        try {
            ms.reuseAddress = true
            ms.bind(InetSocketAddress(port))
            ms.receiveBufferSize = socketBufferSize
            ms.soTimeout = socketTimeoutMs

            if (address.isMulticastAddress) {
                // 尝试用所有可用网络接口加入组播组
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    var joined = false
                    while (interfaces.hasMoreElements()) {
                        val ni = interfaces.nextElement()
                        if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                            try {
                                ms.joinGroup(InetSocketAddress(address, port), ni)
                                joined = true
                            } catch (_: Exception) {
                                // 某些网卡可能不支持，跳过
                            }
                        }
                    }
                    if (!joined) {
                        // 兜底：使用默认接口
                        @Suppress("DEPRECATION")
                        ms.joinGroup(address)
                    }
                } catch (_: Exception) {
                    @Suppress("DEPRECATION")
                    ms.joinGroup(address)
                }
                multicastAddress = address
            }
        } catch (e: Exception) {
            ms.close()
            throw IOException("Failed to open multicast socket: $host:$port", e)
        }

        socket = ms
        packet = DatagramPacket(ByteArray(PACKET_SIZE), PACKET_SIZE)
        packetRemaining = 0
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        // 当前 packet 中有剩余数据
        if (packetRemaining > 0) {
            val toRead = minOf(length, packetRemaining)
            val pkt = packet!!
            val pktOffset = pkt.length - packetRemaining
            System.arraycopy(pkt.data, pkt.offset + pktOffset, buffer, offset, toRead)
            packetRemaining -= toRead
            bytesTransferred(toRead)
            return toRead
        }

        // 接收下一个数据包
        val pkt = packet!!
        try {
            socket!!.receive(pkt)
        } catch (e: SocketTimeoutException) {
            // 超时视为读取失败，上层会触发重连/换线
            throw IOException("UDP receive timeout", e)
        }

        val received = pkt.length
        val toRead = minOf(length, received)
        System.arraycopy(pkt.data, pkt.offset, buffer, offset, toRead)
        packetRemaining = received - toRead
        bytesTransferred(toRead)
        return toRead
    }

    override fun getUri(): Uri? = if (opened) Uri.EMPTY else null

    @Throws(IOException::class)
    override fun close() {
        try {
            val addr = multicastAddress
            val ms = socket
            if (addr != null && ms != null) {
                try {
                    @Suppress("DEPRECATION")
                    ms.leaveGroup(addr)
                } catch (_: Exception) {
                    // best effort
                }
            }
            ms?.close()
        } finally {
            socket = null
            multicastAddress = null
            packet = null
            packetRemaining = 0
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
