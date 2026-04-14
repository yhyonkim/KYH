package com.example.pdbreader.parser

import com.example.pdbreader.model.PdbFormat
import com.example.pdbreader.model.PdbHeader
import com.example.pdbreader.model.PdbRecordEntry
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Palm PDB 파일 파서
 *
 * Palm Database 포맷:
 *  - 헤더: 78 바이트
 *  - 레코드 목록: numRecords * 8 바이트
 *  - 레코드 데이터
 */
class PdbParser(private val file: File) {

    private val bytes: ByteArray = file.readBytes()
    private val buffer: ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

    companion object {
        const val HEADER_SIZE = 78
        const val RECORD_ENTRY_SIZE = 8

        // Palm DOC 압축 버전
        const val DOC_VERSION_UNCOMPRESSED = 1
        const val DOC_VERSION_COMPRESSED = 2
    }

    /**
     * PDB 헤더를 파싱합니다.
     */
    fun parseHeader(): PdbHeader {
        if (bytes.size < HEADER_SIZE) {
            throw IOException("파일이 너무 작습니다 (${bytes.size} bytes). 유효한 PDB 파일이 아닙니다.")
        }

        buffer.position(0)

        // name: 32 bytes, null-terminated
        val nameBytes = ByteArray(32)
        buffer.get(nameBytes)
        val name = String(nameBytes, Charsets.ISO_8859_1).trimEnd('\u0000')

        val attributes = buffer.short
        val version = buffer.short

        // Dates: seconds since Jan 1, 1904 (Palm epoch)
        val creationDate = buffer.int.toLong() and 0xFFFFFFFFL
        val modificationDate = buffer.int.toLong() and 0xFFFFFFFFL
        buffer.int // lastBackupDate (skip)
        buffer.int // modificationNumber (skip)
        buffer.int // appInfoOffset (skip)
        buffer.int // sortInfoOffset (skip)

        // type + creator: 4 bytes each
        val typeBytes = ByteArray(4)
        buffer.get(typeBytes)
        val type = String(typeBytes, Charsets.ISO_8859_1)

        val creatorBytes = ByteArray(4)
        buffer.get(creatorBytes)
        val creator = String(creatorBytes, Charsets.ISO_8859_1)

        buffer.int // uniqueIDSeed (skip)
        buffer.int // nextRecordList (skip)

        val numRecords = buffer.short.toInt() and 0xFFFF

        return PdbHeader(
            name = name,
            attributes = attributes,
            version = version,
            creationDate = creationDate,
            modificationDate = modificationDate,
            type = type,
            creator = creator,
            numRecords = numRecords
        )
    }

    /**
     * 레코드 목록을 파싱합니다.
     */
    fun parseRecordList(numRecords: Int): List<PdbRecordEntry> {
        buffer.position(HEADER_SIZE)
        val records = mutableListOf<PdbRecordEntry>()

        for (i in 0 until numRecords) {
            val offset = buffer.int
            val attrAndId = buffer.int
            val attributes = ((attrAndId shr 24) and 0xFF).toByte()
            val uniqueId = attrAndId and 0x00FFFFFF
            records.add(PdbRecordEntry(offset, attributes, uniqueId))
        }

        return records
    }

    /**
     * 특정 레코드의 원시 데이터를 반환합니다.
     */
    fun getRecordData(recordEntry: PdbRecordEntry, nextOffset: Int): ByteArray {
        val start = recordEntry.offset
        val end = if (nextOffset > start) nextOffset else bytes.size
        val length = end - start
        if (start < 0 || start >= bytes.size || length <= 0) return ByteArray(0)
        return bytes.copyOfRange(start, minOf(start + length, bytes.size))
    }

    /**
     * 파일 크기를 반환합니다.
     */
    fun fileSize(): Long = file.length()

    /**
     * PDB 포맷 감지
     */
    fun detectFormat(header: PdbHeader): PdbFormat {
        return PdbFormat.fromTypeAndCreator(header.type, header.creator)
    }
}
