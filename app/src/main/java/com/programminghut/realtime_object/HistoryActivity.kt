// File: HistoryActivity.kt (PHIÊN BẢN CÓ CHỨC NĂNG XUẤT CSV)
package com.programminghut.realtime_object

// THÊM CÁC IMPORT CẦN THIẾT
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity(), HistoryAdapter.OnItemClickListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var adapter: HistoryAdapter
    private val historyList = mutableListOf<History>()
    private val documentIds = mutableListOf<String>()

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        toolbar = findViewById(R.id.history_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        historyRecyclerView = findViewById(R.id.history_recyclerview)

        adapter = HistoryAdapter(historyList, this)
        historyRecyclerView.adapter = adapter

        loadHistory()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_delete_all -> {
                showDeleteAllConfirmationDialog()
                true
            }
            // ===== XỬ LÝ SỰ KIỆN NHẤN NÚT XUẤT CSV =====
            R.id.menu_export_csv -> {
                checkStoragePermissionAndExport()
                true
            }
            // ==========================================
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ... (các hàm loadHistory, showDeleteAllConfirmationDialog, deleteAllHistory, onItemLongClick, deleteSingleHistoryItem giữ nguyên)
    private fun loadHistory() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                historyList.clear()
                documentIds.clear()
                if (documents.isEmpty) {
                    // Toast.makeText(this, "Không có dữ liệu lịch sử.", Toast.LENGTH_SHORT).show()
                } else {
                    for (document in documents) {
                        historyList.add(document.toObject(History::class.java))
                        documentIds.add(document.id)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.e("HistoryActivity", "Lỗi khi tải lịch sử", exception)
                Toast.makeText(this, "Lỗi khi tải lịch sử: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa tất cả lịch sử không? Hành động này không thể hoàn tác.")
            .setPositiveButton("Xóa") { _, _ ->
                deleteAllHistory()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun deleteAllHistory() {
        val userId = auth.currentUser?.uid ?: return
        val collectionRef = db.collection("users").document(userId).collection("history")
        collectionRef.get().addOnSuccessListener { documents ->
            if (documents.isEmpty) {
                Toast.makeText(this, "Không có gì để xóa.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val batch: WriteBatch = db.batch()
            for (document in documents) {
                batch.delete(document.reference)
            }
            batch.commit().addOnSuccessListener {
                Toast.makeText(this, "Đã xóa tất cả lịch sử!", Toast.LENGTH_SHORT).show()
                historyList.clear()
                documentIds.clear()
                adapter.notifyDataSetChanged()
            }
                .addOnFailureListener {
                    Toast.makeText(this, "Lỗi khi xóa lịch sử.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onItemLongClick(history: History, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Xóa mục này?")
            .setMessage("Bạn có chắc chắn muốn xóa mục lịch sử này không?")
            .setPositiveButton("Xóa") { _, _ ->
                deleteSingleHistoryItem(position)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun deleteSingleHistoryItem(position: Int) {
        val userId = auth.currentUser?.uid ?: return
        if (position >= documentIds.size) return
        val docId = documentIds[position]
        db.collection("users").document(userId).collection("history").document(docId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Đã xóa mục!", Toast.LENGTH_SHORT).show()
                historyList.removeAt(position)
                documentIds.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, historyList.size)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi khi xóa mục.", Toast.LENGTH_SHORT).show()
            }
    }

    // ===== CÁC HÀM MỚI CHO VIỆC XUẤT CSV =====

    private fun checkStoragePermissionAndExport() {
        // Với Android 10 (API 29) trở lên, không cần quyền ghi bộ nhớ ngoài khi lưu vào thư mục chung như Downloads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportHistoryToCsv()
        } else {
            // Với Android 9 trở xuống, cần xin quyền
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                exportHistoryToCsv()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportHistoryToCsv()
            } else {
                Toast.makeText(this, "Quyền ghi bộ nhớ bị từ chối.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Trong file HistoryActivity.kt

// ... (các hàm khác giữ nguyên)

    // ===== HÀM XUẤT CSV ĐÃ ĐƯỢC SỬA LỖI =====
    // Trong file HistoryActivity.kt

// ... (các hàm khác giữ nguyên)

    // ===== HÀM XUẤT CSV ĐÃ ĐƯỢC SỬA LẠI ĐỂ DÙNG DẤU CHẤM PHẨY =====
    private fun exportHistoryToCsv() {
        if (historyList.isEmpty()) {
            Toast.makeText(this, "Không có dữ liệu để xuất.", Toast.LENGTH_LONG).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "lich_su_nhan_dien_$timestamp.csv"

        // ===== SỬA DÒNG NÀY =====
        // Tạo header cho file CSV, dùng dấu chấm phẩy
        val csvHeader = "Label;Score;ImageUrl;Timestamp\n"
        val csvContent = StringBuilder(csvHeader)

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        // Thêm từng dòng dữ liệu, dùng dấu chấm phẩy
        for (item in historyList) {
            val formattedTimestamp = sdf.format(item.timestamp.toDate())

            // ===== VÀ SỬA DÒNG NÀY =====
            // Thay thế dấu phẩy trong số thập phân bằng dấu chấm để đảm bảo tính nhất quán
            val formattedScore = String.format(Locale.US, "%.6f", item.score)

            csvContent.append("${item.label};${formattedScore};${item.imageUrl};\"$formattedTimestamp\"\n")
        }

        try {
            saveCsvFile(fileName, csvContent.toString())
            Toast.makeText(this, "Đã xuất file thành công vào thư mục Downloads!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khi xuất file: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("HistoryActivity", "Lỗi khi lưu file CSV", e)
        }
    }

// ... (các hàm khác giữ nguyên)



// ... (các hàm khác giữ nguyên)


    private fun saveCsvFile(fileName: String, content: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            // Lưu vào thư mục Downloads
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            val outputStream: OutputStream? = resolver.openOutputStream(it)
            outputStream?.use { stream ->
                stream.write(content.toByteArray())
            }
        } ?: throw Exception("Không thể tạo file trong MediaStore.")
    }
    // ==========================================
}
