package com.lemon.yingshi.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityAboutBinding
import com.lemon.yingshi.mobile.databinding.ItemAboutActionCardBinding
import com.lemon.yingshi.mobile.ui.UpdateDownloadDialog
import com.lemon.yingshi.mobile.ui.UpdateInstallCoordinator
import com.lemon.yingshi.mobile.ui.settings.SettingsDialogs
import com.lemon.yingshi.mobile.util.setBackNavigation
import kotlinx.coroutines.launch
import com.lemon.yingshi.mobile.util.DebugLog

@dagger.hilt.android.AndroidEntryPoint
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding
    private val viewModel: AboutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        setupActionCards()
        observeState()
        UpdateDownloadDialog.observe(
            activity = this,
            isDownloading = viewModel.isDownloading,
            downloadProgress = viewModel.downloadProgress,
            downloadFailed = viewModel.downloadFailed,
            installApk = viewModel.installApk
        )

        val versionCode = packageManager.getPackageInfo(packageName, 0).versionCode
        viewModel.checkForUpdates(versionCode)
    }

    private fun setupActionCards() {
        setupActionCard(
            binding.cardVersion,
            R.drawable.ic_about_update,
            getString(R.string.about_version_title),
            getString(R.string.about_version_checking)
        )
        setupActionCard(
            binding.cardStats,
            R.drawable.ic_about_stats,
            getString(R.string.about_stats_title),
            getString(R.string.about_playback_stats, "0秒")
        )

        binding.cardVersion.root.setOnClickListener { onVersionCardClick() }
        // 二改：长按版本号导出崩溃日志（调试用）
        binding.cardVersion.root.setOnLongClickListener {
            exportCrashLog()
            true
        }
        binding.cardStats.root.setOnClickListener { onStatsCardClick() }
        // 二改：调试日志卡
        setupActionCard(
            binding.cardDebugLog,
            R.drawable.ic_about_stats,
            "调试日志",
            "导出运行日志，协助定位问题"
        )
        binding.cardDebugLog.root.setOnClickListener { onDebugLogCardClick() }
    }

    private fun setupActionCard(
        cardBinding: ItemAboutActionCardBinding,
        iconRes: Int,
        title: String,
        subtitle: String
    ) {
        cardBinding.cardIcon.setImageResource(iconRes)
        cardBinding.cardTitle.text = title
        cardBinding.cardSubtitle.text = subtitle
    }

    private fun observeState() {
        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isChecking.collect { checking ->
                    binding.cardVersion.cardSubtitle.text = if (checking) {
                        getString(R.string.about_version_checking)
                    } else {
                        formatVersionSubtitle(currentVersion)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.hasUpdate.collect {
                    binding.cardVersion.cardBadge.isVisible = it
                    if (!viewModel.isChecking.value) {
                        binding.cardVersion.cardSubtitle.text = formatVersionSubtitle(currentVersion)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.versionInfo.collect {
                    if (!viewModel.isChecking.value) {
                        binding.cardVersion.cardSubtitle.text = formatVersionSubtitle(currentVersion)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.totalPlaybackTimeMs.collect { totalMs ->
                    binding.cardStats.cardSubtitle.text = getString(
                        R.string.about_playback_stats,
                        formatPlaybackTime(totalMs)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UpdateInstallCoordinator.retryPendingInstall(this)
    }

    override fun onDestroy() {
        UpdateDownloadDialog.dismiss()
        super.onDestroy()
    }

    private fun formatVersionSubtitle(currentVersion: String): String {
        val versionInfo = viewModel.versionInfo.value
        return if (viewModel.hasUpdate.value && versionInfo != null) {
            getString(
                R.string.about_version_with_update,
                currentVersion,
                versionInfo.versionName
            )
        } else {
            getString(R.string.about_version_stable, currentVersion)
        }
    }

    private fun onVersionCardClick() {
        if (viewModel.isDownloading.value) return
        val versionInfo = viewModel.versionInfo.value
        if (viewModel.hasUpdate.value && versionInfo != null) {
            AboutDialogs.showVersionUpdateDialog(this, versionInfo) {
                viewModel.downloadUpdate()
            }
            return
        }
        if (viewModel.isChecking.value) return
        Toast.makeText(this, R.string.about_update_latest, Toast.LENGTH_SHORT).show()
        val versionCode = packageManager.getPackageInfo(packageName, 0).versionCode
        viewModel.checkForUpdates(versionCode)
    }

    private fun onStatsCardClick() {
        SettingsDialogs.showConfirmDialog(
            context = this,
            message = getString(R.string.about_clear_stats_confirm)
        ) {
            viewModel.clearPlaybackStats()
            Toast.makeText(this, R.string.about_stats_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatPlaybackTime(totalMs: Long): String {
        val hours = totalMs / (1000 * 60 * 60)
        val minutes = (totalMs / (1000 * 60)) % 60
        val seconds = (totalMs / 1000) % 60
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分${seconds}秒"
            seconds > 0 -> "${seconds}秒"
            else -> "0秒"
        }
    }

    /** 二改：调试日志卡点击，弹出选项 */
    private fun onDebugLogCardClick() {
        val options = arrayOf("导出运行日志", "导出崩溃日志")
        android.app.AlertDialog.Builder(this)
            .setTitle("调试日志")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportLogFile(DebugLog.getDebugLogFile(), "运行日志")
                    1 -> exportLogFile(DebugLog.getLatestCrashFile(), "崩溃日志")
                }
            }
            .show()
    }

    /** 二改：导出日志文件 */
    private fun exportLogFile(logFile: java.io.File?, label: String) {
        if (logFile == null || !logFile.exists()) {
            android.widget.Toast.makeText(this, "暂无${label}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                logFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "分享${label}"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** 二改：导出崩溃日志（保留长按入口兼容） */
    private fun exportCrashLog() {
        exportLogFile(DebugLog.getLatestCrashFile(), "崩溃日志")
    }


    companion object {
        fun intent(context: Context): Intent = Intent(context, AboutActivity::class.java)
    }
}
