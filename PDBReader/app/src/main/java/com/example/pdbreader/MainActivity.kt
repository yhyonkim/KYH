package com.example.pdbreader

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pdbreader.adapter.BookAdapter
import com.example.pdbreader.databinding.ActivityMainBinding
import com.example.pdbreader.model.PdbBook
import com.example.pdbreader.ui.LibraryViewModel
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var bookAdapter: BookAdapter

    // 파일 선택 결과 처리
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                copyAndAddPdbFile(uri)
            }
        }
    }

    // 권한 요청 결과 처리
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.all { it.value }
        if (granted) openFilePicker()
        else Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        observeViewModel()

        binding.fabAddBook.setOnClickListener {
            checkPermissionsAndOpenPicker()
        }

        // 외부에서 파일 열기 처리
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                copyAndAddPdbFile(uri)
            }
        }
    }

    private fun setupRecyclerView() {
        bookAdapter = BookAdapter(
            onBookClick = { book -> openBook(book) },
            onBookDelete = { book -> confirmDelete(book) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = bookAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.books.observe(this) { books ->
            bookAdapter.submitList(books)
            binding.emptyView.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (books.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.fabAddBook.isEnabled = !loading
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun checkPermissionsAndOpenPicker() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ : READ_MEDIA_IMAGES 등 세분화된 권한 (파일 선택에는 불필요)
                openFilePicker()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                when {
                    ContextCompat.checkSelfPermission(this, permission) ==
                            PackageManager.PERMISSION_GRANTED -> openFilePicker()
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permission) -> {
                        AlertDialog.Builder(this)
                            .setMessage(R.string.permission_required)
                            .setPositiveButton(R.string.grant_permission) { _, _ ->
                                permissionLauncher.launch(arrayOf(permission))
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                    else -> permissionLauncher.launch(arrayOf(permission))
                }
            }
            else -> openFilePicker()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream",
                "application/x-mobipocket-ebook",
                "*/*"
            ))
        }
        filePickerLauncher.launch(Intent.createChooser(intent, "PDB 파일 선택"))
    }

    /**
     * URI로 지정된 PDB 파일을 앱 내부 저장소로 복사하고 서재에 추가합니다.
     */
    private fun copyAndAddPdbFile(uri: Uri) {
        try {
            val fileName = getFileNameFromUri(uri) ?: "book_${System.currentTimeMillis()}.pdb"

            // .pdb 확장자가 없으면 추가
            val finalName = if (fileName.endsWith(".pdb", ignoreCase = true)) fileName
            else "$fileName.pdb"

            val destDir = File(filesDir, "books").also { it.mkdirs() }
            val destFile = File(destDir, finalName)

            // 파일 복사
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            viewModel.addBook(destFile)
        } catch (e: Exception) {
            Toast.makeText(this, "파일 복사 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment
    }

    private fun openBook(book: PdbBook) {
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
            putExtra(ReaderActivity.EXTRA_FILE_PATH, book.filePath)
            putExtra(ReaderActivity.EXTRA_TITLE, book.title)
        }
        startActivity(intent)
    }

    private fun confirmDelete(book: PdbBook) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_book)
            .setMessage(R.string.delete_book_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.removeBook(book)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
