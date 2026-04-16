package com.example.pdbreader.parser

import com.example.pdbreader.model.PdbBook
import com.example.pdbreader.model.PdbFormat
import com.example.pdbreader.model.PdbHeader
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Palm DOC 및 iSilo PDB 파일 읽기 담당 클래스
 *
 * 지원 포맷:
 *  - Palm DOC (TEXt/REAd) - 비압축 및 압축
 *  - iSilo (iSLT/SiLo, ToGo/SiLo) - 텍스트만 추출
 *  - zTXT (zTXT/GPlm)
 */
class PalmDocReader(private val file: File) {

    data class ReadResult(
        val title: String,
        val text: String,
        val format: PdbFormat,
        val recordCount: Int,
        val textLength: Int,
        val isHtml: Boolean = false
    )

    /**
     * PDB 파일 전체를 읽어 텍스트를 추출합니다.
     */
    fun readAll(): ReadResult {
        val parser = PdbParser(file)
        val header = parser.parseHeader()
        val format = parser.detectFormat(header)
        val records = parser.parseRecordList(header.numRecords)

        if (records.isEmpty()) {
            throw IOException("레코드가 없습니다.")
        }

        return when (format) {
            PdbFormat.PALM_DOC -> readPalmDoc(parser, header, records)
            PdbFormat.ISILO, PdbFormat.ISILO3, PdbFormat.ISILO_OLD ->
                readIsiloAsText(parser, header, records)
            PdbFormat.ZTXT -> readZTxt(parser, header, records)
            else -> readRawText(parser, header, records)
        }
    }

    /**
     * Palm DOC 포맷 읽기
     * Record 0: 헤더 (DOC header)
     * Record 1~N: 텍스트 데이터 (압축 또는 비압축)
     */
    private fun readPalmDoc(
        parser: PdbParser,
        header: PdbHeader,
        records: List<com.example.pdbreader.model.PdbRecordEntry>
    ): ReadResult {
        // Record 0: DOC header
        val headerData = parser.getRecordData(
            records[0],
            if (records.size > 1) records[1].offset else Int.MAX_VALUE
        )

        if (headerData.size < 16) {
            throw IOException("DOC 헤더가 너무 짧습니다.")
        }

        val buf = ByteBuffer.wrap(headerData).order(ByteOrder.BIG_ENDIAN)
        val version = buf.short.toInt() and 0xFFFF
        buf.short // reserved
        val textLength = buf.int
        val textRecords = buf.short.toInt() and 0xFFFF
        // recordSize = buf.short (usually 4096)

        val compressed = version == PdbParser.DOC_VERSION_COMPRESSED
        val sb = StringBuilder(textLength + 1024)

        val count = minOf(textRecords, records.size - 1)
        for (i in 1..count) {
            val nextOffset = if (i + 1 < records.size) records[i + 1].offset else Int.MAX_VALUE
            val rawData = parser.getRecordData(records[i], nextOffset)
            if (rawData.isEmpty()) continue

            val decoded = DocDecoder.decodeRecord(rawData, compressed)
            sb.append(decodeText(decoded))
        }

        val finalText = cleanText(sb.toString())
        return ReadResult(
            title = header.name.ifEmpty { file.nameWithoutExtension },
            text = finalText,
            format = PdbFormat.PALM_DOC,
            recordCount = textRecords,
            textLength = textLength,
            isHtml = isHtmlContent(finalText)
        )
    }

