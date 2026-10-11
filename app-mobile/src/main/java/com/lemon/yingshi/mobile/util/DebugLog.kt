package com.lemon.yingshi.mobile.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 二改：调试日志（参考 ZY-Player DebugLog）
 *
 * 隐私红线：绝不记录播放信息（标题、播放地址、播放进度、集数、源名称等）。
 * 只记录：未捕获异常堆栈、页面包屑（翻页/加载）、网络失败。
 *
 * 文件：
 * - debug.log：环形文件，上限 256KB，超限截断保留后半
 * - crash-*.log：崩溃单独文件，只保留最近 5 个
 */
object DebugLog {

    private const val MAX_FILE_SIZE = 256 * 1024L
    private const val MAX_CRASH_FILES = 5

    @Volatile
    private var dir: File? = null
    private val dateFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileFmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val lock = Any()

    /** Application.onCreate 调一次 */
    fun init(dir: File) {
        this.dir = dir.apply { mkdirs() }
    }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)

    fun e(tag: String, msg: String, tr: Throwable? = null) = write("E", tag, msg, tr)

    /** 面包屑：关键操作轨迹，不含内容数据 */
    fun breadcrumb(msg: String) = write("B", "crumb", msg, null)

    private fun write(level: String, tag: String, msg: String, tr: Throwable?) {
        val d = dir ?: return
        synchronized(lock) {
            try {
                val f = File(d, "debug.log")
                if (f.exists() && f.length() > MAX_FILE_SIZE) {
                    val bytes = f.readBytes()
                    f.writeBytes(bytes.copyOfRange(bytes.size / 2, bytes.size))
                }
                val sb = StringBuilder()
                sb.append(dateFmt.format(Date())).append(' ')
                    .append(level).append('/').append(tag).append(": ").append(msg).append('\n')
                if (tr != null) {
                    sb.append(android.util.Log.getStackTraceString(tr)).append('\n')
                }
                f.appendText(sb.toString())
            } catch (_: Exception) {
            }
        }
    }

    fun saveCrash(thread: Thread, throwable: Throwable) {
        val d = dir ?: return
        try {
            d.listFiles()
                ?.filter { it.name.startsWith("crash-") && it.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_CRASH_FILES - 1)
                ?.forEach { it.delete() }

            val time = fileFmt.format(Date())
            val file = File(d, "crash-$time.log")
            val sb = StringBuilder()
            sb.append("Time: ").append(dateFmt.format(Date())).append('\n')
            sb.append("Thread: ").append(thread.name).append('\n')
            sb.append(android.util.Log.getStackTraceString(throwable))
            file.writeText(sb.toString())

            // 同时记到 debug.log
            e("crash", "Uncaught in ${thread.name}", throwable)
        } catch (_: Exception) {
        }
    }

    fun getDebugLogFile(): File? {
        val d = dir ?: return null
        val f = File(d, "debug.log")
        return if (f.exists()) f else null
    }

    fun getLatestCrashFile(): File? {
        val d = dir ?: return null
        return d.listFiles()
            ?.filter { it.name.startsWith("crash-") && it.name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
    }
}
