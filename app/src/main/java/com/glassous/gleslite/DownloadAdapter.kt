package com.glassous.gleslite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.glassous.gleslite.databinding.ItemDownloadBinding

class DownloadAdapter(
    private val downloadList: List<DownloadItem>,
    private val onItemClick: (DownloadItem) -> Unit,
    private val onCancelClick: (DownloadItem) -> Unit,
    private val onPauseResumeClick: (DownloadItem) -> Unit,
    private val onRetryClick: (DownloadItem) -> Unit,
    private val onDeleteClick: (DownloadItem) -> Unit // 新增：删除回调
) : RecyclerView.Adapter<DownloadAdapter.DownloadViewHolder>() {

    inner class DownloadViewHolder(private val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DownloadItem) {
            binding.fileNameText.text = item.fileName
            binding.statusText.text = item.status
            binding.progressText.text = "${item.progress}%"
            binding.speedText.text = item.speed

            binding.root.setOnClickListener { onItemClick(item) }
            binding.cancelButton.setOnClickListener { onCancelClick(item) }
            binding.pauseResumeButton.setOnClickListener { onPauseResumeClick(item) }
            binding.retryButton.setOnClickListener { onRetryClick(item) }
            // 新增：删除按钮点击事件
            binding.deleteButton.setOnClickListener { onDeleteClick(item) }

            // 根据状态显示/隐藏按钮
            binding.cancelButton.visibility = if (item.status == "下载中" || item.status == "等待中") View.VISIBLE else View.GONE
            binding.pauseResumeButton.visibility = if (item.status == "下载中" || item.status == "等待中" || item.status == "已暂停") View.VISIBLE else View.GONE
            binding.retryButton.visibility = if (item.status == "失败") View.VISIBLE else View.GONE
            binding.deleteButton.visibility = View.VISIBLE // 删除按钮始终可见
            binding.pauseResumeButton.text = if (item.status == "已暂停") "继续" else "暂停"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(downloadList[position])
    }

    override fun getItemCount(): Int = downloadList.size
}