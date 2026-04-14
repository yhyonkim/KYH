package com.example.pdbreader.parser

import java.io.ByteArrayOutputStream

/**
 * Palm DOC 형식 디코더
 *
 * Palm DOC 압축 알고리즘 (version 2):
 *   0x00        : 다음 1바이트를 그대로 출력
 *   0x01-0x08   : 다음 N바이트를 그대로 출력
 *   0x09-0x7F   : 해당 바이트를 ASCII 문자로 출력
 *   0x80-0xBF   : 2바이트 시퀀스 (뒤로 참조 압축)
 *                 distance = ((b1 & 0x3F) << 3) | (b2 >> 5)
 *                 length   = (b2 & 0x1F) + 3
 *   0xC0-0xFF   : 공백 + ASCII 문자 (byte - 0xC0 + 0x40)
 */
object DocDecoder {

    /**
     * Palm DOC 레코드를 디코드합니다.
     * @param data 압축된 레코드 데이터
     * @param compressed true이면 압축 해제, false이면 그대로 반환
     */
    fun decodeRecord(data: ByteArray, compressed: Boolean): ByteArray {
        if (!compressed) return data
        return decompress(data)
    }

    private fun decompress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size * 2)
        var i = 0

        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            i++

            when {
                b == 0x00 -> {
                    // 다음 1바이트를 그대로 출력
                    if (i < data.size) {
                        out.write(data[i].toInt() and 0xFF)
                        i++
                    }
                }
                b in 0x01..0x08 -> {
                    // 다음 b바이트를 그대로 출력
                    val count = minOf(b, data.size - i)
                    out.write(data, i, count)
                    i += count
                }
                b in 0x09..0x7F -> {
                    // ASCII 문자 그대로
                    out.write(b)
                }
                b in 0x80..0xBF -> {
                    // 뒤로 참조 (back reference)
                    if (i < data.size) {
                        val b2 = data[i].toInt() and 0xFF
                        i++
                        val distance = ((b and 0x3F) shl 3) or (b2 ushr 5)
                        val length = (b2 and 0x1F) + 3

                        val outBuf = out.toByteArray()
                        val start = outBuf.size - distance

                        if (start >= 0) {
                            repeat(length) { k ->
                                val idx = start + (k % distance)
                                if (idx < outBuf.size) {
                                    out.write(outBuf[idx].toInt() and 0xFF)
                                } else {
                                    // Copy from what we've just written
                                    val freshBuf = out.toByteArray()
                                    val freshIdx = start + (k % distance)
                                    if (freshIdx < freshBuf.size) {
                                        out.write(freshBuf[freshIdx].toInt() and 0xFF)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // 0xC0-0xFF: 공백 + (b - 0xC0 + 0x40) 문자
                    out.write(0x20) // 공백
                    out.write(b - 0xC0 + 0x40)
                }
            }
        }

        return out.toByteArray()
    }
}
