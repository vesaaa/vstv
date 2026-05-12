package com.vesaa.mytv.utils

import java.nio.ByteBuffer
import kotlin.ExperimentalStdlibApi

@file:OptIn(ExperimentalStdlibApi::class)

/**
 * 从 H.264 Annex B 字节流中提取 CEA-608/708 字幕数据。
 *
 * 解析链路：
 *   TS → PES → H.264 NAL → SEI (type 6)
 *   → user_data_registered_itu_t_t35 (payloadType 4)
 *   → ATSC1_data (GA94: 0x47413934)
 *   → cc_data() → CEA-608 byte pairs
 */
object SeiCaptionParser {

    /** Annex B 起始码: 0x00000001 */
    private val START_CODE_4 = byteArrayOf(0, 0, 0, 1)
    /** Annex B 起始码: 0x000001 */
    private val START_CODE_3 = byteArrayOf(0, 0, 1)

    /** SEI payload type 4 = user_data_registered_itu_t_t35 */
    private const val SEI_PAYLOAD_TYPE_USER_DATA = 4

    /** ATSC1 user_data_type_code "GA94" */
    private val GA94_MAGIC = byteArrayOf(0x47.toByte(), 0x41.toByte(), 0x39.toByte(), 0x34.toByte())

    /**
     * 单对 CEA-608 字节:
     *   [0] = field (0或1) + cc_data_1 (低7位=有效数据)
     *   [1] = cc_data_2 (低7位=有效数据)
     * 共 2 字节有效 caption 数据。
     */
    data class Cea608Pair(
        val field: Int,
        val ccData1: Int,  // 7-bit
        val ccData2: Int   // 7-bit
    )

    /**
     * 从 H.264 Annex B 格式的 sample data 中提取所有 CEA-608 字幕数据对。
     *
     * @param sampleData 视频 sample 的原始 Annex B 字节数据
     * @return 提取到的 CEA-608 数据对列表，可能为空
     */
    fun extractCcData(sampleData: ByteBuffer): List<Cea608Pair> {
        val pairs = mutableListOf<Cea608Pair>()
        val data = ByteArray(sampleData.remaining())
        sampleData.duplicate().get(data)
        extractFromBytes(data, pairs)
        return pairs
    }

