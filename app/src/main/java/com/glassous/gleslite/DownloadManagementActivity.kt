package com.glassous.gleslite

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.glassous.gleslite.databinding.ActivityDownloadManagementBinding
import java.io.File
import java.text.DecimalFormat

class DownloadManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadManagementBinding
    private lateinit var downloadAdapter: DownloadAdapter
    private val downloadList = mutableListOf<DownloadItem>()
    private val STORAGE_PERMISSION_CODE = 100
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var downloadManager: DownloadManager
    private lateinit var sharedPreferences: SharedPreferences

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (downloadId != -1L) {
                checkDownloadStatus(downloadId)
            }
            updateDownloadList()
        }
    }

    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val downloadId = intent?.getLongExtra("download_id", -1) ?: -1
            val fileName = intent?.getStringExtra("file_name")
            val action = intent?.action

            if (downloadId == -1L || fileName == null) return

            when (action) {
                "ACTION_PAUSE" -> {
                    pauseDownload(downloadId, fileName)
                }
                "ACTION_RESUME" -> {
                    resumeDownload(downloadId, fileName)
                }
            }
        }
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateDownloadList()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)

        setupRecyclerView()

        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val actionFilter = IntentFilter().apply {
            addAction("ACTION_PAUSE")
            addAction("ACTION_RESUME")
        }
        ContextCompat.registerReceiver(
            this,
            notificationActionReceiver,
            actionFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        binding.backButton.setOnClickListener { finish() }
        // 新增：清空按钮点击事件
        binding.clearButton.setOnClickListener {
            clearAllDownloads()
        }

        checkStoragePermission()
    }

    private fun setupRecyclerView() {
        downloadAdapter = DownloadAdapter(
            downloadList,
            onItemClick = { downloadItem ->
                openFile(downloadItem)
            },
            onCancelClick = { downloadItem ->
                downloadItem.downloadId?.let { id ->
                    try {
                        downloadManager.remove(id)
                        sharedPreferences.edit()
                            .remove("download_${id}_url")
                            .remove("download_${id}_fileName")
                            .remove("download_${id}_mimeType")
                            .remove("download_${id}_paused")
                            .remove("download_${id}_bytes")
                            .remove("download_${id}_time")
                            .apply()
                        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .cancel(id.toInt())
                        Toast.makeText(this, "已取消下载 ${downloadItem.fileName}", Toast.LENGTH_SHORT).show()
                        updateDownloadList()
                    } catch (e: Exception) {
                        Toast.makeText(this, "取消下载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onPauseResumeClick = { downloadItem ->
                downloadItem.downloadId?.let { id ->
                    if (downloadItem.status == getString(R.string.download_status_running) ||
                        downloadItem.status == getString(R.string.download_status_pending)
                    ) {
                        pauseDownload(id, downloadItem.fileName)
                    } else if (downloadItem.status == getString(R.string.download_status_paused)) {
                        resumeDownload(id, downloadItem.fileName)
                    }
                }
            },
            onRetryClick = { downloadItem ->
                downloadItem.downloadId?.let { id ->
                    try {
                        val url = sharedPreferences.getString("download_${id}_url", null)
                        val fileName = sharedPreferences.getString("download_${id}_fileName", null)
                        val mimeType = sharedPreferences.getString("download_${id}_mimeType", null)
                        if (url != null && fileName != null) {
                            val request = DownloadManager.Request(Uri.parse(url)).apply {
                                setMimeType(mimeType)
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                setTitle(fileName)
                                setDescription("AppDownload:$packageName")
                            }
                            val newDownloadId = downloadManager.enqueue(request)
                            sharedPreferences.edit()
                                .putString("download_${newDownloadId}_url", url)
                                .putString("download_${newDownloadId}_fileName", fileName)
                                .putString("download_${newDownloadId}_mimeType", mimeType)
                                .remove("download_${id}_url")
                                .remove("download_${id}_fileName")
                                .remove("download_${id}_mimeType")
                                .remove("download_${id}_paused")
                                .remove("download_${id}_bytes")
                                .remove("download_${id}_time")
                                .apply()
                            Toast.makeText(this, "正在重试下载 ${downloadItem.fileName}", Toast.LENGTH_SHORT).show()
                            updateDownloadList()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "重试下载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            // 新增：删除按钮回调
            onDeleteClick = { downloadItem ->
                downloadItem.downloadId?.let { id ->
                    try {
                        // 删除任务
                        downloadManager.remove(id)
                        // 删除文件（如果存在）
                        val file = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            downloadItem.fileName
                        )
                        if (file.exists()) {
                            file.delete()
                        }
                        // 清理 SharedPreferences
                        sharedPreferences.edit()
                            .remove("download_${id}_url")
                            .remove("download_${id}_fileName")
                            .remove("download_${id}_mimeType")
                            .remove("download_${id}_paused")
                            .remove("download_${id}_bytes")
                            .remove("download_${id}_time")
                            .apply()
                        // 清理通知
                        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .cancel(id.toInt())
                        Toast.makeText(this, "已删除 ${downloadItem.fileName}", Toast.LENGTH_SHORT).show()
                        updateDownloadList()
                    } catch (e: Exception) {
                        Toast.makeText(this, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        binding.downloadRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DownloadManagementActivity)
            adapter = downloadAdapter
        }
    }

    private fun pauseDownload(downloadId: Long, fileName: String) {
        try {
            // 实际暂停下载任务
            downloadManager.remove(downloadId)
            sharedPreferences.edit()
                .putBoolean("download_${downloadId}_paused", true)
                .apply()
            Toast.makeText(this, "已暂停下载 $fileName", Toast.LENGTH_SHORT).show()
            updateDownloadList()
        } catch (e: Exception) {
            Toast.makeText(this, "暂停下载失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeDownload(downloadId: Long, fileName: String) {
        try {
            val url = sharedPreferences.getString("download_${downloadId}_url", null)
            val mimeType = sharedPreferences.getString("download_${downloadId}_mimeType", null)
            if (url != null && fileName != null) {
                Log.d("DownloadManagement", "Resuming download: URL=$url, FileName=$fileName")
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setTitle(fileName)
                    setDescription("AppDownload:$packageName")
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
                val newDownloadId = downloadManager.enqueue(request)
                Log.d("DownloadManagement", "New Download ID: $newDownloadId (Old ID: $downloadId)")

                sharedPreferences.edit()
                    .putString("download_${newDownloadId}_url", url)
                    .putString("download_${newDownloadId}_fileName", fileName)
                    .putString("download_${newDownloadId}_mimeType", mimeType)
                    .putBoolean("download_${newDownloadId}_paused", false)
                    .remove("download_${downloadId}_url")
                    .remove("download_${downloadId}_fileName")
                    .remove("download_${downloadId}_mimeType")
                    .remove("download_${downloadId}_paused")
                    .remove("download_${downloadId}_bytes")
                    .remove("download_${downloadId}_time")
                    .apply()

                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(downloadId.toInt())

                showDownloadNotification(newDownloadId, fileName, 0)

                Toast.makeText(this, "已继续下载 $fileName", Toast.LENGTH_SHORT).show()
                updateDownloadList()
            } else {
                Toast.makeText(this, "无法继续下载：缺少必要信息", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("DownloadManagement", "Resume download failed: ${e.message}")
            Toast.makeText(this, "继续下载失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 新增：清空所有下载任务
    private fun clearAllDownloads() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val editor = sharedPreferences.edit()
            downloadList.forEach { item ->
                item.downloadId?.let { id ->
                    // 移除下载任务
                    downloadManager.remove(id)
                    // 删除文件
                    val file = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        item.fileName
                    )
                    if (file.exists()) {
                        file.delete()
                    }
                    // 清理通知
                    notificationManager.cancel(id.toInt())
                    // 清理 SharedPreferences
                    editor.remove("download_${id}_url")
                    editor.remove("download_${id}_fileName")
                    editor.remove("download_${id}_mimeType")
                    editor.remove("download_${id}_paused")
                    editor.remove("download_${id}_bytes")
                    editor.remove("download_${id}_time")
                }
            }
            editor.apply()
            downloadList.clear()
            downloadAdapter.notifyDataSetChanged()
            Toast.makeText(this, "已清空所有下载任务", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "清空失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        } else {
            updateDownloadList()
            handler.post(updateProgressRunnable)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            updateDownloadList()
            handler.post(updateProgressRunnable)
        } else {
            Toast.makeText(this, "存储权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDownloadList() {
        downloadList.clear()
        val query = DownloadManager.Query()
        val cursor: Cursor? = downloadManager.query(query)
        val activeDownloadIds = mutableSetOf<Long>()

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val description = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION))
                if (description != "AppDownload:$packageName") continue

                activeDownloadIds.add(id)

                val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "未知文件"
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val speed = calculateDownloadSpeed(id, bytesDownloaded)
                val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                val filePath = localUri?.let { uri -> Uri.parse(uri).path } ?: ""

                val progress = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt() else 0
                var statusText = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> getString(R.string.download_status_completed)
                    DownloadManager.STATUS_FAILED -> getString(R.string.download_status_failed)
                    DownloadManager.STATUS_PAUSED -> getString(R.string.download_status_paused)
                    DownloadManager.STATUS_PENDING -> getString(R.string.download_status_pending)
                    DownloadManager.STATUS_RUNNING -> getString(R.string.download_status_running)
                    else -> "未知状态"
                }

                if (sharedPreferences.getBoolean("download_${id}_paused", false) &&
                    status != DownloadManager.STATUS_SUCCESSFUL
                ) {
                    statusText = getString(R.string.download_status_paused)
                }

                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                    updateDownloadNotification(id, title, progress, speed, statusText)
                } else if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.toInt())
                }

                downloadList.add(
                    DownloadItem(
                        fileName = title,
                        filePath = filePath,
                        timestamp = System.currentTimeMillis(),
                        downloadId = id,
                        progress = progress,
                        speed = speed,
                        status = statusText
                    )
                )
            }
        }

        sharedPreferences.all.forEach { (key, _) ->
            if (key.startsWith("download_") && key.endsWith("_url")) {
                val id = key.substringAfter("download_").substringBefore("_url").toLongOrNull()
                if (id != null && !activeDownloadIds.contains(id) && sharedPreferences.getBoolean("download_${id}_paused", false)) {
                    val fileName = sharedPreferences.getString("download_${id}_fileName", "未知文件") ?: "未知文件"
                    downloadList.add(
                        DownloadItem(
                            fileName = fileName,
                            filePath = "",
                            timestamp = System.currentTimeMillis(),
                            downloadId = id,
                            progress = 0,
                            speed = "0 KB/s",
                            status = getString(R.string.download_status_paused)
                        )
                    )
                }
            }
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.listFiles()?.forEach { file ->
            val downloadId = sharedPreferences.all.keys
                .filter { it.startsWith("download_") && it.endsWith("_fileName") }
                .find { sharedPreferences.getString(it, null) == file.name }
                ?.substringAfter("download_")?.substringBefore("_fileName")?.toLongOrNull()
            if (downloadId != null && !downloadList.any { it.downloadId == downloadId }) {
                downloadList.add(
                    DownloadItem(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        timestamp = file.lastModified(),
                        downloadId = downloadId,
                        progress = 100,
                        speed = "0 KB/s",
                        status = getString(R.string.download_status_completed)
                    )
                )
            }
        }

        downloadAdapter.notifyDataSetChanged()
    }

    private fun calculateDownloadSpeed(downloadId: Long, bytesDownloaded: Long): String {
        val key = "download_$downloadId"
        val lastBytes = sharedPreferences.getLong("${key}_bytes", 0L)
        val lastTime = sharedPreferences.getLong("${key}_time", 0L)
        val currentTime = System.currentTimeMillis()

        sharedPreferences.edit()
            .putLong("${key}_bytes", bytesDownloaded)
            .putLong("${key}_time", currentTime)
            .apply()

        return if (lastTime > 0 && currentTime > lastTime) {
            val bytesPerSecond = (bytesDownloaded - lastBytes) / ((currentTime - lastTime) / 1000.0)
            formatSpeed(bytesPerSecond)
        } else {
            "0 KB/s"
        }
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        val units = arrayOf("B/s", "KB/s", "MB/s")
        var speed = bytesPerSecond
        var unitIndex = 0
        while (speed >= 1024 && unitIndex < units.size - 1) {
            speed /= 1024
            unitIndex++
        }
        return "${DecimalFormat("#.##").format(speed)} ${units[unitIndex]}"
    }

    private fun updateDownloadNotification(downloadId: Long, fileName: String, progress: Int, speed: String, status: String) {
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

        val pauseIntent = Intent("ACTION_PAUSE").apply {
            putExtra("download_id", downloadId)
            putExtra("file_name", fileName)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            this,
            downloadId.toInt(),
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent("ACTION_RESUME").apply {
            putExtra("download_id", downloadId)
            putExtra("file_name", fileName)
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            this,
            downloadId.toInt(),
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, "DOWNLOAD_CHANNEL")
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(fileName)
            .setContentText("进度: $progress% | 速度: $speed | 状态: $status")
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)

        if (status == getString(R.string.download_status_running) || status == getString(R.string.download_status_pending)) {
            notificationBuilder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_pause,
                    "暂停",
                    pausePendingIntent
                )
            )
        } else if (status == getString(R.string.download_status_paused)) {
            notificationBuilder.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_resume,
                    "继续",
                    resumePendingIntent
                )
            )
        }

        notificationManager.notify(downloadId.toInt(), notificationBuilder.build())
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

        val pauseIntent = Intent("ACTION_PAUSE").apply {
            putExtra("download_id", downloadId)
            putExtra("file_name", fileName)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            this,
            downloadId.toInt(),
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "DOWNLOAD_CHANNEL")
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(fileName)
            .setContentText(getString(R.string.notification_download_progress, fileName))
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_pause,
                    "暂停",
                    pausePendingIntent
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(downloadId.toInt(), notification)
    }

    private fun checkDownloadStatus(downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor: Cursor? = downloadManager.query(query)
        cursor?.use {
            if (it.moveToFirst()) {
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    localUri?.let { uri ->
                        val filePath = Uri.parse(uri).path
                        if (filePath?.endsWith(".apk", ignoreCase = true) == true) {
                            openApkFile(File(filePath))
                        }
                    }
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(downloadId.toInt())
                }
            }
        }
    }

    private fun openFile(downloadItem: DownloadItem) {
        val file = File(downloadItem.filePath)
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        if (downloadItem.filePath.endsWith(".apk", ignoreCase = true)) {
            openApkFile(file)
        } else {
            try {
                val uri = FileProvider.getUriForFile(this, "com.glassous.gleslite.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开此文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openApkFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "com.glassous.gleslite.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法安装此APK", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadReceiver)
        unregisterReceiver(notificationActionReceiver)
        handler.removeCallbacks(updateProgressRunnable)
    }
}

data class DownloadItem(
    val fileName: String,
    val filePath: String,
    val timestamp: Long,
    val downloadId: Long? = null,
    val progress: Int = 100,
    val speed: String = "0 KB/s",
    val status: String = ""
)