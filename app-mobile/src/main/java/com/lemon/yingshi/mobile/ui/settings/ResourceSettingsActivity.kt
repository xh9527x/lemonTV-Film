package com.lemon.yingshi.mobile.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.LinearLayout
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityResourceSettingsBinding
import com.lemon.yingshi.mobile.databinding.ItemMacCmsServerBinding
import com.lemon.yingshi.tv.data.remote.model.MacCmsConnectionResult
import com.lemon.yingshi.tv.data.remote.model.MacCmsServerEntry
import com.lemon.yingshi.tv.ui.viewmodel.MacCmsConfigViewModel
import com.lemon.yingshi.mobile.util.setBackNavigation
import com.lemon.yingshi.mobile.util.DebugLog
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResourceSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResourceSettingsBinding
    private val viewModel: MacCmsConfigViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.breadcrumb("srvmgmt: onCreate")
        binding = ActivityResourceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setBackNavigation { finish() }
        observeState()
        binding.testButton.setOnClickListener {
            viewModel.testConnection(binding.urlInput.text?.toString().orEmpty())
        }
        binding.saveButton.setOnClickListener {
            viewModel.saveServerUrl(
                binding.urlInput.text?.toString().orEmpty(),
                binding.nameInput.text?.toString().orEmpty()
            )
        }
        // 二改：导入/导出
        binding.exportButton.setOnClickListener {
            DebugLog.breadcrumb("srvmgmt: export tapped")
            exportServers()
        }
        binding.importButton.setOnClickListener {
            DebugLog.breadcrumb("srvmgmt: import picker launched")
            importPicker.launch("application/json")
        }
    }

    /** 二改：文件选择器（导入 JSON） */
    private val importPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            DebugLog.breadcrumb("srvmgmt: import picker cancelled")
            return@registerForActivityResult
        }
        DebugLog.breadcrumb("srvmgmt: import file picked")
        lifecycleScope.launch {
            try {
                val json = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText().orEmpty()
                val count = viewModel.importServersJson(json)
                DebugLog.breadcrumb("srvmgmt: import result count=$count")
                Toast.makeText(
                    this@ResourceSettingsActivity,
                    if (count >= 0) getString(R.string.settings_import_success, count)
                    else getString(R.string.settings_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                DebugLog.breadcrumb("srvmgmt: import failed (exception)")
                Toast.makeText(
                    this@ResourceSettingsActivity,
                    R.string.settings_import_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** 二改：导出服务器列表为 JSON 文件（先写文件再调系统分享） */
    private fun exportServers() {
        lifecycleScope.launch {
            try {
                val json = viewModel.exportServersJson()
                DebugLog.breadcrumb("srvmgmt: export json ready len=${json.length}")
                // 先写文件：时间戳命名，避免覆盖
                val timeStamp =
                    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val fileName = "lemonfilm-servers-$timeStamp.json"
                val file = java.io.File(getExternalFilesDir(null), fileName)
                file.writeText(json)
                DebugLog.breadcrumb("srvmgmt: export file written")
                // 再调分享：FileProvider 授权只读
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@ResourceSettingsActivity,
                    "${packageName}.fileprovider",
                    file
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(
                    android.content.Intent.createChooser(
                        intent,
                        getString(R.string.settings_export)
                    )
                )
                DebugLog.breadcrumb("srvmgmt: export chooser shown file=$fileName")
            } catch (_: Exception) {
                DebugLog.breadcrumb("srvmgmt: export failed (exception)")
                Toast.makeText(
                    this@ResourceSettingsActivity,
                    R.string.settings_export_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** 二改：编辑服务器对话框 */
    private fun showEditServerDialog(entry: MacCmsServerEntry) {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val urlLayout = TextInputLayout(this).apply {
            hint = getString(R.string.settings_server_url_hint)
        }
        val urlInput = TextInputEditText(this).apply { setText(entry.url) }
        urlLayout.addView(urlInput)
        val nameLayout = TextInputLayout(this).apply {
            hint = getString(R.string.settings_server_name_hint)
        }
        val nameInput = TextInputEditText(this).apply { setText(entry.name) }
        nameLayout.addView(nameInput)
        container.addView(urlLayout)
        container.addView(nameLayout)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_edit_server)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_save) { _, _ ->
                viewModel.updateServer(
                    oldUrl = entry.url,
                    newUrl = urlInput.text?.toString().orEmpty(),
                    name = nameInput.text?.toString().orEmpty()
                )
            }
            .show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.serverList,
                    viewModel.serverUrl,
                    viewModel.testingUrl
                ) { list, current, testing ->
                    Triple(list, current, testing)
                }.collect { (list, current, testing) ->
                    if (current.isNotBlank() && binding.urlInput.text?.toString().orEmpty() != current &&
                        !binding.urlInput.hasFocus()
                    ) {
                        binding.urlInput.setText(current)
                    }
                    val name = list.find { it.url == current }?.name.orEmpty()
                    if (!binding.nameInput.hasFocus() &&
                        binding.nameInput.text?.toString().orEmpty() != name
                    ) {
                        binding.nameInput.setText(name)
                    }
                    renderServerList(list, current, testing)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.lastTestStatus,
                    viewModel.testResult,
                    viewModel.isTesting
                ) { status, result, testing ->
                    Triple(status, result, testing)
                }.collect { (status, result, testing) ->
                    binding.testButton.isEnabled = !testing
                    binding.saveButton.isEnabled = !testing
                    binding.testButton.text = if (testing) {
                        getString(R.string.settings_testing)
                    } else {
                        getString(R.string.settings_test_connectivity)
                    }
                    updateStatusUi(testing, status, result)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lastTestTime.collect { time ->
                    binding.lastTestText.isVisible = time > 0L
                    if (time > 0L) {
                        binding.lastTestText.text =
                            getString(R.string.settings_last_test_format, dateFormat.format(Date(time)))
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveMessage.collect { message ->
                    binding.saveMessageText.isVisible = !message.isNullOrBlank()
                    binding.saveMessageText.text = message
                }
            }
        }
    }

    private fun updateStatusUi(
        isTesting: Boolean,
        lastTestStatus: String,
        testResult: MacCmsConnectionResult?
    ) {
        val isConnected = testResult?.success == true || lastTestStatus == "已连接"
        val dotDrawable = when {
            isTesting -> R.drawable.bg_status_dot_yellow
            isConnected -> R.drawable.bg_status_dot_green
            else -> R.drawable.bg_status_dot_gray
        }
        binding.statusDot.background = ContextCompat.getDrawable(this, dotDrawable)
        binding.statusText.text = when {
            isTesting -> getString(R.string.settings_status_testing)
            !testResult?.message.isNullOrBlank() -> testResult!!.message
            lastTestStatus.isNotBlank() -> lastTestStatus
            else -> getString(R.string.settings_status_disconnected)
        }
    }

    private fun renderServerList(
        list: List<MacCmsServerEntry>,
        currentUrl: String,
        testingUrl: String = ""
    ) {
        binding.savedServersHeader.isVisible = list.isNotEmpty()
        binding.serverListContainer.removeAllViews()
        list.forEach { entry ->
            val item = ItemMacCmsServerBinding.inflate(
                layoutInflater,
                binding.serverListContainer,
                false
            )
            val active = entry.url == currentUrl
            val title = buildString {
                append(entry.name.ifBlank { entry.url })
                if (active) append("  · ${getString(R.string.settings_server_current)}")
            }
            item.serverUrlText.text = title
            val subtitle = buildList {
                if (entry.name.isNotBlank()) add(entry.url)
                add(entry.compactSummary().ifBlank { "未测试" })
            }.joinToString(" · ")
            item.serverStatusText.text = subtitle
            item.serverStatusText.isVisible = true
            item.root.setBackgroundResource(
                if (active) R.drawable.bg_server_row_active else R.drawable.bg_settings_input
            )
            val rowTesting = testingUrl == entry.url
            item.testItemButton.isEnabled = testingUrl.isBlank()
            item.testItemButton.text = if (rowTesting) {
                getString(R.string.settings_testing)
            } else {
                getString(R.string.settings_test_list)
            }
            item.testItemButton.setOnClickListener { viewModel.testConnection(entry.url) }
            item.useButton.isVisible = !active
            item.useButton.setOnClickListener {
                binding.urlInput.setText(entry.url)
                binding.nameInput.setText(entry.name)
                viewModel.selectServer(entry.url)
            }
            // 二改：点击整行快捷切换服务器
            item.root.setOnClickListener {
                if (!active) {
                    binding.urlInput.setText(entry.url)
                    binding.nameInput.setText(entry.name)
                    viewModel.selectServer(entry.url)
                }
            }
            // 二改：编辑服务器
            item.editButton.setOnClickListener { showEditServerDialog(entry) }
            item.deleteButton.setOnClickListener {
                val label = entry.name.ifBlank { entry.url }
                SettingsDialogs.showConfirmDialog(
                    this,
                    getString(R.string.settings_delete_server_confirm, label)
                ) {
                    DebugLog.breadcrumb("srvmgmt: delete server confirmed")
                    viewModel.removeServer(entry.url)
                }
            }
            binding.serverListContainer.addView(item.root)
        }
    }
}
