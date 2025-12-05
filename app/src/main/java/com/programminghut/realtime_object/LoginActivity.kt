// File: LoginActivity.kt (PHIÊN BẢN HOÀN CHỈNH)
package com.programminghut.realtime_object

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Tự động đăng nhập nếu người dùng đã có phiên làm việc
        if (auth.currentUser != null) {
            redirectUser()
        }

        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnRegister = findViewById<Button>(R.id.btn_register)
        val btnLogin = findViewById<Button>(R.id.btn_login)

        // Sự kiện cho nút ĐĂNG KÝ
        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || password.length < 6) {
                Toast.makeText(this, "Email không được trống, mật khẩu phải có ít nhất 6 ký tự", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            // Khi đăng ký, mặc định vai trò là 'user'
                            val userMap = hashMapOf(
                                "email" to user.email,
                                "role" to "user"
                            )
                            db.collection("users").document(user.uid)
                                .set(userMap)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Đăng ký và tạo vai trò thành công!", Toast.LENGTH_SHORT).show()
                                    redirectUser()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Đăng ký Auth thành công, nhưng lỗi khi tạo vai trò: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Đăng ký thất bại: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // Sự kiện cho nút ĐĂNG NHẬP
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập email và mật khẩu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                        redirectUser()
                    } else {
                        Toast.makeText(this, "Đăng nhập thất bại. Vui lòng kiểm tra lại.", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun redirectUser() {
        val userId = auth.currentUser?.uid ?: return

        Toast.makeText(this, "Đang kiểm tra vai trò người dùng...", Toast.LENGTH_SHORT).show()

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role")

                    // PHÂN QUYỀN Ở ĐÂY
                    if (role == "admin") {
                        Toast.makeText(this, "Chào mừng Admin!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Chào mừng User!", Toast.LENGTH_SHORT).show()
                    }

                    // Dù là Admin hay User đều vào MainActivity
                    startActivity(Intent(this, MainActivity::class.java))
                    finish() // Đóng LoginActivity để không quay lại được

                } else {
                    // Trường hợp hiếm gặp: Có tài khoản Auth nhưng không có document trong Firestore
                    Toast.makeText(this, "Không tìm thấy thông tin vai trò. Vui lòng liên hệ hỗ trợ.", Toast.LENGTH_LONG).show()
                    auth.signOut() // Đăng xuất người dùng để tránh bị kẹt
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Lỗi kết nối khi lấy vai trò: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }
}
