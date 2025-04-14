package com.glassous.gleslite

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glassous.gleslite.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    public var binding: ActivityMainBinding? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var currentTitle: String = "未加载网页"
    private var isFullscreen = false
    private var isUrlBarVisible = true
    private var isButtonContainerExpanded = true
    private lateinit var historyManager: HistoryManager
    private var historyIdCounter = 0

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "通知权限被拒绝，无法显示下载通知", Toast.LENGTH_LONG).show()
        }
    }

    private val favoritesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedUrl = result.data?.getStringExtra("selected_url")
            selectedUrl?.let {
                binding?.webView?.loadUrl(it)
                binding?.urlEditText?.setText(it)
            }
        }
    }

    private val dataManagementLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedUrl = result.data?.getStringExtra("selected_url")
            selectedUrl?.let {
                binding?.webView?.loadUrl(it)
                binding?.urlEditText?.setText(it)
            }
        }
    }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedUrl = result.data?.getStringExtra("selected_url")
            selectedUrl?.let {
                binding?.webView?.loadUrl(it)
                binding?.urlEditText?.setText(it)
            }
        }
    }

    private val downloadManagementLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* 无需处理结果 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        (application as App).mainActivity = WeakReference(this)
        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        historyManager = HistoryManager(this)

        createNotificationChannel()
        requestNotificationPermission()

        savedInstanceState?.let {
            isFullscreen = it.getBoolean("isFullscreen", false)
            currentTitle = it.getString("currentTitle", "未加载网页") ?: "未加载网页"
            isUrlBarVisible = it.getBoolean("isUrlBarVisible", true)
            isButtonContainerExpanded = it.getBoolean("isButtonContainerExpanded", true)
            historyIdCounter = it.getInt("historyIdCounter", 0)
        } ?: run {
            isUrlBarVisible = sharedPreferences.getBoolean("isUrlBarVisible", true)
            isButtonContainerExpanded = sharedPreferences.getBoolean("isButtonContainerExpanded", true)
            historyIdCounter = sharedPreferences.getInt("historyIdCounter", 0)
        }

        setupWebView()
        setupButtons()
        loadDefaultUrl()

        binding?.urlBar?.post {
            updateUrlBarVisibility(false)
            updateButtonContainerState(false)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("DOWNLOAD_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupWebView() {
        binding?.webView?.settings?.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            setSupportZoom(true)
            setBuiltInZoomControls(true)
            setDisplayZoomControls(false)
        }

        CookieManager.getInstance().setAcceptCookie(sharedPreferences.getBoolean("save_cookies", true))

        binding?.webView?.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let {
                        binding?.urlEditText?.setText(it)
                        if (sharedPreferences.getBoolean("save_history", true)) {
                            historyIdCounter++
                            val historyItem = HistoryItem(
                                id = historyIdCounter,
                                title = currentTitle,
                                url = it,
                                timestamp = System.currentTimeMillis()
                            )
                            historyManager.saveHistory(historyItem)
                            sharedPreferences.edit().putInt("historyIdCounter", historyIdCounter).apply()
                        }
                    }
                    currentTitle = view?.title ?: "未加载网页"
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e("WebViewError", "Error code: $errorCode, Description: $description, URL: $failingUrl")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding?.progressBar?.apply {
                        visibility = View.VISIBLE
                        progress = newProgress
                        if (newProgress == 100) visibility = View.GONE
                    }
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    enterFullscreen(view, callback)
                }

                override fun onHideCustomView() {
                    exitFullscreen()
                }
            }

            setDownloadListener { url, _, _, mimeType, _ ->
                GlobalScope.launch(Dispatchers.IO) {
                    val fileSize = getFileSize(url)
                    val fileName = url.substringAfterLast("/", "unknown_file")
                    runOnUiThread {
                        showDownloadConfirmDialog(url, fileName, fileSize, mimeType)
                    }
                }
            }
        }
    }

    private fun getFileSize(url: String): String {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connect()
            val length = connection.contentLengthLong
            connection.disconnect()
            return if (length > 0) formatFileSize(length) else getString(R.string.download_size_unknown)
        } catch (e: Exception) {
            Log.e("FileSize", "Error fetching file size: ${e.message}")
            return getString(R.string.download_size_unknown)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "${DecimalFormat("#.##").format(size)} ${units[unitIndex]}"
    }

    private fun showDownloadConfirmDialog(url: String, fileName: String, fileSize: String, mimeType: String?) {
        AlertDialog.Builder(this)
            .setTitle(R.string.download_confirm_title)
            .setMessage(getString(R.string.download_confirm_message, fileName, fileSize))
            .setPositiveButton(R.string.download_confirm_yes) { _, _ ->
                startDownload(url, fileName, mimeType)
            }
            .setNegativeButton(R.string.download_confirm_no) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun startDownload(url: String, fileName: String, mimeType: String?) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setTitle(fileName)
                // 使用描述字段标记本应用的下载任务
                setDescription("AppDownload:$packageName")
            }
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            // 保存下载信息以支持暂停/恢复
            sharedPreferences.edit()
                .putString("download_${downloadId}_url", url)
                .putString("download_${downloadId}_fileName", fileName)
                .putString("download_${downloadId}_mimeType", mimeType)
                .apply()

            // 显示自定义通知
            showDownloadNotification(downloadId, fileName, 0)

            Toast.makeText(this, "开始下载 $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDownloadNotification(downloadId: Long, fileName: String, progress: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, DownloadManagementActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            downloadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "DOWNLOAD_CHANNEL")
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(fileName)
            .setContentText(getString(R.string.notification_download_progress, fileName))
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(downloadId.toInt(), notification)
    }

    private fun setupButtons() {
        binding?.goButton?.setOnClickListener {
            var url = binding?.urlEditText?.text.toString() ?: return@setOnClickListener
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            binding?.webView?.loadUrl(url)
        }

        binding?.clearButton?.setOnClickListener { binding?.urlEditText?.setText("") }
        binding?.forwardButton?.setOnClickListener { binding?.webView?.takeIf { it.canGoForward() }?.goForward() }
        binding?.backButton?.setOnClickListener { binding?.webView?.takeIf { it.canGoBack() }?.goBack() }
        binding?.refreshButton?.setOnClickListener { binding?.webView?.reload() }
        binding?.settingsButton?.setOnClickListener { settingsLauncher.launch(Intent(this, SettingsActivity::class.java)) }
        binding?.dataManagementButton?.setOnClickListener { dataManagementLauncher.launch(Intent(this, DataManagementActivity::class.java)) }
        binding?.downloadManagementButton?.setOnClickListener { downloadManagementLauncher.launch(Intent(this, DownloadManagementActivity::class.java)) }

        binding?.toggleUrlBarButton?.setOnClickListener {
            isUrlBarVisible = !isUrlBarVisible
            Log.d("UrlBarToggle", "Toggled isUrlBarVisible to: $isUrlBarVisible")
            updateUrlBarVisibility(true)
            saveUrlBarState()
        }

        binding?.toggleButtonContainer?.setOnClickListener {
            isButtonContainerExpanded = !isButtonContainerExpanded
            Log.d("ButtonContainerToggle", "Toggled isButtonContainerExpanded to: $isButtonContainerExpanded")
            updateButtonContainerState(true)
            saveButtonContainerState()
        }
    }

    private fun updateUrlBarVisibility(animate: Boolean) {
        binding?.urlBar?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val urlBarHeight = binding?.urlBar?.measuredHeight ?: 0
        Log.d("UrlBarVisibility", "isUrlBarVisible: $isUrlBarVisible, urlBarHeight: $urlBarHeight")

        if (isUrlBarVisible) {
            binding?.toggleUrlBarButton?.setImageResource(R.drawable.ic_collapse)
            binding?.toggleUrlBarButton?.contentDescription = getString(R.string.collapse_top_bar)
            binding?.urlBar?.visibility = View.VISIBLE
            if (animate && urlBarHeight > 0) {
                binding?.urlBar?.translationY = -urlBarHeight.toFloat()
                binding?.urlBar?.animate()
                    ?.translationY(0f)
                    ?.setDuration(200)
                    ?.start()
            } else {
                binding?.urlBar?.translationY = 0f
            }
        } else {
            binding?.toggleUrlBarButton?.setImageResource(R.drawable.ic_expand)
            binding?.toggleUrlBarButton?.contentDescription = getString(R.string.expand_top_bar)
            if (animate && urlBarHeight > 0) {
                binding?.urlBar?.animate()
                    ?.translationY(-urlBarHeight.toFloat())
                    ?.setDuration(200)
                    ?.withEndAction {
                        binding?.urlBar?.visibility = View.GONE
                        Log.d("UrlBarVisibility", "urlBar set to GONE after animation")
                    }
                    ?.start()
            } else {
                binding?.urlBar?.apply {
                    visibility = View.GONE
                    translationY = -urlBarHeight.toFloat()
                }
                Log.d("UrlBarVisibility", "urlBar set to GONE without animation")
            }
        }
    }

    private fun updateButtonContainerState(animate: Boolean) {
        if (isButtonContainerExpanded) {
            binding?.toggleButtonContainer?.setImageResource(R.drawable.ic_expand)
            binding?.forwardButton?.visibility = View.VISIBLE
            binding?.backButton?.visibility = View.VISIBLE
            binding?.refreshButton?.visibility = View.VISIBLE
            binding?.dataManagementButton?.visibility = View.VISIBLE
            binding?.downloadManagementButton?.visibility = View.VISIBLE
            binding?.settingsButton?.visibility = View.VISIBLE
            if (animate) {
                listOfNotNull(
                    binding?.forwardButton,
                    binding?.backButton,
                    binding?.refreshButton,
                    binding?.dataManagementButton,
                    binding?.downloadManagementButton,
                    binding?.settingsButton
                ).forEachIndexed { index, button ->
                    button.alpha = 0f
                    button.translationY = 100f
                    button.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .setStartDelay((index * 50).toLong())
                        .start()
                }
            } else {
                listOfNotNull(
                    binding?.forwardButton,
                    binding?.backButton,
                    binding?.refreshButton,
                    binding?.dataManagementButton,
                    binding?.downloadManagementButton,
                    binding?.settingsButton
                ).forEach { button ->
                    button.alpha = 1f
                    button.translationY = 0f
                }
            }
        } else {
            binding?.toggleButtonContainer?.setImageResource(R.drawable.ic_collapse)
            if (animate) {
                listOfNotNull(
                    binding?.settingsButton,
                    binding?.downloadManagementButton,
                    binding?.dataManagementButton,
                    binding?.refreshButton,
                    binding?.backButton,
                    binding?.forwardButton
                ).forEachIndexed { index, button ->
                    button.animate()
                        .alpha(0f)
                        .translationY(100f)
                        .setDuration(200)
                        .setStartDelay((index * 50).toLong())
                        .withEndAction {
                            button.visibility = View.GONE
                        }
                        .start()
                }
            } else {
                binding?.forwardButton?.visibility = View.GONE
                binding?.backButton?.visibility = View.GONE
                binding?.refreshButton?.visibility = View.GONE
                binding?.dataManagementButton?.visibility = View.GONE
                binding?.downloadManagementButton?.visibility = View.GONE
                binding?.settingsButton?.visibility = View.GONE
            }
        }
    }

    private fun saveUrlBarState() {
        sharedPreferences.edit().putBoolean("isUrlBarVisible", isUrlBarVisible).apply()
    }

    private fun saveButtonContainerState() {
        sharedPreferences.edit().putBoolean("isButtonContainerExpanded", isButtonContainerExpanded).apply()
    }

    private fun loadDefaultUrl() {
        var defaultUrl = sharedPreferences.getString("default_url", "https://www.bing.com") ?: "https://www.bing.com"
        if (!defaultUrl.startsWith("http://") && !defaultUrl.startsWith("https://")) defaultUrl = "https://$defaultUrl"
        binding?.urlEditText?.setText(defaultUrl)
        binding?.webView?.loadUrl(defaultUrl)
    }

    private fun enterFullscreen(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (customView != null) {
            exitFullscreen()
            return
        }

        customView = view
        customViewCallback = callback
        isFullscreen = true

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        binding?.fullscreenContainer?.addView(customView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        binding?.fullscreenContainer?.visibility = View.VISIBLE
        binding?.webView?.visibility = View.GONE
        binding?.buttonContainer?.visibility = View.GONE
        binding?.urlBar?.visibility = View.GONE
        binding?.toggleUrlBarButton?.visibility = View.GONE
        binding?.progressBar?.visibility = View.GONE

        binding?.fullscreenContainer?.alpha = 0f
        binding?.fullscreenContainer?.animate()?.alpha(1f)?.setDuration(200)?.start()
    }

    private fun exitFullscreen() {
        if (customView == null) return

        binding?.fullscreenContainer?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

                binding?.fullscreenContainer?.apply {
                    visibility = View.GONE
                    removeAllViews()
                }
                binding?.webView?.visibility = View.VISIBLE
                binding?.buttonContainer?.visibility = View.VISIBLE
                binding?.toggleUrlBarButton?.visibility = View.VISIBLE
                updateUrlBarVisibility(false)
                updateButtonContainerState(false)

                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                isFullscreen = false

                Toast.makeText(this, "已退出全屏，按钮已恢复", Toast.LENGTH_SHORT).show()
            }
            ?.start()
    }

    override fun onBackPressed() {
        when {
            isFullscreen -> exitFullscreen()
            binding?.webView?.canGoBack() == true -> binding?.webView?.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isFullscreen", isFullscreen)
        outState.putString("currentTitle", currentTitle)
        outState.putBoolean("isUrlBarVisible", isUrlBarVisible)
        outState.putBoolean("isButtonContainerExpanded", isButtonContainerExpanded)
        outState.putInt("historyIdCounter", historyIdCounter)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isFullscreen && customView != null) {
            binding?.fullscreenContainer?.apply {
                removeAllViews()
                addView(customView, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
        }
        binding?.urlBar?.post {
            updateUrlBarVisibility(false)
            updateButtonContainerState(false)
        }
    }

    override fun onPause() {
        super.onPause()
        binding?.webView?.onPause()
        binding?.webView?.pauseTimers()
        cancelAllAnimations()
    }

    override fun onResume() {
        super.onResume()
        binding?.webView?.onResume()
        binding?.webView?.resumeTimers()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFullscreen) exitFullscreen()

        binding?.webView?.apply {
            stopLoading()
            webChromeClient = null
            loadUrl("about:blank")
            if (!sharedPreferences.getBoolean("save_history", true)) clearHistory()
            if (!sharedPreferences.getBoolean("save_cookies", true)) CookieManager.getInstance().removeAllCookies(null)
            removeAllViews()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }

        customView = null
        customViewCallback = null
        binding = null
        favoritesLauncher.unregister()
        dataManagementLauncher.unregister()
        settingsLauncher.unregister()
        downloadManagementLauncher.unregister()
        notificationPermissionLauncher.unregister()
        (application as App).mainActivity = null
    }

    fun cancelAllAnimations() {
        binding?.urlBar?.animate()?.cancel()
        binding?.fullscreenContainer?.animate()?.cancel()
        listOfNotNull(
            binding?.forwardButton,
            binding?.backButton,
            binding?.refreshButton,
            binding?.dataManagementButton,
            binding?.downloadManagementButton,
            binding?.settingsButton
        ).forEach { it.animate()?.cancel() }
    }

    fun openFavoritesActivity() {
        val currentUrl = binding?.urlEditText?.text.toString() ?: return
        val favorite = Favorite(currentTitle, currentUrl)
        val intent = Intent(this, FavoritesActivity::class.java).apply {
            putExtra("current_favorite", favorite)
        }
        favoritesLauncher.launch(intent)
    }
}