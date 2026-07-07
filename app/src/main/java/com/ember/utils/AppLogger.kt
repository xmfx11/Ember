package com.ember.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 系统级日志：写文件 + 内存缓冲（供 UI 查看）
 * - 所有模块统一走 AppLogger，不直接用 Log
 * - 文件按天滚动，保留最近 7 天
 */
object AppLogger {

    private const val TAG = "Ember"
    private const val MAX_BUFFER = 2000          // 内存缓冲最多 2000 条
    private const val KEEP_DAYS = 7              // 日志保留 7 天
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024  // 单文件 5MB 上限

    private lateinit var logDir: File
    private val buffer = ConcurrentLinkedDeque<String>()
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    fun init(context: Context) {
        logDir = File(context.filesDir, "logs").apply { mkdirs() }
        // 清理过期日志
        cleanupOldLogs()
    }

    fun d(msg: String) = write("D", msg, null)
    fun i(msg: String) = write("I", msg, null)
    fun w(msg: String, t: Throwable? = null) = write("W", msg, t)
    fun e(msg: String, t: Throwable? = null) = write("E", msg, t)

    private fun write(level: String, msg: String, t: Throwable?) {
        val time = timeFmt.format(Date())
        val line = "$time $level/$TAG: $msg"
        if (t != null) line + "\n" + Log.getStackTraceString(t)

        // 内存缓冲
        buffer.addLast(line)
        while (buffer.size > MAX_BUFFER) buffer.pollFirst()

        // 同时输出到 logcat
        when (level) {
            "D" -> Log.d(TAG, msg, t)
            "I" -> Log.i(TAG, msg, t)
            "W" -> Log.w(TAG, msg, t)
            "E" -> Log.e(TAG, msg, t)
        }

        // 写文件
        try {
            val file = currentLogFile()
            file.appendText(line + "\n")
            if (t != null) file.appendText(Log.getStackTraceString(t) + "\n")
            // 文件过大则滚动
            if (file.length() > MAX_FILE_SIZE) {
                val rotated = File(logDir, "embylite_${dateFmt.format(Date())}_1.log")
                if (rotated.exists()) rotated.delete()
                file.renameTo(rotated)
            }
        } catch (_: Exception) {
            // 日志失败不能崩溃
        }
    }

    // 内存缓冲快照（UI 用）
    fun getBuffer(): List<String> = buffer.toList()

    // 全部日志（合并内存 + 文件），UI 查看
    fun getAll(): String {
        val sb = StringBuilder()
        // 先读今天的文件
        try {
            val file = currentLogFile()
            if (file.exists()) sb.append(file.readText())
        } catch (_: Exception) {}
        sb.append("\n--- 内存缓冲 ---\n")
        buffer.forEach { sb.append(it).append("\n") }
        return sb.toString()
    }

    // 清空（用户在设置页点清空）
    fun clear() {
        buffer.clear()
        try {
            logDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private fun currentLogFile(): File =
        File(logDir, "embylite_${dateFmt.format(Date())}.log")

    private fun cleanupOldLogs() {
        try {
            val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000
            logDir.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) f.delete()
            }
        } catch (_: Exception) {}
    }
}
