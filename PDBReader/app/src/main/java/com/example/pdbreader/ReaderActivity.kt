package com.example.pdbreader

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pdbreader.databinding.ActivityReaderBinding
import com.example.pdbreader.model.Bookmark
import com.example.pdbreader.parser.PalmDocReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var prefs: SharedPreferences

    private var bookId: Long = -1
    private var filePath: String = ""
    private var bookTitle: String = ""
    private var fullText: String = ""
    private var isHtml: Boolean = false

    // 읽기 설정
    private var fontSize = 16f
    private var bgTheme = BG_WHITE
    private var lineSpacing = 1.6f

    // 검색
    private var searchQuery = ""
    private var searchResults = listOf<Int>()
    private var currentSearchIndex = -1

    // 북마크
    private val bookmarks = mutableListOf<Bookmark>()

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_TITLE = "extra_title"

        const val BG_WHITE = 0
        const val BG_SEPIA = 1
        const val BG_DARK = 2

        private const val PREF_FONT_SIZE = "font_size"
        private const val PREF_BG_THEME = "bg_theme"
        private const val PREF_LINE_SPACING = "line_spacing"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

        bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1)
        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        bookTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = bookTitle
        }

        loadSettings()
        loadBookmarks()
        applyTheme()
        setupSearchBar()
        setupProgressBar()

        if (filePath.isNotEmpty()) {
            loadBook()
        } else {
            Toast.makeText(this, R.string.error_reading_file, Toast.LENGTH_LONG).show()
            finish()
        }

        binding.scrollView.setOnClickListener { toggleUI() }
    }

    private fun loadBook() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvContent.text = ""

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    PalmDocReader(File(filePath)).readAll()
                }

                fullText = result.text
                isHtml = result.isHtml
                displayText(fullText)
                updateProgressBar()

                val savedPos = prefs.getInt("pos_$bookId", 0)
                if (savedPos > 0) {
                    binding.scrollView.post {
                        binding.scrollView.scrollTo(0, savedPos)
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@ReaderActivity,
                    "${getString(R.string.error_reading_file)}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } finally {
                binding.progressIndicator.visibility = View.GONE
            }
        }
    }

    private fun displayText(text: String) {
        binding.tvContent.apply {
            textSize = fontSize
            setLineSpacing(0f, lineSpacing)
            this.text = if (isHtml) {
                // HTML 렌더링
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(text)
                }
            } else {
                text
            }
        }
    }

    private fun applyTheme() {
        val (bgColor, textColor) = when (bgTheme) {
            BG_SEPIA -> Pair(
                resources.getColor(R.color.bg_sepia, theme),
                resources.getColor(R.color.text_sepia, theme)
            )
            BG_DARK -> Pair(
                resources.getColor(R.color.bg_dark, theme),
                resources.getColor(R.color.text_dark, theme)
            )
            else -> Pair(
                resources.getColor(R.color.bg_white, theme),
                resources.getColor(R.color.text_white, theme)
            )
        }
        binding.readerContainer.setBackgroundColor(bgColor)
        binding.scrollView.setBackgroundColor(bgColor)
        binding.tvContent.setBackgroundColor(bgColor)
        binding.tvContent.setTextColor(textColor)

        if (bgTheme == BG_DARK) {
            binding.toolbar.setBackgroundColor(resources.getColor(R.color.toolbar_dark, theme))
            binding.bottomBar.setBackgroundColor(resources.getColor(R.color.toolbar_dark, theme))
        }
    }

    private fun setupSearchBar() {
        binding.etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER)) {
                performSearch(binding.etSearch.text.toString())
                true
            } else false
        }
        binding.btnSearchNext.setOnClickListener { navigateSearch(forward = true) }
        binding.btnSearchPrev.setOnClickListener { navigateSearch(forward = false) }
        binding.btnSearchClose.setOnClickListener { hideSearchBar() }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        searchQuery = query
        searchResults = mutableListOf<Int>().also { results ->
            var index = 0
            while (true) {
                index = fullText.indexOf(query, index, ignoreCase = true)
                if (index == -1) break
                results.add(index)
                index += query.length
            }
        }
        currentSearchIndex = if (searchResults.isNotEmpty()) 0 else -1

        if (searchResults.isEmpty()) {
            Toast.makeText(this, R.string.no_results, Toast.LENGTH_SHORT).show()
        } else {
            highlightSearchResults()
            scrollToSearchResult(currentSearchIndex)
        }
    }

    private fun highlightSearchResults() {
        if (searchQuery.isEmpty() || fullText.isEmpty()) {
            displayText(fullText)
            return
        }

        val spannable = SpannableString(fullText)
        var index = 0
        while (true) {
            index = fullText.indexOf(searchQuery, index, ignoreCase = true)
            if (index == -1) break
            spannable.setSpan(
                BackgroundColorSpan(Color.YELLOW),
                index, index + searchQuery.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index += searchQuery.length
        }

        if (currentSearchIndex in searchResults.indices) {
            val pos = searchResults[currentSearchIndex]
            spannable.setSpan(
                BackgroundColorSpan(Color.parseColor("#FF8C00")),
                pos, pos + searchQuery.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.tvContent.text = spannable
    }

    private fun scrollToSearchResult(index: Int) {
        if (index !in searchResults.indices) return
        val pos = searchResults[index]
        val layout = binding.tvContent.layout ?: return
        val line = layout.getLineForOffset(pos)
        val y = layout.getLineTop(line)
        binding.scrollView.smoothScrollTo(0, y - 100)
    }

    private fun navigateSearch(forward: Boolean) {
        if (searchResults.isEmpty()) return
        currentSearchIndex = if (forward) {
            (currentSearchIndex + 1) % searchResults.size
        } else {
            (currentSearchIndex - 1 + searchResults.size) % searchResults.size
        }
        highlightSearchResults()
        scrollToSearchResult(currentSearchIndex)
    }

    private fun hideSearchBar() {
        binding.searchBar.visibility = View.GONE
        searchQuery = ""
        searchResults = emptyList()
        currentSearchIndex = -1
        displayText(fullText)
    }

    // ──────────────────────────── 북마크 ────────────────────────────

    private fun addBookmark() {
        if (fullText.isEmpty()) return
        val scrollY = binding.scrollView.scrollY
        val contentHeight = binding.scrollView.getChildAt(0)?.height ?: 1
        val charIndex = ((scrollY.toFloat() / contentHeight) * fullText.length).toInt()
            .coerceIn(0, fullText.length)
        val preview = fullText.substring(charIndex, minOf(charIndex + 60, fullText.length))
            .replace('\n', ' ')
            .trim()

        val bookmark = Bookmark(
            bookId = bookId,
            scrollPosition = scrollY,
            textPreview = preview
        )
        bookmarks.add(bookmark)
        saveBookmarks()
        Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarks() {
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
            return
        }

        val items = bookmarks.mapIndexed { i, bm ->
            val time = android.text.format.DateFormat.format("MM/dd HH:mm", bm.createdAt)
            "${i + 1}. [$time]\n${bm.textPreview}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.bookmarks)
            .setItems(items) { _, which ->
                val bm = bookmarks[which]
                binding.scrollView.smoothScrollTo(0, bm.scrollPosition)
            }
            .setNeutralButton("삭제") { _, _ ->
                showDeleteBookmarkDialog()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteBookmarkDialog() {
        val items = bookmarks.mapIndexed { i, bm ->
            val time = android.text.format.DateFormat.format("MM/dd HH:mm", bm.createdAt)
            "${i + 1}. [$time] ${bm.textPreview}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("북마크 삭제")
            .setItems(items) { _, which ->
                bookmarks.removeAt(which)
                saveBookmarks()
                Toast.makeText(this, R.string.bookmark_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveBookmarks() {
        val arr = JSONArray()
        bookmarks.forEach { bm ->
            arr.put(JSONObject().apply {
                put("id", bm.id)
                put("bookId", bm.bookId)
                put("scrollPosition", bm.scrollPosition)
                put("textPreview", bm.textPreview)
                put("createdAt", bm.createdAt)
            })
        }
        prefs.edit().putString("bookmarks_$bookId", arr.toString()).apply()
    }

    private fun loadBookmarks() {
        if (bookId == -1L) return
        val saved = prefs.getString("bookmarks_$bookId", null) ?: return
        try {
            val arr = JSONArray(saved)
            bookmarks.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                bookmarks.add(
                    Bookmark(
                        id = obj.getLong("id"),
                        bookId = obj.getLong("bookId"),
                        scrollPosition = obj.getInt("scrollPosition"),
                        textPreview = obj.getString("textPreview"),
                        createdAt = obj.getLong("createdAt")
                    )
                )
            }
        } catch (e: Exception) {
            bookmarks.clear()
        }
    }

    // ────────────────────────────────────────────────────────────────

    private fun setupProgressBar() {
        binding.seekBarProgress.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && fullText.isNotEmpty()) {
                    val targetPos = (binding.scrollView.getChildAt(0).height * progress / 100f).toInt()
                    binding.scrollView.scrollTo(0, targetPos)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            updateProgressBar()
            savePosition()
        }
    }

    private fun updateProgressBar() {
        val scrollY = binding.scrollView.scrollY
        val contentHeight = binding.scrollView.getChildAt(0)?.height ?: return
        val viewHeight = binding.scrollView.height
        val maxScroll = contentHeight - viewHeight
        if (maxScroll > 0) {
            val progress = (scrollY * 100f / maxScroll).toInt()
            binding.seekBarProgress.progress = progress.coerceIn(0, 100)
            binding.tvCurrentPage.text = "$progress%"
            binding.tvTotalPages.text = "총 ${"%,d".format(fullText.length)}자"
        }
    }

    private fun savePosition() {
        if (bookId == -1L) return
        val scrollY = binding.scrollView.scrollY
        val contentHeight = binding.scrollView.getChildAt(0)?.height ?: 0
        val progress = if (contentHeight > 0) scrollY.toFloat() / contentHeight else 0f
        prefs.edit().putInt("pos_$bookId", scrollY).apply()

        getSharedPreferences("progress_updates", Context.MODE_PRIVATE)
            .edit()
            .putFloat("progress_$bookId", progress)
            .putInt("pos_$bookId", scrollY)
            .apply()
    }

    private fun toggleUI() {
        val isVisible = binding.appBarLayout.visibility == View.VISIBLE
        binding.appBarLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.bottomBar.visibility = if (isVisible) View.GONE else View.VISIBLE
    }

    private fun loadSettings() {
        fontSize = prefs.getFloat(PREF_FONT_SIZE, 16f)
        bgTheme = prefs.getInt(PREF_BG_THEME, BG_WHITE)
        lineSpacing = prefs.getFloat(PREF_LINE_SPACING, 1.6f)
    }

    private fun saveSettings() {
        prefs.edit()
            .putFloat(PREF_FONT_SIZE, fontSize)
            .putInt(PREF_BG_THEME, bgTheme)
            .putFloat(PREF_LINE_SPACING, lineSpacing)
            .apply()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reader, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_search -> {
                binding.searchBar.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
                true
            }
            R.id.action_add_bookmark -> { addBookmark(); true }
            R.id.action_show_bookmarks -> { showBookmarks(); true }
            R.id.action_font_size -> showFontSizeDialog()
            R.id.action_background -> showBackgroundDialog()
            R.id.action_book_info -> showBookInfoDialog()
            R.id.action_go_to_page -> showGoToPageDialog()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFontSizeDialog(): Boolean {
        val options = arrayOf("작게 (12sp)", "보통 (16sp)", "크게 (20sp)", "아주 크게 (24sp)")
        val sizes = floatArrayOf(12f, 16f, 20f, 24f)
        val current = sizes.indexOfFirst { it == fontSize }.coerceAtLeast(1)

        AlertDialog.Builder(this)
            .setTitle(R.string.font_size)
            .setSingleChoiceItems(options, current) { dialog, which ->
                fontSize = sizes[which]
                binding.tvContent.textSize = fontSize
                saveSettings()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun showBackgroundDialog(): Boolean {
        val options = arrayOf(
            getString(R.string.bg_white),
            getString(R.string.bg_sepia),
            getString(R.string.bg_dark)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.background_color)
            .setSingleChoiceItems(options, bgTheme) { dialog, which ->
                bgTheme = which
                applyTheme()
                saveSettings()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    private fun showBookInfoDialog(): Boolean {
        val file = File(filePath)
        val sizeStr = when {
            file.length() >= 1024 * 1024 -> "%.1f MB".format(file.length() / (1024.0 * 1024.0))
            file.length() >= 1024 -> "%.1f KB".format(file.length() / 1024.0)
            else -> "${file.length()} B"
        }
        val message = """
            |제목: $bookTitle
            |크기: $sizeStr
            |글자 수: ${"%,d".format(fullText.length)}자
            |형식: ${if (isHtml) "HTML" else "텍스트"}
            |북마크: ${bookmarks.size}개
            |파일: ${file.name}
        """.trimMargin()

        AlertDialog.Builder(this)
            .setTitle(R.string.book_info)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
        return true
    }

    private fun showGoToPageDialog(): Boolean {
        val input = EditText(this).apply {
            hint = "0-100 (%)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.go_to_page)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val percent = input.text.toString().toIntOrNull()?.coerceIn(0, 100)
                    ?: return@setPositiveButton
                val contentHeight = binding.scrollView.getChildAt(0)?.height
                    ?: return@setPositiveButton
                binding.scrollView.smoothScrollTo(0, (contentHeight * percent / 100f).toInt())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return true
    }

    override fun onStop() {
        super.onStop()
        savePosition()
    }
}
