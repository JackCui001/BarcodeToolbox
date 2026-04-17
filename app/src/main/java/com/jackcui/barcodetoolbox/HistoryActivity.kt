package com.jackcui.barcodetoolbox

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.alibaba.fastjson2.JSON
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jackcui.barcodetoolbox.MainActivity.Companion.showErrorToast
import com.jackcui.barcodetoolbox.MainActivity.Companion.showInfoToast
import com.jackcui.barcodetoolbox.databinding.ActivityHistoryBinding
import java.io.File

class HistoryActivity : AppCompatActivity() {

    private lateinit var bd: ActivityHistoryBinding

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bd = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(bd.root)

        val itt = intent
        val history = itt.getSerializableExtra("history_map") as? History ?: run {
            showErrorToast(getString(R.string.toast_error_data_failed))
            finish()
            return
        }
        val key = itt.getStringExtra("key") ?: run {
            showErrorToast(getString(R.string.toast_error_data_failed))
            finish()
            return
        }
        val listIndex = itt.getIntExtra("list_index", -1)

        val historyList = history[key] ?: run {
            showErrorToast(getString(R.string.toast_error_record_not_found))
            finish()
            return
        }

        if (listIndex < 0 || listIndex >= historyList.size) {
            showErrorToast(getString(R.string.toast_error_invalid_index))
            finish()
            return
        }

        bd.tvOutput.text = historyList[listIndex]
        bd.efabRemoveRecord.shrink()

        bd.efabRemoveRecord.setOnLongClickListener {
            if (bd.efabRemoveRecord.isExtended) {
                bd.efabRemoveRecord.shrink()
            } else {
                bd.efabRemoveRecord.extend()
            }
            true
        }

        bd.efabRemoveRecord.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(getString(R.string.dialog_title_delete_confirm))
                .setMessage(getString(R.string.msg_delete_confirm, key, listIndex))
                .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                    val file = File(applicationContext.filesDir, "history.json")
                    if (!file.exists()) {
                        showErrorToast(getString(R.string.toast_error_data_failed)) // Re-using data failed as it's general
                        return@setPositiveButton
                    }
                    if (historyList.size > 1) {
                        historyList.removeAt(listIndex)
                        history[key] = historyList
                    } else {
                        history.remove(key)
                    }
                    file.writeText(JSON.toJSONString(history))
                    showInfoToast(getString(R.string.msg_delete_success, key, listIndex))
                    finish()
                }.show()
        }
    }
}