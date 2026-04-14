package com.example.pdbreader.model

/**
 * 북마크 모델
 * @param bookId 해당 책의 ID
 * @param scrollPosition 스크롤 Y 위치 (픽셀)
 * @param textPreview 해당 위치의 텍스트 미리보기 (최대 60자)
 */
data class Bookmark(
    val id: Long = System.currentTimeMillis(),
    val bookId: Long,
    val scrollPosition: Int,
    val textPreview: String,
    val createdAt: Long = System.currentTimeMillis()
)
