package com.glassous.gleslite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class FavoritesAdapter(
    private val favorites: MutableList<Favorite>,
    private val onItemClick: (Favorite) -> Unit,
    private val onItemDelete: (Int) -> Unit,
    private val onItemEdit: (Int, Favorite) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        val urlTextView: TextView = itemView.findViewById(R.id.urlTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val favorite = favorites[position]
        holder.titleTextView.text = favorite.title
        holder.urlTextView.text = favorite.url

        // 点击列表项，触发 onItemClick
        holder.itemView.setOnClickListener {
            onItemClick(favorite)
        }

        // 长按删除
        holder.itemView.setOnLongClickListener {
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("删除收藏")
                .setMessage("确定要删除 ${favorite.title} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    onItemDelete(position)
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        // 点击编辑（可以添加一个编辑按钮，这里简化为点击标题触发编辑）
        holder.titleTextView.setOnClickListener {
            val dialogView = LayoutInflater.from(holder.itemView.context)
                .inflate(R.layout.dialog_edit_favorite, null)
            val titleEditText = dialogView.findViewById<TextInputEditText>(R.id.editTitleEditText)
            val urlEditText = dialogView.findViewById<TextInputEditText>(R.id.editUrlEditText)

            titleEditText.setText(favorite.title)
            urlEditText.setText(favorite.url)

            AlertDialog.Builder(holder.itemView.context)
                .setTitle("编辑收藏")
                .setView(dialogView)
                .setPositiveButton("保存") { _, _ ->
                    val newTitle = titleEditText.text.toString()
                    val newUrl = urlEditText.text.toString()
                    if (newTitle.isNotEmpty() && newUrl.isNotEmpty()) {
                        val updatedFavorite = Favorite(newTitle, newUrl)
                        onItemEdit(position, updatedFavorite)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun getItemCount(): Int = favorites.size
}