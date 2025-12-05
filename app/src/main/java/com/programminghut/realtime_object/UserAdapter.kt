// File: UserAdapter.kt (PHIÊN BẢN ĐÃ SỬA LỖI CÚ PHÁP)
package com.programminghut.realtime_object

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// ===== HÀM KHỞI TẠO ĐÃ ĐƯỢC SỬA LẠI CHO ĐÚNG CÚ PHÁP =====
class UserAdapter(
    private val userList: List<User>,
    private val userInteractionListener: UserInteractionListener
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    // Interface để giao tiếp với Activity
    interface UserInteractionListener {
        fun onUserLongClick(user: User, position: Int)
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emailTextView: TextView = itemView.findViewById(R.id.user_email_textview)
        val roleTextView: TextView = itemView.findViewById(R.id.user_role_textview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.user_item, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.emailTextView.text = user.email
        holder.roleTextView.text = "Role: ${user.role}"

        // Đăng ký sự kiện nhấn giữ vào item
        holder.itemView.setOnLongClickListener {
            userInteractionListener.onUserLongClick(user, position)
            true
        }
    }

    override fun getItemCount() = userList.size
}