    /**
     * 从原始字节数组中提取 CEA-608 数据
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun extractFromBytes(data: ByteArray, output: MutableList<Cea608Pair>) {
        var pos = 0
        while (pos < data.size - 3) {
            val startCodeLen = findStartCode(data, pos) ?: run { pos++; continue }
            pos += startCodeLen

            if (pos >= data.size) break

            val nalHeader = data[pos].toInt() and 0xFF
            pos++

            val nalUnitType = nalHeader and 0x1F
            val nalRefIdc = (nalHeader shr 5) and 0x03

            if (nalUnitType != 6) {
                // 不是 SEI，跳过此 NAL
                continue
            }

            // SEI NAL: 解析 payload
            // 找到下一个起始码作为结束位置
            val nextStart = findNextStartCode(data, pos) ?: data.size
            val seiData = data.copyOfRange(pos, nextStart)

            parseSeiPayload(seiData, output)
            pos = nextStart
        }
    }

    // ── 起始码检测 ────────────────────────────────────────────

    private fun findStartCode(data: ByteArray, pos: Int): Int? {
        if (pos + 3 <= data.size &&
            data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
            if (data[pos + 2] == 1.toByte()) return 3
            if (pos + 4 <= data.size && data[pos + 2] == 0.toByte() && data[pos + 3] == 1.toByte())
                return 4
        }
        return null
    }

    private fun findNextStartCode(data: ByteArray, fromPos: Int): Int? {
        var pos = fromPos
        while (pos < data.size - 3) {
            val len = findStartCode(data, pos)
            if (len != null) return pos
            pos++
        }
        return null
    }

    // ── SEI payload 解析 ──────────────────────────────────────

    private fun parseSeiPayload(seiData: ByteArray, output: MutableList<Cea608Pair>) {
        var pos = 0
        while (pos < seiData.size) {
            if (pos >= seiData.size) break
            // 读取 payloadType (ue(v) 编码)
            val typeResult = readUeGolomb(seiData, pos) ?: break
            val payloadType = typeResult.first
            pos = typeResult.second

            // 读取 payloadSize (ue(v) 编码)
            val sizeResult = readUeGolomb(seiData, pos) ?: break
            val payloadSize = sizeResult.first
            pos = sizeResult.second

            if (pos + payloadSize > seiData.size) break

            if (payloadType == SEI_PAYLOAD_TYPE_USER_DATA) {
                parseUserDataT35(seiData, pos, payloadSize, output)
            }

            pos += payloadSize
            // SEI RBSP 尾部可能有 rbsp_trailing_bits (0x80)
            while (pos < seiData.size && seiData[pos] == 0.toByte()) pos++
        }
    }

    // ── user_data_registered_itu_t_t35 解析 ───────────────────

    private fun parseUserDataT35(
        data: ByteArray,
        offset: Int,
        length: Int,
        output: MutableList<Cea608Pair>
    ) {
        var pos = offset
        // user_data_registered_itu_t_t35():
        //   itu_t_t35_country_code: 8 bits (0xB5 = USA)
        //   itu_t_t35_provider_code: 16 bits (usually 0x0031 for ATSC)
        if (pos + 2 >= offset + length) return
        val countryCode = data[pos].toInt() and 0xFF
        pos++

        if (countryCode != 0xB5 && countryCode != 0x42) return // 0xB5=USA, 0x42=South Korea (also used)

        val providerCode = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2

        // provider_code 0x0031 = ATSC, but some encoders use different values
        // We'll try to match GA94 regardless of provider code
        // ATSC1_data(): user_data_type_code (4 bytes) + user_data_type_structure()
        if (pos + 4 > offset + length) return

        // Check for GA94 magic
        if (data[pos] == GA94_MAGIC[0] && data[pos + 1] == GA94_MAGIC[1] &&
            data[pos + 2] == GA94_MAGIC[2] && data[pos + 3] == GA94_MAGIC[3]
        ) {
            pos += 4
            parseAtsc1Data(data, pos, offset + length, output)
        }
    }

    // ── ATSC1_data 解析 ───────────────────────────────────────

    private fun parseAtsc1Data(
        data: ByteArray,
        offset: Int,
        endOffset: Int,
        output: MutableList<Cea608Pair>
    ) {
        var pos = offset
        if (pos >= endOffset) return

        // process_em_data_flag: 1 bit
        val emData = (data[pos].toInt() shr 7) and 0x01
        // process_cc_data_flag: 1 bit
        val ccDataFlag = (data[pos].toInt() shr 6) and 0x01
        // additional_data: 1 bit
        // cc_count: 5 bits
        val ccCount = data[pos].toInt() and 0x1F
        pos++

        if (ccDataFlag == 0) return

        // cc_data() : 循环 cc_count 次，每个 3 字节
        for (i in 0 until ccCount) {
            if (pos + 3 > endOffset) break

            val markerBit = (data[pos].toInt() shr 5) and 0x01
            // cc_valid: 1 bit (marker bit 应该是 1)
            if (markerBit != 1) {
                pos += 3
                continue
            }

            val ccType = (data[pos].toInt() shr 2) and 0x07
            val ccData1Raw = data[pos].toInt() and 0x03  // top 2 bits of cc_data_1
            pos++

            val ccData1 = ((ccData1Raw shl 6) or ((data[pos].toInt() shr 2) and 0x3F))
            val ccData2TopBits = data[pos].toInt() and 0x03  // top 2 bits of cc_data_2
            pos++

            val ccData2 = ((ccData2TopBits shl 6) or ((data[pos].toInt() shr 2) and 0x3F))

            // cc_type: 0=field1, 1=field2
            // CEA-608 data is in cc_data_1 and cc_data_2 (each 7 bits valid, but we store full 8-bit for Cea608Decoder)
            val field = ccType  // 0 or 1

            // Check for valid data (non-zero)
            if (ccData1 != 0 || ccData2 != 0) {
                output.add(Cea608Pair(field, ccData1, ccData2))
            }

            pos++
        }
    }

    // ── Exp-Golomb 解码 ───────────────────────────────────────

    /**
     * 读取无符号指数哥伦布编码 (ue(v))。
     * @return Pair<value, newPosition> 或 null
     */
    private fun readUeGolomb(data: ByteArray, pos: Int): Pair<Int, Int>? {
        var leadingZeroBits = 0
        var readPos = pos
        while (readPos < data.size * 8 && readBit(data, readPos) == 0) {
            leadingZeroBits++
            readPos++
            if (readPos - pos > 32) return null // 保护：防止无限循环
        }
        if (readPos >= data.size * 8) return null

        // 跳过第一个 1
        readPos++

        var value = 0
        for (i in 0 until leadingZeroBits) {
            if (readPos >= data.size * 8) return null
            value = (value shl 1) or readBit(data, readPos)
            readPos++
        }
        value += (1 shl leadingZeroBits) - 1

        // 转换 bit 位置为字节位置
        val bytePos = (readPos + 7) / 8
        return Pair(value, bytePos.coerceAtMost(data.size))
    }

    private fun readBit(data: ByteArray, bitPos: Int): Int {
        val byteIndex = bitPos / 8
        if (byteIndex >= data.size) return 0
        val bitIndex = 7 - (bitPos % 8)
        return (data[byteIndex].toInt() shr bitIndex) and 0x01
    }
}