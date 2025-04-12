package com.glassous.gleslite

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class HistoryManager(private val context: Context) {
    private val gson = Gson()
    private val historyFile = File(context.filesDir, "history.json")

    fun saveHistory(historyItem: HistoryItem) {
        val currentHistory = getHistory().toMutableList()
        currentHistory.add(0, historyItem) // 新记录添加到顶部
        historyFile.writeText(gson.toJson(currentHistory))
    }

    fun getHistory(): List<HistoryItem> {
        if (!historyFile.exists()) return emptyList()
        val json = historyFile.readText()
        return gson.fromJson(json, object : TypeToken<List<HistoryItem>>() {}.type) ?: emptyList()
    }

    fun deleteHistoryItem(id: Int) {
        val currentHistory = getHistory().toMutableList()
        val updatedHistory = currentHistory.filter { it.id != id }
        historyFile.writeText(gson.toJson(updatedHistory))
    }

    fun clearHistory() {
        historyFile.writeText("[]")
    }

    fun exportHistory(): String {
        return historyFile.readText()
    }
}