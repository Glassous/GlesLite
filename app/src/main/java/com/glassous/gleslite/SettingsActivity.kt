package com.glassous.gleslite

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.glassous.gleslite.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    private val favoritesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedUrl = result.data?.getStringExtra("selected_url")
            selectedUrl?.let {
                // 将选中的 URL 返回给 MainActivity
                val intent = Intent().apply {
                    putExtra("selected_url", it)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)

        // 加载已保存的默认 URL
        val defaultUrl = sharedPreferences.getString("default_url", "")
        if (defaultUrl.isNullOrEmpty()) {
            binding.defaultUrlEditText.setText("https://")
        } else {
            binding.defaultUrlEditText.setText(defaultUrl)
        }

        // 返回键点击事件
        binding.backButton.setOnClickListener {
            finish() // 关闭当前 Activity，返回到 MainActivity
        }

        // 保存按钮点击事件
        binding.saveButton.setOnClickListener {
            var url = binding.defaultUrlEditText.text.toString().trim()
            if (url.isNotEmpty() && url != "https://") {
                // 如果 URL 不以 http:// 或 https:// 开头，自动添加 https://
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                // 保存 URL 到 SharedPreferences
                sharedPreferences.edit().putString("default_url", url).apply()
                // 更新 EditText 显示
                binding.defaultUrlEditText.setText(url)
                Toast.makeText(this, "默认启动页面已保存", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请输入有效的 URL", Toast.LENGTH_SHORT).show()
            }
        }

        // 收藏按钮点击事件
        binding.favoritesButton.setOnClickListener {
            val currentUrl = sharedPreferences.getString("default_url", "https://www.bing.com") ?: "https://www.bing.com"
            val favorite = Favorite("Default Page", currentUrl)
            val intent = Intent(this, FavoritesActivity::class.java).apply {
                putExtra("current_favorite", favorite)
            }
            favoritesLauncher.launch(intent)
        }
    }
}