    /**
     * iSilo 포맷 - 텍스트 레코드를 순차적으로 읽어 표시
     * iSilo는 독점 포맷이므로 가능한 범위에서 텍스트를 추출합니다.
     */
    private fun readIsiloAsText(
        parser: PdbParser,
        header: PdbHeader,
        records: List<com.example.pdbreader.model.PdbRecordEntry>
    ): ReadResult {
        val sb = StringBuilder()
        var textLength = 0

        for (i in 1 until records.size) {
            val nextOffset = if (i + 1 < records.size) records[i + 1].offset else Int.MAX_VALUE
            val data = parser.getRecordData(records[i], nextOffset)
            if (data.isEmpty()) continue

            // iSilo 레코드에서 출력 가능한 텍스트 추출
            val text = extractPrintableText(data)
            if (text.isNotBlank()) {
                sb.append(text)
                sb.append('\n')
                textLength += text.length
            }
        }

        val displayText = if (sb.isBlank()) {
            "[iSilo 포맷은 독점 압축을 사용합니다. 텍스트를 완전히 표시하려면 iSilo 앱이 필요합니다.]\n\n" +
                "파일명: ${file.name}\n제목: ${header.name}\n레코드 수: ${records.size}"
        } else {
            cleanText(sb.toString())
        }

        return ReadResult(
            title = header.name.ifEmpty { file.nameWithoutExtension },
            text = displayText,
            format = if (header.creator == "SiLo") PdbFormat.ISILO else PdbFormat.ISILO3,
            recordCount = records.size - 1,
            textLength = textLength
        )
    }

    /**
     * zTXT 포맷 - zlib 압축 텍스트
     */
    private fun readZTxt(
        parser: PdbParser,
        header: PdbHeader,
        records: List<com.example.pdbreader.model.PdbRecordEntry>
    ): ReadResult {
        val sb = StringBuilder()

        for (i in 1 until records.size) {
            val nextOffset = if (i + 1 < records.size) records[i + 1].offset else Int.MAX_VALUE
            val data = parser.getRecordData(records[i], nextOffset)
            if (data.isEmpty()) continue

            try {
                // zlib 압축 해제 시도
                val inflater = java.util.zip.Inflater()
                inflater.setInput(data)
                val output = ByteArray(data.size * 8)
                val len = inflater.inflate(output)
                inflater.end()
                sb.append(String(output, 0, len, Charsets.UTF_8))
            } catch (e: Exception) {
                // 압축 해제 실패 시 원시 텍스트 추출
                sb.append(extractPrintableText(data))
            }
        }

        return ReadResult(
            title = header.name.ifEmpty { file.nameWithoutExtension },
            text = cleanText(sb.toString()),
            format = PdbFormat.ZTXT,
            recordCount = records.size - 1,
            textLength = sb.length
        )
    }

    /**
     * 알 수 없는 포맷 - 원시 텍스트 추출
     */
    private fun readRawText(
        parser: PdbParser,
        header: PdbHeader,
        records: List<com.example.pdbreader.model.PdbRecordEntry>
    ): ReadResult {
        val sb = StringBuilder()
        sb.append("=== PDB 파일 정보 ===\n")
        sb.append("파일명: ${file.name}\n")
        sb.append("제목: ${header.name}\n")
        sb.append("타입: ${header.type}\n")
        sb.append("제작사: ${header.creator}\n")
        sb.append("레코드 수: ${header.numRecords}\n\n")
        sb.append("=== 텍스트 내용 (원시 추출) ===\n\n")

        for (i in 1 until records.size) {
            val nextOffset = if (i + 1 < records.size) records[i + 1].offset else Int.MAX_VALUE
            val data = parser.getRecordData(records[i], nextOffset)
            val text = extractPrintableText(data)
            if (text.isNotBlank()) {
                sb.append(text)
                sb.append('\n')
            }
        }

        return ReadResult(
            title = header.name.ifEmpty { file.nameWithoutExtension },
            text = cleanText(sb.toString()),
            format = PdbFormat.UNKNOWN,
            recordCount = records.size - 1,
            textLength = sb.length
        )
    }

