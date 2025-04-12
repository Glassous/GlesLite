package com.glassous.gleslite

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity
import com.glassous.gleslite.databinding.ActivityMainBinding
import java.lang.ref.WeakReference

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        (application as App).mainActivity = WeakReference(this)
        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
        historyManager = HistoryManager(this)

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
        }
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
            binding?.settingsButton?.visibility = View.VISIBLE
            if (animate) {
                listOfNotNull(
                    binding?.forwardButton,
                    binding?.backButton,
                    binding?.refreshButton,
                    binding?.dataManagementButton,
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
        (application as App).mainActivity = null
    }

    private fun cancelAllAnimations() {
        binding?.urlBar?.animate()?.cancel()
        binding?.fullscreenContainer?.animate()?.cancel()
        listOfNotNull(
            binding?.forwardButton,
            binding?.backButton,
            binding?.refreshButton,
            binding?.dataManagementButton,
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