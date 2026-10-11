package com.lemon.yingshi.mobile.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 二改：崩溃日志收集
 * - App 启动时安装全局异常捕获，崩溃时把堆栈写入文件
 * - 关于页提供“导出日志”按钮，方便反馈 bug 时附上
 */
object CrashLogHelper {

    private const val LOG_DIR = "crash_logs"
    private const val MAX_FILES = 5

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(context.applicationContext, thread, throwable)
            } catch (_: Exception) {
            }
            // 交给系统默认处理（弹“已停止”对话框）
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun saveCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.getExternalFilesDir(null), LOG_DIR).apply { mkdirs() }
        // 只保留最近 5 个
        dir.listFiles()?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES - 1)
            ?.forEach { it.delete() }

        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$time.txt")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        file.writeText(
            buildString {
                appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("Thread: ${thread.name}")
                appendLine("Brand: ${Build.BRAND} Model: ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("App: ${context.packageName}")
                appendLine("=".repeat(40))
                append(sw.toString())
            }
        )
    }

    /** 获取最新的崩溃日志文件，没有则返回 null */
    fun latestLogFile(context: Context): File? {
        val dir = File(context.getExternalFilesDir(null), LOG_DIR)
        return dir.listFiles()
            ?.filter { it.name.startsWith("crash_") && it.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
    }
}
