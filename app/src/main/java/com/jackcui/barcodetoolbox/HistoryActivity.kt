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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bd = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(bd.root)

        val itt = intent
        val history = itt.getSerializableExtra("history_map") as? History ?: run {
            showErrorToast("数据获取失败")
            finish()
            return
        }
        val key = itt.getStringExtra("key") ?: run {
            showErrorToast("数据获取失败")
            finish()
            return
        }
        val listIndex = itt.getIntExtra("list_index", -1)

        val historyList = history[key] ?: run {
            showErrorToast("未找到对应记录")
            finish()
            return
        }

        if (listIndex < 0 || listIndex >= historyList.size) {
            showErrorToast("记录索引无效")
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
            MaterialAlertDialogBuilder(this).setTitle("删除记录")
                .setMessage("日期：$key\n索引：${listIndex}\n确认要删除吗？此操作不可撤销！")
                .setPositiveButton("确认") { _, _ ->
                    val file = File(applicationContext.filesDir, "history.json")
                    if (!file.exists()) {
                        showErrorToast("删除指定历史记录时出错，未找到数据文件")
                        return@setPositiveButton
                    }
                    if (historyList.size > 1) {
                        historyList.removeAt(listIndex)
                        history[key] = historyList
                    } else {
                        history.remove(key)
                    }
                    file.writeText(JSON.toJSONString(history))
                    showInfoToast("日期：$key\n索引：${listIndex}\n记录删除成功")
                    finish()
                }.show()
        }
    }
}