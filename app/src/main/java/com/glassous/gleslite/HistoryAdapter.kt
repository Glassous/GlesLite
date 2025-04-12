package com.glassous.gleslite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.glassous.gleslite.databinding.ItemHistoryBinding
import com.glassous.gleslite.databinding.ItemHistoryHeaderBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private val historyGroups: MutableList<HistoryGroup>,
    private val onItemClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    class HeaderViewHolder(private val binding: ItemHistoryHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            binding.headerText.text = date
        }
    }

    class HistoryViewHolder(
        private val binding: ItemHistoryBinding,
        private val onItemClick: (HistoryItem) -> Unit,
        private val onDeleteClick: (HistoryItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(history: HistoryItem) {
            binding.historyTitle.text = history.title
            binding.historyUrl.text = history.url
            binding.root.setOnClickListener { onItemClick(history) }
            binding.deleteButton.setOnClickListener { onDeleteClick(history) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val binding = ItemHistoryHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            HistoryViewHolder(binding, onItemClick, onDeleteClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val (groupIndex, itemIndex) = getGroupAndItemIndex(position)
        if (holder is HeaderViewHolder) {
            holder.bind(historyGroups[groupIndex].date)
        } else if (holder is HistoryViewHolder) {
            holder.bind(historyGroups[groupIndex].items[itemIndex - 1])
        }
    }

    override fun getItemCount(): Int {
        return historyGroups.sumOf { it.items.size + 1 } // 每个分组包含一个头部 + 条目数量
    }

    override fun getItemViewType(position: Int): Int {
        val (groupIndex, itemIndex) = getGroupAndItemIndex(position)
        return if (itemIndex == 0) TYPE_HEADER else TYPE_ITEM
    }

    private fun getGroupAndItemIndex(position: Int): Pair<Int, Int> {
        var currentPosition = 0
        historyGroups.forEachIndexed { groupIndex, group ->
            val groupSize = group.items.size + 1 // 包括头部
            if (position < currentPosition + groupSize) {
                return Pair(groupIndex, position - currentPosition)
            }
            currentPosition += groupSize
        }
        return Pair(historyGroups.size - 1, 0)
    }

    fun updateData(historyItems: List<HistoryItem>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val grouped = historyItems.groupBy { dateFormat.format(it.timestamp) }
        val newGroups = grouped.entries.sortedByDescending { it.key }.map { entry ->
            HistoryGroup(
                date = entry.key,
                items = entry.value
            )
        }
        historyGroups.clear()
        historyGroups.addAll(newGroups)
        notifyDataSetChanged()
    }
}