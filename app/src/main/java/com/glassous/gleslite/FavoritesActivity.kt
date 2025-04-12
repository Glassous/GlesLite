package com.glassous.gleslite

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.glassous.gleslite.databinding.ActivityFavoritesBinding
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var favorites: MutableList<Favorite>
    private lateinit var adapter: FavoritesAdapter
    private lateinit var currentFavorite: Favorite

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)

        // 获取当前浏览网页
        currentFavorite = intent.getSerializableExtra("current_favorite") as Favorite
        binding.currentPageTitle.text = currentFavorite.title
        binding.currentPageUrl.text = currentFavorite.url

        // 加载收藏列表
        loadFavorites()

        // 设置 RecyclerView
        adapter = FavoritesAdapter(
            favorites,
            onItemClick = { favorite ->
                // 点击列表项，返回主页并加载网页
                Toast.makeText(this, "返回主页后打开这个网页", Toast.LENGTH_SHORT).show()
                val intent = Intent().apply {
                    putExtra("selected_url", favorite.url)
                }
                setResult(RESULT_OK, intent)
                finish()
            },
            onItemDelete = { position ->
                // 删除收藏
                favorites.removeAt(position)
                adapter.notifyItemRemoved(position)
                saveFavorites()
            },
            onItemEdit = { position, updatedFavorite ->
                // 修改收藏
                favorites[position] = updatedFavorite
                adapter.notifyItemChanged(position)
                saveFavorites()
            }
        )
        binding.favoritesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.favoritesRecyclerView.adapter = adapter

        // 返回键点击事件
        binding.backButton.setOnClickListener {
            finish()
        }

        // 一键收藏按钮点击事件
        binding.addFavoriteButton.setOnClickListener {
            if (favorites.none { it.url == currentFavorite.url }) {
                favorites.add(currentFavorite)
                adapter.notifyItemInserted(favorites.size - 1)
                saveFavorites()
                Toast.makeText(this, "已收藏 ${currentFavorite.title}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "该网页已收藏", Toast.LENGTH_SHORT).show()
            }
        }

        // 新增自定义页面按钮点击事件
        binding.addCustomFavoriteButton.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_favorite, null)
            val titleEditText = dialogView.findViewById<TextInputEditText>(R.id.editTitleEditText)
            val urlEditText = dialogView.findViewById<TextInputEditText>(R.id.editUrlEditText)

            // 初始值
            titleEditText.setText("")
            urlEditText.setText("https://")

            AlertDialog.Builder(this)
                .setTitle("新增自定义页面")
                .setView(dialogView)
                .setPositiveButton("添加") { _, _ ->
                    val title = titleEditText.text.toString()
                    var url = urlEditText.text.toString()
                    if (title.isNotEmpty() && url.isNotEmpty() && url != "https://") {
                        // 确保 URL 以 http:// 或 https:// 开头
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://$url"
                        }
                        val newFavorite = Favorite(title, url)
                        if (favorites.none { it.url == newFavorite.url }) {
                            favorites.add(newFavorite)
                            adapter.notifyItemInserted(favorites.size - 1)
                            saveFavorites()
                            Toast.makeText(this, "已添加 $title", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "该网页已收藏", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "请输入有效的标题和 URL", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // “？”按钮点击事件
        binding.helpButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("使用说明")
                .setMessage("点击标题编辑收藏，点击网址跳转，长按删除")
                .setPositiveButton("知道了") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun loadFavorites() {
        val gson = Gson()
        val json = sharedPreferences.getString("favorites", null)
        val type = object : TypeToken<MutableList<Favorite>>() {}.type
        favorites = if (json != null) {
            gson.fromJson(json, type)
        } else {
            mutableListOf()
        }
    }

    private fun saveFavorites() {
        val gson = Gson()
        val json = gson.toJson(favorites)
        sharedPreferences.edit().putString("favorites", json).apply()
    }
}