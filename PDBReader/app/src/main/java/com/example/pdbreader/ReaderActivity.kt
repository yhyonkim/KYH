package com.example.pdbreader

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.GestureDetector
import android.view.KeyEvent
import androidx.core.view.WindowCompat
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
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
import java.util.Locale

class ReaderActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityReaderBinding
    private lateinit var prefs: SharedPreferences

    private var bookId: Long = -1
    private var filePath: String = ""
    private var bookTitle: String = ""
    private var fullText: String = ""
    private var isHtml: Boolean = false

    // 읽기 설정
    private var fontSize = 16f
    private var bgTheme = BG_AUTO   // 기본값: 시스템 따름
    private var lineSpacing = 1.6f

    // 검색
    private var searchQuery = ""
    private var searchResults = listOf<Int>()
    private var currentSearchIndex = -1

    // 북마크
    private val bookmarks = mutableListOf<Bookmark>()

    // TTS
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isSpeaking = false
    private var ttsMenuItem: MenuItem? = null

    // 스와이프 제스처
    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                val dx = e2.x - (e1?.x ?: 0f)
                val dy = e2.y - (e1?.y ?: 0f)
                // 수평 스와이프가 수직보다 클 때만 페이지 이동
                if (Math.abs(dx) > Math.abs(dy) * 1.5f &&
                    Math.abs(dx) > SWIPE_THRESHOLD &&
                    Math.abs(velocityX) > SWIPE_VELOCITY) {
                    if (dx < 0) pageDown() else pageUp()
                    return true
                }
                return false
            }
        })
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_TITLE = "extra_title"

        const val BG_AUTO  = -1   // 시스템 다크 모드 연동
        const val BG_WHITE =  0
        const val BG_SEPIA =  1
        const val BG_DARK  =  2

        private const val PREF_FONT_SIZE    = "font_size"
        private const val PREF_BG_THEME     = "bg_theme"
        private const val PREF_LINE_SPACING = "line_spacing"

        private const val TTS_CHUNK          = 3000   // TTS는 한 번에 최대 4000자
        private const val SWIPE_THRESHOLD    = 100f   // px
        private const val SWIPE_VELOCITY     = 100f   // px/s
        private const val SHARE_MAX_LENGTH   = 5000   // 공유 최대 글자 수
    }

    // ────────────────────────── 생명주기 ──────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

        bookId   = intent.getLongExtra(EXTRA_BOOK_ID, -1)
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

        // TTS 초기화
        tts = TextToSpeech(this, this)

        if (filePath.isNotEmpty()) loadBook()
        else { Toast.makeText(this, R.string.error_reading_file, Toast.LENGTH_LONG).show(); finish() }

        binding.scrollView.setOnClickListener { toggleUI() }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        tts?.stop()
    }

    // ────────────────────────── TTS 콜백 ──────────────────────────

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                       result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) {
                // 한국어 없으면 영어로 폴백
                tts?.setLanguage(Locale.ENGLISH)
                ttsReady = true
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { updateTtsMenuItem(speaking = false) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { updateTtsMenuItem(speaking = false) }
                }
            })
        }
    }

    private fun toggleTts() {
        if (!ttsReady) {
            Toast.makeText(this, R.string.tts_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
            updateTtsMenuItem(speaking = false)
        } else {
            val text = if (isHtml) {
                // HTML 태그 제거 후 읽기
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    Html.fromHtml(fullText, Html.FROM_HTML_MODE_COMPACT).toString()
                else @Suppress("DEPRECATION") Html.fromHtml(fullText).toString()
            } else fullText

            if (text.isBlank()) return

            // 긴 텍스트는 청크로 분할하여 큐에 추가
            val chunks = text.chunked(TTS_CHUNK)
            chunks.forEachIndexed { idx, chunk ->
                val params = android.os.Bundle()
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                tts?.speak(chunk, if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                    params, "chunk_$idx")
            }
            isSpeaking = true
            updateTtsMenuItem(speaking = true)
            Toast.makeText(this, R.string.tts_reading, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTtsMenuItem(speaking: Boolean) {
        isSpeaking = speaking
        ttsMenuItem?.title = getString(if (speaking) R.string.tts_stop else R.string.tts_read)
    }

    // ────────────────────────── 책 로드 ──────────────────────────

    private fun loadBook() {
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvContent.text = ""

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    PalmDocReader(File(filePath)).readAll()
                }
                fullText = result.text
                isHtml   = result.isHtml
                displayText(fullText)
                updateProgressBar()

                val savedPos = prefs.getInt("pos_$bookId", 0)
                if (savedPos > 0) {
                    binding.scrollView.post { binding.scrollView.scrollTo(0, savedPos) }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
                else @Suppress("DEPRECATION") Html.fromHtml(text)
            } else text
        }
    }

    // ────────────────────────── 테마 ──────────────────────────────

    private fun isSystemDark(): Boolean {
        return (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyTheme() {
        val effective = if (bgTheme == BG_AUTO) {
            if (isSystemDark()) BG_DARK else BG_WHITE
        } else bgTheme

        val (bgColor, textColor) = when (effective) {
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

        if (effective == BG_DARK) {
            binding.toolbar.setBackgroundColor(resources.getColor(R.color.toolbar_dark, theme))
            binding.bottomBar.setBackgroundColor(resources.getColor(R.color.toolbar_dark, theme))
        } else {
            binding.toolbar.setBackgroundColor(resources.getColor(R.color.primary, theme))
            binding.bottomBar.setBackgroundColor(resources.getColor(R.color.primary, theme))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 시스템 다크 모드 전환 시 BG_AUTO 모드라면 즉시 반영
        if (bgTheme == BG_AUTO) applyTheme()
    }

    // ────────────────────────── 검색 ──────────────────────────────

    private fun setupSearchBar() {
        binding.etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER)) {
                performSearch(binding.etSearch.text.toString()); true
            } else false
        }
        binding.btnSearchNext.setOnClickListener  { navigateSearch(forward = true)  }
        binding.btnSearchPrev.setOnClickListener  { navigateSearch(forward = false) }
        binding.btnSearchClose.setOnClickListener { hideSearchBar() }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        searchQuery = query
        val results = mutableListOf<Int>()
        var index = 0
        while (true) {
            index = fullText.indexOf(query, index, ignoreCase = true)
            if (index == -1) break
            results.add(index)
            index += query.length
        }
        searchResults = results
        currentSearchIndex = if (results.isNotEmpty()) 0 else -1

        if (searchResults.isEmpty()) Toast.makeText(this, R.string.no_results, Toast.LENGTH_SHORT).show()
        else { highlightSearchResults(); scrollToSearchResult(currentSearchIndex) }
    }

    private fun highlightSearchResults() {
        if (searchQuery.isEmpty() || fullText.isEmpty()) { displayText(fullText); return }
        val spannable = SpannableString(fullText)
        var index = 0
        while (true) {
            index = fullText.indexOf(searchQuery, index, ignoreCase = true)
            if (index == -1) break
            spannable.setSpan(BackgroundColorSpan(Color.YELLOW),
                index, index + searchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            index += searchQuery.length
        }
        if (currentSearchIndex in searchResults.indices) {
            val pos = searchResults[currentSearchIndex]
            spannable.setSpan(BackgroundColorSpan(Color.parseColor("#FF8C00")),
                pos, pos + searchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvContent.text = spannable
    }

    private fun scrollToSearchResult(index: Int) {
        if (index !in searchResults.indices) return
        val layout = binding.tvContent.layout ?: return
        val y = layout.getLineTop(layout.getLineForOffset(searchResults[index]))
        binding.scrollView.smoothScrollTo(0, (y - 100).coerceAtLeast(0))
    }

    private fun navigateSearch(forward: Boolean) {
        if (searchResults.isEmpty()) return
        currentSearchIndex = if (forward) (currentSearchIndex + 1) % searchResults.size
        else (currentSearchIndex - 1 + searchResults.size) % searchResults.size
        highlightSearchResults()
        scrollToSearchResult(currentSearchIndex)
    }

    private fun hideSearchBar() {
        binding.searchBar.visibility = View.GONE
        searchQuery = ""; searchResults = emptyList(); currentSearchIndex = -1
        displayText(fullText)
    }

    // ────────────────────────── 북마크 ────────────────────────────

    private fun addBookmark() {
        if (fullText.isEmpty()) return
        val scrollY = binding.scrollView.scrollY
        val contentHeight = binding.scrollView.getChildAt(0)?.height ?: 1
        val charIndex = ((scrollY.toFloat() / contentHeight) * fullText.length)
            .toInt().coerceIn(0, fullText.length)
        val preview = fullText.substring(charIndex, minOf(charIndex + 60, fullText.length))
            .replace('\n', ' ').trim()

        bookmarks.add(Bookmark(bookId = bookId, scrollPosition = scrollY, textPreview = preview))
        saveBookmarks()
        Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarks() {
        if (bookmarks.isEmpty()) { Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show(); return }
        val items = bookmarks.mapIndexed { i, bm ->
            val t = android.text.format.DateFormat.format("MM/dd HH:mm", bm.createdAt)
            "${i + 1}. [$t]\n${bm.textPreview}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.bookmarks)
            .setItems(items) { _, which -> binding.scrollView.smoothScrollTo(0, bookmarks[which].scrollPosition) }
            .setNeutralButton("삭제") { _, _ -> showDeleteBookmarkDialog() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteBookmarkDialog() {
        val items = bookmarks.mapIndexed { i, bm ->
            val t = android.text.format.DateFormat.format("MM/dd HH:mm", bm.createdAt)
            "${i + 1}. [$t] ${bm.textPreview}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("북마크 삭제")
            .setItems(items) { _, which ->
                bookmarks.removeAt(which); saveBookmarks()
                Toast.makeText(this, R.string.bookmark_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun saveBookmarks() {
        val arr = JSONArray()
        bookmarks.forEach { bm ->
            arr.put(JSONObject().apply {
                put("id", bm.id); put("bookId", bm.bookId)
                put("scrollPosition", bm.scrollPosition)
                put("textPreview", bm.textPreview); put("createdAt", bm.createdAt)
            })
        }
        prefs.edit().putString("bookmarks_$bookId", arr.toString()).apply()
    }

    private fun loadBookmarks() {
        if (bookId == -1L) return
        val saved = prefs.getString("bookmarks_$bookId", null) ?: return
        try {
            val arr = JSONArray(saved); bookmarks.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                bookmarks.add(Bookmark(o.getLong("id"), o.getLong("bookId"),
                    o.getInt("scrollPosition"), o.getString("textPreview"), o.getLong("createdAt")))
            }
        } catch (e: Exception) { bookmarks.clear() }
    }

    // ─────────────────────── 진행률 / 위치 ───────────────────────

    private fun setupProgressBar() {
        binding.seekBarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val h = binding.scrollView.getChildAt(0)?.height ?: return
                    binding.scrollView.scrollTo(0, (h * p / 100f).toInt())
                }
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            updateProgressBar(); savePosition()
        }
    }

    private fun updateProgressBar() {
        val scrollY = binding.scrollView.scrollY
        val contentHeight = binding.scrollView.getChildAt(0)?.height ?: return
        val maxScroll = contentHeight - binding.scrollView.height
        if (maxScroll > 0) {
            val progress = (scrollY * 100f / maxScroll).toInt().coerceIn(0, 100)
            binding.seekBarProgress.progress = progress
            binding.tvCurrentPage.text = "$progress%"
            binding.tvTotalPages.text = "%,d자".format(fullText.length)
        }
    }

    private fun savePosition() {
        if (bookId == -1L) return
        val scrollY = binding.scrollView.scrollY
        val h = binding.scrollView.getChildAt(0)?.height ?: 0
        val progress = if (h > 0) scrollY.toFloat() / h else 0f
        prefs.edit().putInt("pos_$bookId", scrollY).apply()
        getSharedPreferences("progress_updates", Context.MODE_PRIVATE).edit()
            .putFloat("progress_$bookId", progress).putInt("pos_$bookId", scrollY).apply()
    }

    private fun toggleUI() {
        val visible = binding.appBarLayout.visibility == View.VISIBLE
        binding.appBarLayout.visibility = if (visible) View.GONE else View.VISIBLE
        binding.bottomBar.visibility    = if (visible) View.GONE else View.VISIBLE
    }

    // ─────────────────────── 스와이프 제스처 ─────────────────────

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun pageDown() {
        val pageHeight = binding.scrollView.height
        binding.scrollView.smoothScrollBy(0, pageHeight)
    }

    private fun pageUp() {
        val pageHeight = binding.scrollView.height
        binding.scrollView.smoothScrollBy(0, -pageHeight)
    }

    // ─────────────────────── 텍스트 공유 ─────────────────────────

    private fun shareText() {
        if (fullText.isEmpty()) {
            Toast.makeText(this, R.string.error_reading_file, Toast.LENGTH_SHORT).show()
            return
        }
        // 현재 위치 기준으로 일부 텍스트 공유 (최대 SHARE_MAX_LENGTH자)
        val scrollY = binding.scrollView.scrollY
        val contentH = binding.scrollView.getChildAt(0)?.height ?: 1
        val startChar = ((scrollY.toFloat() / contentH) * fullText.length)
            .toInt().coerceIn(0, fullText.length)
        val shareText = fullText.substring(startChar, minOf(startChar + SHARE_MAX_LENGTH, fullText.length))

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, bookTitle)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_text)))
    }

    // ─────────────────────── 설정 저장/로드 ──────────────────────

    private fun loadSettings() {
        fontSize    = prefs.getFloat(PREF_FONT_SIZE, 16f)
        bgTheme     = prefs.getInt(PREF_BG_THEME, BG_AUTO)
        lineSpacing = prefs.getFloat(PREF_LINE_SPACING, 1.6f)
    }

    private fun saveSettings() {
        prefs.edit()
            .putFloat(PREF_FONT_SIZE, fontSize)
            .putInt(PREF_BG_THEME, bgTheme)
            .putFloat(PREF_LINE_SPACING, lineSpacing)
            .apply()
    }

    // ─────────────────────── 메뉴 ────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reader, menu)
        ttsMenuItem = menu.findItem(R.id.action_tts)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home          -> { onBackPressedDispatcher.onBackPressed(); true }
        R.id.action_search         -> { binding.searchBar.visibility = View.VISIBLE; binding.etSearch.requestFocus(); true }
        R.id.action_tts            -> { toggleTts(); true }
        R.id.action_add_bookmark   -> { addBookmark(); true }
        R.id.action_show_bookmarks -> { showBookmarks(); true }
        R.id.action_share          -> { shareText(); true }
        R.id.action_font_size      -> showFontSizeDialog()
        R.id.action_background     -> showBackgroundDialog()
        R.id.action_book_info      -> showBookInfoDialog()
        R.id.action_go_to_page     -> showGoToPageDialog()
        else                       -> super.onOptionsItemSelected(item)
    }

    // ────────────────────── 다이얼로그들 ─────────────────────────

    private fun showFontSizeDialog(): Boolean {
        val options = arrayOf("작게 (12sp)", "보통 (16sp)", "크게 (20sp)", "아주 크게 (24sp)")
        val sizes   = floatArrayOf(12f, 16f, 20f, 24f)
        val current = sizes.indexOfFirst { it == fontSize }.coerceAtLeast(1)
        AlertDialog.Builder(this)
            .setTitle(R.string.font_size)
            .setSingleChoiceItems(options, current) { dialog, which ->
                fontSize = sizes[which]; binding.tvContent.textSize = fontSize; saveSettings(); dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null).show()
        return true
    }

    private fun showBackgroundDialog(): Boolean {
        val options = arrayOf(
            "시스템 자동 (${getString(R.string.dark_mode_auto)})",
            getString(R.string.bg_white),
            getString(R.string.bg_sepia),
            getString(R.string.bg_dark)
        )
        val current = when (bgTheme) { BG_AUTO -> 0; BG_WHITE -> 1; BG_SEPIA -> 2; else -> 3 }
        AlertDialog.Builder(this)
            .setTitle(R.string.background_color)
            .setSingleChoiceItems(options, current) { dialog, which ->
                bgTheme = when (which) { 0 -> BG_AUTO; 1 -> BG_WHITE; 2 -> BG_SEPIA; else -> BG_DARK }
                applyTheme(); saveSettings(); dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null).show()
        return true
    }

    private fun showBookInfoDialog(): Boolean {
        val file = File(filePath)
        val size = when {
            file.length() >= 1024 * 1024 -> "%.1f MB".format(file.length() / (1024.0 * 1024.0))
            file.length() >= 1024        -> "%.1f KB".format(file.length() / 1024.0)
            else                         -> "${file.length()} B"
        }
        val msg = """
            |제목: $bookTitle
            |크기: $size
            |글자 수: ${"%,d".format(fullText.length)}자
            |형식: ${if (isHtml) "HTML" else "텍스트"}
            |북마크: ${bookmarks.size}개
            |파일: ${file.name}
        """.trimMargin()
        AlertDialog.Builder(this).setTitle(R.string.book_info).setMessage(msg)
            .setPositiveButton(R.string.ok, null).show()
        return true
    }

    private fun showGoToPageDialog(): Boolean {
        val input = EditText(this).apply { hint = "0-100 (%)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle(R.string.go_to_page).setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val pct = input.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: return@setPositiveButton
                val h   = binding.scrollView.getChildAt(0)?.height ?: return@setPositiveButton
                binding.scrollView.smoothScrollTo(0, (h * pct / 100f).toInt())
            }
            .setNegativeButton(R.string.cancel, null).show()
        return true
    }
}
