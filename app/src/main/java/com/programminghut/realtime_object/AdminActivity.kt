// File: AdminActivity.kt (PHIÊN BẢN CÓ CHỨC NĂNG XÓA)
package com.programminghut.realtime_object

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ===== IMPLEMENT INTERFACE CỦA ADAPTER =====
class AdminActivity : AppCompatActivity(), UserAdapter.UserInteractionListener {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth // Thêm biến Auth
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var adapter: UserAdapter
    private val userList = mutableListOf<User>()
    private val userDocIds = mutableListOf<String>() // Lưu ID của document để biết cần xóa document nào

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val toolbar: Toolbar = findViewById(R.id.admin_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance() // Khởi tạo Auth

        usersRecyclerView = findViewById(R.id.users_recyclerview)
        // ===== SỬA LỖI Ở ĐÂY: TRUYỀN 'this' VÀO ADAPTER =====
        // Khởi tạo adapter với 'this' (chính là Activity này) làm listener
        adapter = UserAdapter(userList, this)
        usersRecyclerView.adapter = adapter

        loadUsers()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadUsers() {
        db.collection("users")
            .get()
            .addOnSuccessListener { documents ->
                userList.clear()
                userDocIds.clear()

                for (document in documents) {
                    val user = document.toObject(User::class.java)
                    // Lọc để không hiển thị chính tài khoản admin đang đăng nhập
                    if (document.id != auth.currentUser?.uid) {
                        userList.add(user)
                        userDocIds.add(document.id)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Lỗi khi tải danh sách người dùng: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ===== HÀM XỬ LÝ SỰ KIỆN NHẤN GIỮ TỪ ADAPTER =====
    override fun onUserLongClick(user: User, position: Int) {
        // Không cho phép xóa một admin khác
        if (user.role == "admin") {
            Toast.makeText(this, "Không thể xóa tài khoản Admin khác.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Xóa người dùng")
            .setMessage("Bạn có chắc chắn muốn xóa người dùng '${user.email}' không? Hành động này sẽ xóa vĩnh viễn thông tin và không thể hoàn tác.")
            .setPositiveButton("Xóa") { _, _ ->
                deleteUser(position)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    // ===================================================

    // ===== HÀM XÓA MỘT NGƯỜI DÙNG =====
    private fun deleteUser(position: Int) {
        if (position >= userDocIds.size) return // Kiểm tra an toàn, tránh crash

        val userDocId = userDocIds[position]

        // Bước 1: Xóa document trong Firestore
        db.collection("users").document(userDocId).delete()
            .addOnSuccessListener {
                Log.d("AdminActivity", "Đã xóa document của người dùng trong Firestore.")

                // LƯU Ý QUAN TRỌNG:
                // Việc xóa tài khoản trong Firebase Authentication từ client-side là rất hạn chế
                // và cần người dùng phải đăng nhập lại gần đây.
                // Một Admin không thể xóa tài khoản Auth của người khác từ ứng dụng.
                // Chức năng này đúng ra phải được xử lý ở backend với Firebase Admin SDK.
                // Trong khuôn khổ dự án này, chúng ta chỉ xóa dữ liệu trên Firestore.

                Toast.makeText(this, "Đã xóa thông tin người dùng!", Toast.LENGTH_SHORT).show()

                // Cập nhật lại giao diện
                userList.removeAt(position)
                userDocIds.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, userList.size)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi khi xóa thông tin người dùng: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    // ===================================
}