    /**
     * 바이트 배열에서 출력 가능한 텍스트를 추출합니다.
     * EUC-KR, UTF-8, ISO-8859-1 순으로 인코딩을 시도합니다.
     */
    private fun extractPrintableText(data: ByteArray): String {
        // EUC-KR 시도 (한국어 PDB 대부분)
        if (data.any { it.toInt() and 0xFF > 0x7F }) {
            try {
                val text = String(data, charset("EUC-KR"))
                val filtered = text.filter { it != '\uFFFD' && (it.isLetterOrDigit() || it.isWhitespace() || it.code > 0x7F || it in ".,!?\"'()[]{}:;-_/@#$%^&*") }
                if (filtered.length > data.size / 4) return filtered
            } catch (_: Exception) {}
        }
        // UTF-8 시도
        try {
            val text = String(data, Charsets.UTF_8)
            val filtered = text.filter { it != '\uFFFD' && (it.isLetterOrDigit() || it.isWhitespace() || it.code > 0x7F || it in ".,!?\"'()[]{}:;-_/@#$%^&*") }
            if (filtered.length > data.size / 8) return filtered
        } catch (_: Exception) {}
        // ASCII fallback
        return String(data, Charsets.ISO_8859_1)
            .filter { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }
    }

    /**
     * 바이트 배열을 올바른 인코딩으로 디코딩합니다. (EUC-KR / UTF-8 자동 감지)
     */
    private fun decodeText(data: ByteArray): String {
        if (data.any { it.toInt() and 0xFF > 0x7F }) {
            // EUC-KR 시도
            try {
                val text = String(data, charset("EUC-KR"))
                if (text.none { it == '\uFFFD' }) return text
            } catch (_: Exception) {}
        }
        return try {
            String(data, Charsets.UTF_8)
        } catch (_: Exception) {
            String(data, Charsets.ISO_8859_1)
        }
    }

    /**
     * 텍스트가 HTML 컨텐츠인지 감지합니다.
     */
    private fun isHtmlContent(text: String): Boolean {
        val sample = text.take(2000).lowercase()
        return sample.contains("<html") ||
            sample.contains("<body") ||
            (sample.contains("<p>") || sample.contains("<p ")) ||
            sample.contains("<br") ||
            sample.contains("<div") ||
            sample.contains("<h1") || sample.contains("<h2") || sample.contains("<h3")
    }

    /**
     * 텍스트를 정리합니다 (불필요한 공백 등 제거)
     */
    private fun cleanText(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("\n{4,}"), "\n\n\n")  // 과도한 빈 줄 제거
            .trim()
    }

    companion object {
        /**
         * 파일이 유효한 PDB인지 빠르게 검사합니다.
         */
        fun isValidPdb(file: File): Boolean {
            if (!file.exists() || file.length() < PdbParser.HEADER_SIZE) return false
            return try {
                val parser = PdbParser(file)
                val header = parser.parseHeader()
                header.numRecords > 0
            } catch (e: Exception) {
                false
            }
        }

        /**
         * PDB 파일 메타데이터만 빠르게 읽습니다 (전체 내용 로드 없이).
         */
        fun readMetadata(file: File): PdbBook? {
            return try {
                val parser = PdbParser(file)
                val header = parser.parseHeader()
                val records = parser.parseRecordList(header.numRecords)
                val format = parser.detectFormat(header)

                // Record 0에서 텍스트 길이 읽기 (Palm DOC의 경우)
                val textLength = if (format == PdbFormat.PALM_DOC && records.isNotEmpty()) {
                    val headerData = parser.getRecordData(
                        records[0],
                        if (records.size > 1) records[1].offset else Int.MAX_VALUE
                    )
                    if (headerData.size >= 8) {
                        val buf = java.nio.ByteBuffer.wrap(headerData).order(java.nio.ByteOrder.BIG_ENDIAN)
                        buf.short // version
                        buf.short // reserved
                        buf.int   // textLength
                    } else 0
                } else 0

                PdbBook(
                    title = header.name.ifEmpty { file.nameWithoutExtension },
                    filePath = file.absolutePath,
                    format = format,
                    fileSize = file.length(),
                    recordCount = header.numRecords,
                    textLength = textLength
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
