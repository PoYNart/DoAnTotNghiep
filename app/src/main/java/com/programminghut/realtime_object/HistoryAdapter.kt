// File: HistoryAdapter.kt (PHIÊN BẢN ĐÃ SỬA LỖI)
package com.programminghut.realtime_object

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Locale

// ===== THAY ĐỔI TRONG HÀM KHỞI TẠO =====
class HistoryAdapter(
    private val historyList: List<History>,
    private val itemClickListener: OnItemClickListener // Sử dụng interface tự định nghĩa
) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    // Interface tự định nghĩa để giao tiếp với Activity
    interface OnItemClickListener {
        fun onItemLongClick(history: History, position: Int)
    }

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.history_image)
        val labelView: TextView = itemView.findViewById(R.id.history_label)
        val scoreView: TextView = itemView.findViewById(R.id.history_score)
        val timestampView: TextView = itemView.findViewById(R.id.history_timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val historyItem = historyList[position]

        // Dùng Glide để tải ảnh
        Glide.with(holder.itemView.context)
            .load(historyItem.imageUrl)
            .into(holder.imageView)

        holder.labelView.text = "Lớp: ${historyItem.label}"
        holder.scoreView.text = "Điểm: ${String.format("%.2f", historyItem.score)}"

        // Định dạng lại timestamp cho dễ đọc
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.timestampView.text = "Thời gian: ${sdf.format(historyItem.timestamp.toDate())}"

        // Đăng ký sự kiện nhấn giữ vào item
        holder.itemView.setOnLongClickListener {
            itemClickListener.onItemLongClick(historyItem, position)
            true
        }
    }

    override fun getItemCount() = historyList.size
}
