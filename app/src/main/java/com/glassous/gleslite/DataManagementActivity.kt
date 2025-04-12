package com.glassous.gleslite

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.glassous.gleslite.databinding.ActivityDataManagementBinding
import java.io.File
import java.text.DecimalFormat
import android.webkit.CookieManager

class DataManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataManagementBinding
    private lateinit var historyAdapter: HistoryAdapter
    private val historyGroups = mutableListOf<HistoryGroup>()
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var historyManager: HistoryManager

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let {
            val content = historyManager.exportHistory()
            contentResolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            Toast.makeText(this, "History exported", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        historyManager = HistoryManager(this)

        setupSwitches()
        setupButtons()
        setupRecyclerView()
        updateCacheSize()
    }

    private fun setupSwitches() {
        binding.saveCookiesSwitch.isChecked = sharedPreferences.getBoolean("save_cookies", true)
        binding.saveHistorySwitch.isChecked = sharedPreferences.getBoolean("save_history", true)

        binding.saveCookiesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("save_cookies", isChecked).apply()
            CookieManager.getInstance().setAcceptCookie(isChecked)
        }

        binding.saveHistorySwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("save_history", isChecked).apply()
        }
    }

    private fun setupButtons() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.clearCookiesButton.setOnClickListener {
            CookieManager.getInstance().removeAllCookies(null)
            Toast.makeText(this, "Cookies cleared", Toast.LENGTH_SHORT).show()
        }

        binding.clearCacheButton.setOnClickListener {
            (application as App).mainActivity?.get()?.binding?.webView?.clearCache(true)
            updateCacheSize()
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
        }

        binding.clearAllHistoryButton.setOnClickListener {
            historyManager.clearHistory()
            updateHistoryList()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }

        binding.exportHistoryButton.setOnClickListener {
            exportLauncher.launch("history_export.txt")
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(historyGroups,
            onItemClick = { historyItem ->
                // 点击历史记录，返回 MainActivity 并加载网页
                val intent = Intent().apply {
                    putExtra("selected_url", historyItem.url)
                }
                setResult(RESULT_OK, intent)
                finish()
            },
            onDeleteClick = { history ->
                historyManager.deleteHistoryItem(history.id)
                updateHistoryList()
            }
        )
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DataManagementActivity)
            adapter = historyAdapter
        }
        updateHistoryList()
    }

    private fun updateHistoryList() {
        val history = historyManager.getHistory()
        historyAdapter.updateData(history)
    }

    private fun updateCacheSize() {
        val cacheDir = cacheDir
        val externalCacheDir = externalCacheDir
        val cacheSize = (getDirSize(cacheDir) + getDirSize(externalCacheDir)) / 1024.0 / 1024.0
        val formattedSize = DecimalFormat("#.##").format(cacheSize)
        binding.cacheSizeText.text = getString(R.string.cache_size, "$formattedSize MB")
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }
}