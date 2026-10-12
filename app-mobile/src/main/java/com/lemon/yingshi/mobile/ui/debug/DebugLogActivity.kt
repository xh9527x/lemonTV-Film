package com.lemon.yingshi.mobile.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.lemon.yingshi.mobile.databinding.ActivityDebugLogBinding
import com.lemon.yingshi.mobile.util.DebugLog

/**
 * 二改：调试日志查看页（参考 ZY-Player DebugLogActivity）
 * 查看、复制、清空、导出，方便精准定位问题。
 * 隐私红线：不含任何播放信息（标题、地址、进度、集数、源名）。
 */
class DebugLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCopy.setOnClickListener { onCopy() }
        binding.btnClear.setOnClickListener { onClear() }
        binding.btnExport.setOnClickListener { onExport() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        // 崩溃记录
        binding.crashList.removeAllViews()
        val files = DebugLog.crashFiles()
        if (files.isEmpty()) {
            binding.crashList.addView(hintView("暂无崩溃记录"))
        } else {
            files.forEach { f ->
                val row = TextView(this).apply {
                    text = "⚠ ${f.name}（${f.length() / 1024}KB）"
                    textSize = 12f
                    setPadding(0, 18, 0, 18)
                    setOnClickListener { showCrashDetail(f.name) }
                }
                binding.crashList.addView(row)
                val div = View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                    setBackgroundColor(0xFF222222.toInt())
                }
                binding.crashList.addView(div)
            }
        }
        // 运行日志
        val log = DebugLog.readDebugLog()
        binding.tvLog.text = log.ifBlank { "暂无运行日志" }
    }

    private fun hintView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(0, 12, 0, 12)
        }
    }

    private fun showCrashDetail(name: String) {
        val content = DebugLog.readCrashFile(name)
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(content.ifBlank { "无内容" })
            .setPositiveButton("复制") { _, _ -> copyText(content) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun onCopy() {
        val text = binding.tvLog.text.toString()
        if (text.isBlank() || text == "暂无运行日志") {
            Toast.makeText(this, "暂无日志", Toast.LENGTH_SHORT).show()
            return
        }
        copyText(text)
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun copyText(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("debug_log", text))
        } catch (_: Exception) {
        }
    }

    private fun onClear() {
        AlertDialog.Builder(this)
            .setTitle("")
            .setMessage("清空全部调试日志（含崩溃记录）？")
            .setPositiveButton("清空") { _, _ ->
                DebugLog.clear()
                refresh()
                Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun onExport() {
        val logFile = DebugLog.getDebugLogFile()
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(this, "暂无运行日志", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                logFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "导出调试日志"))
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
