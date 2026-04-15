package com.example.pdbreader.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.pdbreader.model.PdbBook
import com.example.pdbreader.parser.PalmDocReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 책 목록 정렬 기준
 */
enum class SortOrder { BY_TITLE, BY_DATE_ADDED, BY_PROGRESS }

/**
 * 서재(Library) ViewModel
 * 책 목록을 관리하고 SharedPreferences에 저장합니다.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences(
        "library_prefs", android.content.Context.MODE_PRIVATE
    )

    private val _books = MutableLiveData<List<PdbBook>>(emptyList())
    val books: LiveData<List<PdbBook>> = _books

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var sortOrder = SortOrder.BY_DATE_ADDED

    init {
        loadBooks()
    }

    /**
     * 정렬 기준을 변경합니다.
     */
    fun setSortOrder(order: SortOrder) {
        sortOrder = order
        _books.value = sortBooks(_books.value ?: emptyList())
    }

    fun currentSortOrder(): SortOrder = sortOrder

    private fun sortBooks(list: List<PdbBook>): List<PdbBook> = when (sortOrder) {
        SortOrder.BY_TITLE      -> list.sortedBy { it.title.lowercase() }
        SortOrder.BY_DATE_ADDED -> list.sortedByDescending { it.addedDate }
        SortOrder.BY_PROGRESS   -> list.sortedByDescending { it.readingProgress }
    }

    private fun loadBooks() {
        val saved = prefs.getString(KEY_BOOKS, null) ?: return
        try {
            val arr = JSONArray(saved)
            val list = mutableListOf<PdbBook>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val book = bookFromJson(obj)
                // 파일이 존재하는 경우만 로드
                if (File(book.filePath).exists()) {
                    list.add(book)
                }
            }
            _books.value = sortBooks(list)
        } catch (e: Exception) {
            // 저장된 데이터 손상 시 초기화
            prefs.edit().remove(KEY_BOOKS).apply()
        }
    }

    /**
     * PDB 파일을 서재에 추가합니다.
     */
    fun addBook(file: File) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val book = withContext(Dispatchers.IO) {
                    if (!PalmDocReader.isValidPdb(file)) {
                        throw Exception("유효하지 않은 PDB 파일입니다.")
                    }
                    PalmDocReader.readMetadata(file)
                        ?: throw Exception("파일 메타데이터를 읽을 수 없습니다.")
                }

                // 중복 체크
                val current = _books.value ?: emptyList()
                if (current.any { it.filePath == file.absolutePath }) {
                    _error.value = "이미 서재에 있는 파일입니다."
                    return@launch
                }

                val updated = sortBooks(current + book)
                _books.value = updated
                saveBooks(current + book)  // 저장은 정렬 전 순서로 유지
            } catch (e: Exception) {
                _error.value = e.message ?: "파일을 추가할 수 없습니다."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 책을 서재에서 삭제합니다.
     */
    fun removeBook(book: PdbBook) {
        val updated = (_books.value ?: emptyList()).filter { it.id != book.id }
        _books.value = updated
        saveBooks(updated)
    }

    /**
     * 읽기 진행률을 업데이트합니다.
     */
    fun updateProgress(bookId: Long, progress: Float, position: Int) {
        val updated = (_books.value ?: emptyList()).map { book ->
            if (book.id == bookId) {
                book.copy(readingProgress = progress, lastReadPosition = position)
            } else book
        }
        _books.value = updated
        saveBooks(updated)
    }

    fun clearError() {
        _error.value = null
    }

    private fun saveBooks(books: List<PdbBook>) {
        val arr = JSONArray()
        books.forEach { arr.put(bookToJson(it)) }
        prefs.edit().putString(KEY_BOOKS, arr.toString()).apply()
    }

    private fun bookToJson(book: PdbBook): JSONObject {
        return JSONObject().apply {
            put("id", book.id)
            put("title", book.title)
            put("filePath", book.filePath)
            put("format", book.format.name)
            put("fileSize", book.fileSize)
            put("recordCount", book.recordCount)
            put("textLength", book.textLength)
            put("readingProgress", book.readingProgress)
            put("lastReadPosition", book.lastReadPosition)
            put("addedDate", book.addedDate)
        }
    }

    private fun bookFromJson(obj: JSONObject): PdbBook {
        val formatName = obj.optString("format", "UNKNOWN")
        val format = try {
            com.example.pdbreader.model.PdbFormat.valueOf(formatName)
        } catch (e: Exception) {
            com.example.pdbreader.model.PdbFormat.UNKNOWN
        }

        return PdbBook(
            id = obj.getLong("id"),
            title = obj.getString("title"),
            filePath = obj.getString("filePath"),
            format = format,
            fileSize = obj.getLong("fileSize"),
            recordCount = obj.getInt("recordCount"),
            textLength = obj.getInt("textLength"),
            readingProgress = obj.getDouble("readingProgress").toFloat(),
            lastReadPosition = obj.getInt("lastReadPosition"),
            addedDate = obj.getLong("addedDate")
        )
    }

    companion object {
        private const val KEY_BOOKS = "books"
    }
}
