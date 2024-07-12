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

        // 获取意图对象
        val itt = intent
        // 获取传递的值
//        val history = JSON.parseObject(itt.getStringExtra("history_map_json"), object : TypeReference<History>() {})
        val history = itt.getSerializableExtra("history_map") as History
        val key = itt.getStringExtra("key")!!
        val listIndex = itt.getIntExtra("list_index", -1)
        // 设置值
        bd.tvOutput.text = history[key]!![listIndex]

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
                .setPositiveButton(
                    "确认"
                ) { _, _ ->
                    val file = File(applicationContext.filesDir, "history.json")
                    if (!file.exists()) {
                        showErrorToast("删除指定历史记录时出错，未找到数据文件")
                        return@setPositiveButton
                    }
                    val list = history[key]!!
                    if (list.size > 1) {
                        list.removeAt(listIndex)
                        history[key] = list
                    } else {
                        history.remove(key)
                    }
                    val newJsonStr = JSON.toJSONString(history)
                    file.writeText(newJsonStr)
                    showInfoToast("日期：$key\n索引：${listIndex}\n记录删除成功")
                    finish()
                }.show()
        }
    }
}