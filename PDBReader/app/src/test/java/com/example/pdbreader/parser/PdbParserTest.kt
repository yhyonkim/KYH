package com.example.pdbreader.parser

import com.example.pdbreader.model.PdbFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PDB 파일 파서 단위 테스트
 */
class PdbParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ───────────── 헬퍼 ─────────────

    /**
     * 최소한의 유효한 Palm PDB 파일을 생성합니다.
     */
    private fun createMinimalPdb(
        name: String = "TestBook",
        type: String = "TEXt",
        creator: String = "REAd",
        numRecords: Int = 1,
        recordData: ByteArray = ByteArray(16) { 0 }
    ): File {
        val file = tempFolder.newFile("test.pdb")
        val buf = ByteBuffer.allocate(1024).order(ByteOrder.BIG_ENDIAN)

        // 헤더 (78 bytes)
        val nameBytes = ByteArray(32)
        name.toByteArray(Charsets.ISO_8859_1).copyInto(nameBytes, 0, 0, minOf(name.length, 31))
        buf.put(nameBytes)            // name (32 bytes)
        buf.putShort(0)               // attributes
        buf.putShort(0)               // version
        buf.putInt(0)                 // creationDate
        buf.putInt(0)                 // modificationDate
        buf.putInt(0)                 // lastBackupDate
        buf.putInt(0)                 // modificationNumber
        buf.putInt(0)                 // appInfoOffset
        buf.putInt(0)                 // sortInfoOffset
        buf.put(type.toByteArray(Charsets.ISO_8859_1))      // type (4 bytes)
        buf.put(creator.toByteArray(Charsets.ISO_8859_1))   // creator (4 bytes)
        buf.putInt(0)                 // uniqueIDSeed
        buf.putInt(0)                 // nextRecordList
        buf.putShort(numRecords.toShort()) // numRecords

        // 레코드 목록 (8 bytes per record)
        val recordOffset = 78 + numRecords * 8
        buf.putInt(recordOffset)      // offset
        buf.putInt(0)                 // attr + uniqueId

        // 레코드 데이터
        buf.put(recordData)

        file.writeBytes(buf.array().copyOf(buf.position()))
        return file
    }

    // ───────────── 헤더 파싱 ─────────────

    @Test
    fun `Palm DOC 헤더를 올바르게 파싱한다`() {
        val file = createMinimalPdb(name = "MyBook", type = "TEXt", creator = "REAd")
        val parser = PdbParser(file)
        val header = parser.parseHeader()

        assertEquals("MyBook", header.name)
        assertEquals("TEXt", header.type)
        assertEquals("REAd", header.creator)
        assertEquals(1, header.numRecords)
    }

    @Test
    fun `iSilo 헤더를 올바르게 파싱한다`() {
        val file = createMinimalPdb(type = "iSLT", creator = "SiLo")
        val parser = PdbParser(file)
        val header = parser.parseHeader()

        assertEquals("iSLT", header.type)
        assertEquals("SiLo", header.creator)
    }

    @Test
    fun `32자 이름의 null termination을 올바르게 처리한다`() {
        val file = createMinimalPdb(name = "Short")
        val parser = PdbParser(file)
        val header = parser.parseHeader()

        assertEquals("Short", header.name)
        assertFalse(header.name.contains('\u0000'))
    }

    @Test
    fun `파일이 너무 작으면 IOException을 던진다`() {
        val file = tempFolder.newFile("tiny.pdb")
        file.writeBytes(ByteArray(10))

        val parser = PdbParser(file)
        assertThrows(java.io.IOException::class.java) {
            parser.parseHeader()
        }
    }

    // ───────────── 포맷 감지 ─────────────

    @Test
    fun `TEXt-REAd 포맷을 PALM_DOC으로 감지한다`() {
        val file = createMinimalPdb(type = "TEXt", creator = "REAd")
        val parser = PdbParser(file)
        val header = parser.parseHeader()
        val format = parser.detectFormat(header)

        assertEquals(PdbFormat.PALM_DOC, format)
    }

    @Test
    fun `iSLT-SiLo 포맷을 ISILO로 감지한다`() {
        val file = createMinimalPdb(type = "iSLT", creator = "SiLo")
        val parser = PdbParser(file)
        val header = parser.parseHeader()
        val format = parser.detectFormat(header)

        assertEquals(PdbFormat.ISILO, format)
    }

    @Test
    fun `알 수 없는 타입은 UNKNOWN으로 감지한다`() {
        val file = createMinimalPdb(type = "????" , creator = "????")
        val parser = PdbParser(file)
        val header = parser.parseHeader()
        val format = parser.detectFormat(header)

        assertEquals(PdbFormat.UNKNOWN, format)
    }

    // ───────────── 레코드 파싱 ─────────────

    @Test
    fun `레코드 목록을 올바르게 파싱한다`() {
        val file = createMinimalPdb(numRecords = 1)
        val parser = PdbParser(file)
        val header = parser.parseHeader()
        val records = parser.parseRecordList(header.numRecords)

        assertEquals(1, records.size)
        assertTrue(records[0].offset > 0)
    }

    @Test
    fun `레코드 데이터를 올바르게 읽는다`() {
        val testData = "Hello PDB".toByteArray()
        // DOC 헤더 형태로 패딩 (최소 16 bytes)
        val padded = ByteArray(16)
        testData.copyInto(padded)

        val file = createMinimalPdb(recordData = padded)
        val parser = PdbParser(file)
        val header = parser.parseHeader()
        val records = parser.parseRecordList(header.numRecords)

        val data = parser.getRecordData(records[0], Int.MAX_VALUE)
        assertTrue(data.isNotEmpty())
    }

    // ───────────── PalmDocReader 통합 ─────────────

    @Test
    fun `비압축 Palm DOC 파일을 올바르게 읽는다`() {
        val content = "This is a test book content."
        val contentBytes = content.toByteArray(Charsets.UTF_8)

        // DOC 헤더 레코드 (16 bytes): version=1(비압축), textLength, textRecords=1
        val docHeader = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(1)                          // version (비압축)
            putShort(0)                          // reserved
            putInt(contentBytes.size)            // textLength
            putShort(1)                          // textRecords
            putShort(4096)                       // recordSize
            putInt(0)                            // reserved
        }.array()

        // 두 번째 레코드 (실제 텍스트) 포함한 PDB 생성
        val file = tempFolder.newFile("book.pdb")
        val numRecords = 2
        val buf = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN)

        // 헤더
        val nameBytes = ByteArray(32)
        "TestBook".toByteArray(Charsets.ISO_8859_1).copyInto(nameBytes)
        buf.put(nameBytes)
        buf.putShort(0); buf.putShort(0)
        buf.putInt(0); buf.putInt(0); buf.putInt(0); buf.putInt(0)
        buf.putInt(0); buf.putInt(0)
        buf.put("TEXt".toByteArray(Charsets.ISO_8859_1))
        buf.put("REAd".toByteArray(Charsets.ISO_8859_1))
        buf.putInt(0); buf.putInt(0)
        buf.putShort(numRecords.toShort())

        val rec0Offset = 78 + numRecords * 8
        val rec1Offset = rec0Offset + 16

        // 레코드 목록
        buf.putInt(rec0Offset); buf.putInt(0)
        buf.putInt(rec1Offset); buf.putInt(0)

        // 레코드 0: DOC 헤더
        buf.put(docHeader)
        // 레코드 1: 텍스트
        buf.put(contentBytes)

        file.writeBytes(buf.array().copyOf(buf.position()))

        val reader = PalmDocReader(file)
        val result = reader.readAll()

        assertEquals("TestBook", result.title)
        assertEquals(PdbFormat.PALM_DOC, result.format)
        assertTrue(result.text.contains("This is a test book content."))
    }

    @Test
    fun `isValidPdb는 유효한 PDB를 true로 반환한다`() {
        val file = createMinimalPdb()
        assertTrue(PalmDocReader.isValidPdb(file))
    }

    @Test
    fun `isValidPdb는 빈 파일을 false로 반환한다`() {
        val file = tempFolder.newFile("empty.pdb")
        assertFalse(PalmDocReader.isValidPdb(file))
    }

    @Test
    fun `isValidPdb는 존재하지 않는 파일을 false로 반환한다`() {
        val file = File("/tmp/nonexistent_${System.currentTimeMillis()}.pdb")
        assertFalse(PalmDocReader.isValidPdb(file))
    }
}
