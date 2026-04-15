package com.example.pdbreader.parser

import org.junit.Assert.*
import org.junit.Test

/**
 * Palm DOC 압축 해제 알고리즘 단위 테스트
 *
 * Palm DOC 압축 알고리즘:
 *   0x00        : 다음 1바이트를 그대로 출력
 *   0x01-0x08   : 다음 N바이트를 그대로 출력
 *   0x09-0x7F   : ASCII 문자 그대로
 *   0x80-0xBF   : 뒤로 참조 압축
 *   0xC0-0xFF   : 공백 + ASCII 문자
 */
class DocDecoderTest {

    // ───────────── 비압축 모드 ─────────────

    @Test
    fun `비압축 모드에서 데이터를 그대로 반환한다`() {
        val input = "Hello, World!".toByteArray()
        val result = DocDecoder.decodeRecord(input, compressed = false)
        assertArrayEquals(input, result)
    }

    @Test
    fun `빈 데이터를 비압축 모드로 처리하면 빈 배열을 반환한다`() {
        val result = DocDecoder.decodeRecord(ByteArray(0), compressed = false)
        assertEquals(0, result.size)
    }

    // ───────────── 압축 모드 - 리터럴 ─────────────

    @Test
    fun `0x09-0x7F 범위의 ASCII 바이트를 그대로 출력한다`() {
        // 'H'=0x48, 'i'=0x69 모두 0x09-0x7F 범위
        val input = byteArrayOf(0x48, 0x69) // "Hi"
        val result = DocDecoder.decodeRecord(input, compressed = true)
        assertEquals("Hi", String(result, Charsets.ISO_8859_1))
    }

    @Test
    fun `0x00 뒤의 1바이트를 그대로 출력한다`() {
        // 0x00 + 0x01 → 0x01 출력
        val input = byteArrayOf(0x00, 0x01)
        val result = DocDecoder.decodeRecord(input, compressed = true)
        assertArrayEquals(byteArrayOf(0x01), result)
    }

    @Test
    fun `0x01-0x08 범위에서 N바이트를 그대로 복사한다`() {
        // 0x03 + "abc" → "abc"
        val input = byteArrayOf(0x03, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        val result = DocDecoder.decodeRecord(input, compressed = true)
        assertEquals("abc", String(result, Charsets.ISO_8859_1))
    }

    // ───────────── 압축 모드 - 공백 압축 ─────────────

    @Test
    fun `0xC0-0xFF는 공백과 ASCII 문자를 출력한다`() {
        // 0xC0 + 0x40 = 0x80 → space + '@'
        // 0xC1 → space + 'A' (0xC1 - 0xC0 + 0x40 = 0x41 = 'A')
        val input = byteArrayOf(0xC1.toByte()) // → " A"
        val result = DocDecoder.decodeRecord(input, compressed = true)
        assertEquals(2, result.size)
        assertEquals(' '.code.toByte(), result[0])
        assertEquals('A'.code.toByte(), result[1])
    }

    @Test
    fun `0xFF는 공백과 0x7F를 출력한다`() {
        // 0xFF - 0xC0 + 0x40 = 0x7F
        val input = byteArrayOf(0xFF.toByte())
        val result = DocDecoder.decodeRecord(input, compressed = true)
        assertEquals(2, result.size)
        assertEquals(' '.code.toByte(), result[0])
        assertEquals(0x7F.toByte(), result[1])
    }

    // ───────────── 압축 모드 - 뒤로 참조 ─────────────

    @Test
    fun `0x80-0xBF는 뒤로 참조하여 반복 데이터를 출력한다`() {
        // "aaaa" 를 압축 표현: 'a' 출력 후 뒤로 3칸에서 4글자 복사
        // 먼저 'a'(0x61) 리터럴 → output = [a]
        // 그 다음 back-ref: distance=1, length=3 → "aaa"
        // distance=1 → (b1 & 0x3F) << 3 | (b2 >> 5) = 1
        // length=3   → (b2 & 0x1F) + 3 = 3 → b2 & 0x1F = 0
        // b1 = 0x80 | (1 >> 3) << 0 = ? let distance=1: (b1&0x3F)<<3 | b2>>5 = 1
        // 가장 단순한 경우: distance=1, length=3 → b1=0x80, b2=0x00 | (0<<5) = 0x00
        // but (0x80&0x3F)<<3 = 0, 0|b2>>5=b2>>5=0 → distance=0 → invalid
        // distance=1: need (b1&0x3F)<<3 | b2>>5 = 1 → b2>>5=1 → b2=0x20
        // length=3: (b2&0x1F)+3=3 → b2&0x1F=0 → b2=0x20
        // b1=0x80 (b1&0x3F=0, 0<<3=0, 0|1=1 via b2>>5=1)
        val input = byteArrayOf(
            0x61,        // 'a'
            0x80.toByte(), 0x20  // back-ref: distance=1, length=3
        )
        val result = DocDecoder.decodeRecord(input, compressed = true)
        assertEquals("aaaa", String(result, Charsets.ISO_8859_1))
    }

    // ───────────── 통합 테스트 ─────────────

    @Test
    fun `혼합 압축 데이터를 올바르게 디코딩한다`() {
        // "Hello World" 를 수동 인코딩:
        // 'H'(0x48) 'e'(0x65) 'l'(0x6C) 'l'(0x6C) 'o'(0x6F) → 0x09-0x7F 범위이므로 그대로
        // ' W' → 0xD7 (0xD7-0xC0+0x40=0x57='W', 앞에 space)
        // 'o'(0x6F) 'r'(0x72) 'l'(0x6C) 'd'(0x64) → 그대로
        val input = byteArrayOf(
            'H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(),
            'l'.code.toByte(), 'o'.code.toByte(),
            0xD7.toByte(),   // ' ' + 'W'
            'o'.code.toByte(), 'r'.code.toByte(), 'l'.code.toByte(), 'd'.code.toByte()
        )
        val result = DocDecoder.decodeRecord(input, compressed = true)
        assertEquals("Hello World", String(result, Charsets.ISO_8859_1))
    }

    @Test
    fun `빈 입력을 압축 모드로 처리하면 빈 배열을 반환한다`() {
        val result = DocDecoder.decodeRecord(ByteArray(0), compressed = true)
        assertEquals(0, result.size)
    }

    @Test
    fun `긴 반복 문자열을 올바르게 압축 해제한다`() {
        // "aaaaaaaaaaaa" (12개) 표현
        // 'a' 리터럴 + back-ref distance=1, length=11
        // b2>>5 = 1 (distance 하위 3비트), b2&0x1F = 8 (length-3=8)
        // b2 = (1 << 5) | 8 = 0x28
        // b1 = 0x80 (distance 상위 6비트=0)
        val input = byteArrayOf(
            0x61,              // 'a'
            0x80.toByte(), 0x28  // distance=1, length=11
        )
        val result = DocDecoder.decodeRecord(input, compressed = true)
        assertEquals("a".repeat(12), String(result, Charsets.ISO_8859_1))
    }
}
