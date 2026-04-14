package com.example.pdbreader.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Palm PDB 파일 포맷 타입
 */
enum class PdbFormat(val typeName: String, val creatorName: String, val displayName: String) {
    PALM_DOC("TEXt", "REAd", "Palm DOC"),
    ISILO("iSLT", "SiLo", "iSilo"),
    ISILO3("ToGo", "SiLo", "iSilo 3"),
    ZTXT("zTXT", "GPlm", "zTXT"),
    UNKNOWN("????", "????", "Unknown PDB");

    companion object {
        fun fromTypeAndCreator(type: String, creator: String): PdbFormat {
            return values().firstOrNull {
                it.typeName == type && it.creatorName == creator
            } ?: values().firstOrNull {
                it.creatorName == creator
            } ?: UNKNOWN
        }
    }
}

/**
 * 서재에 저장되는 책 정보
 */
data class PdbBook(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val filePath: String,
    val format: PdbFormat,
    val fileSize: Long,
    val recordCount: Int,
    val textLength: Int,
    var readingProgress: Float = 0f,   // 0.0 ~ 1.0
    var lastReadPosition: Int = 0,
    val addedDate: Long = System.currentTimeMillis()
) {
    val progressPercent: Int get() = (readingProgress * 100).toInt()

    val fileSizeDisplay: String get() {
        return when {
            fileSize >= 1024 * 1024 -> "%.1f MB".format(fileSize / (1024.0 * 1024.0))
            fileSize >= 1024 -> "%.1f KB".format(fileSize / 1024.0)
            else -> "$fileSize B"
        }
    }
}

/**
 * PDB 파일 헤더 정보
 */
data class PdbHeader(
    val name: String,
    val attributes: Short,
    val version: Short,
    val creationDate: Long,
    val modificationDate: Long,
    val type: String,
    val creator: String,
    val numRecords: Int
)

/**
 * PDB 레코드 항목
 */
data class PdbRecordEntry(
    val offset: Int,
    val attributes: Byte,
    val uniqueId: Int
)
