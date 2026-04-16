package com.jackcui.barcodetoolbox

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.text.set
import androidx.preference.PreferenceManager
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.TypeReference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.hjq.toast.ToastParams
import com.hjq.toast.Toaster
import com.hjq.toast.style.CustomToastStyle
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions
import com.huawei.hms.ml.scan.HmsScanFrame
import com.huawei.hms.ml.scan.HmsScanFrameOptions
import com.jackcui.barcodetoolbox.WifiHelper.Companion.ssid
import com.jackcui.barcodetoolbox.databinding.ActivityMainBinding
import com.jackcui.barcodetoolbox.databinding.DialogHistoryPickerBinding
import java.io.File
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// 定义历史记录类型：map<日期,list<内容>>
typealias History = MutableMap<String, MutableList<String>>

class MainActivity : AppCompatActivity() {
    private lateinit var bd: ActivityMainBinding
    private var multiScanPrefix: String? = null
    private var multiPicPrefix: String? = null
    private var multiCodePrefix: String? = null
    private var saveImageUri: Uri? = null
    private var scanCnt = 1
    private var bParse = true
    private var bAutoSaveResult = false

    // 定义Activity result launcher
    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                saveImageUri?.let {
                    scanPic(MediaStore.Images.Media.getBitmap(contentResolver, it))
                    if (bAutoSaveResult) {
                        saveData()
                    }
                }
            }
        }
    private val pickFilesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {
            if (it.size == 1) {
                scanPic(uri = it[0])
            } else {
                for (i in it.indices) {
                    scanPic(uri = it[i], multiPicIdx = i, multiPicAmt = it.size)
                }
            }
            if (bAutoSaveResult) {
                saveData()
            }
        }

    companion object {
        /**
         * Define requestCode.
         */
        const val HW_SCAN_REQ_CODE = 0
        const val WIFI_INTENT_REQ_CODE = 1
        const val TAG = "BarcodeToolbox"
        const val WAIT_FOR_SCAN = "等待识别"
        const val INVOKED_BY_INTENT_VIEW = "【由外部应用打开文件调用】"
        const val INVOKED_BY_INTENT_SEND = "【由外部应用分享文件调用】"
        const val SCAN_MODE_ASK = "ask"
        const val SCAN_MODE_NORMAL = "normal"
        const val SCAN_MODE_CAMERA = "camera"
        const val SCAN_MODE_FILE = "file"

        fun showSnackbar(view: View, msg: String, duration: Int) {
            Snackbar.make(view, msg, duration).show()
        }

        fun showInfoToast(text: String) {
            val params = ToastParams()
            params.text = text
            params.style = CustomToastStyle(R.layout.toast_info, Gravity.BOTTOM, 0, 64)
            Toaster.show(params)
        }

        fun showWarnToast(text: String) {
            val params = ToastParams()
            params.text = text
            params.style = CustomToastStyle(R.layout.toast_warn, Gravity.BOTTOM, 0, 64)
            Toaster.show(params)
        }

        fun showErrorToast(text: String) {
            val params = ToastParams()
            params.text = text
            params.style = CustomToastStyle(R.layout.toast_error, Gravity.BOTTOM, 0, 64)
            Toaster.show(params)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding
        bd = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bd.root)

        // 初始化 Toast 框架
        Toaster.init(this.application)

        // 读取设置
        readSettings()

        // Init str
        bd.tvOutput.text = SpannableStringBuilder().appendMySpan(WAIT_FOR_SCAN, "#7400FF", null)
        bd.extendedFabAction.hide()

        // Get intent, action and MIME type
        val intent = intent
        val action = intent.action
        val type = intent.type
        Log.d(TAG, intent.toString())

        type?.let {
            if (!it.startsWith("image/")) {
                showErrorToast("导入了错误的文件类型")
            } else if (action == Intent.ACTION_SEND) {
                bd.tvOutput.text = SpannableStringBuilder().appendMySpan(
                    "$INVOKED_BY_INTENT_SEND\n", "#FF4400", 1.1
                )
                handleSendImage(intent) // Handle single image being sent
            } else if (action == Intent.ACTION_SEND_MULTIPLE) {
                bd.tvOutput.text = SpannableStringBuilder().appendMySpan(
                    "$INVOKED_BY_INTENT_SEND\n", "#FF4400", 1.1
                )
                handleSendMultipleImages(intent) // Handle multiple images being sent
            } else if (action == Intent.ACTION_VIEW) {
                bd.tvOutput.text = SpannableStringBuilder().appendMySpan(
                    "$INVOKED_BY_INTENT_VIEW\n", "#FF4400", 1.1
                )
                handleViewImage(intent) // Handle single image being viewed
            }
        }

        bd.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.item_help -> {
                    MaterialAlertDialogBuilder(this).setTitle("帮助")
                        .setMessage(R.string.introduction).show()
                    true
                }

                R.id.item_cfg -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }

                else -> false
            }
        }

        bd.extendedFabAction.setOnLongClickListener {
            if (bd.extendedFabAction.isExtended) {
                bd.extendedFabAction.shrink()
            } else {
                bd.extendedFabAction.extend()
            }
            true
        }

        bd.fabClear.setOnClickListener {
            Log.d(TAG, "tvOutput Cleared")
            showInfoToast("输出信息已清空")
            bd.tvOutput.text = SpannableStringBuilder().appendMySpan(WAIT_FOR_SCAN, "#7400FF", null)
            scanCnt = 1
            bd.extendedFabAction.hide()
        }

        bd.fabScanCode.setOnClickListener {
            val items = arrayOf("普通扫码", "拍照扫码", "图片文件扫码")
            var choice = -1
            MaterialAlertDialogBuilder(this).setTitle("扫码方式")
                .setSingleChoiceItems(items, -1) { _, which ->
                    choice = which
                    val text = StringBuilder("${items[which]}：")
                    text.append(
                        when (which) {
                            0 -> "弹出取景框实时扫描，最主流的扫码方式，与微信、支付宝相同，只支持单码"
                            1 -> "进入相机拍照界面，按下拍照按钮后才对照片进行扫描，支持多码"
                            2 -> "进入文件管理器，选择图片文件扫描，可批量选择图片，支持多码"
                            else -> ""
                        }
                    )
                    showInfoToast(text.toString())
                }.setPositiveButton("确定") { _, _ ->
                    when (choice) {
                        0 -> reqPermAndScan(true)
                        1 -> reqPermAndScan(false)
                        2 -> pickFilesLauncher.launch("image/*")
                    }
                }.show()
        }

        bd.fabGenCode.setOnClickListener {
            startActivity(Intent(this, GenerateCodeActivity::class.java))
        }

        bd.fabHistory.setOnClickListener {
            val items = arrayOf("保存", "查询")
            MaterialAlertDialogBuilder(this).setTitle("历史记录").setItems(
                items
            ) { _, which ->
                when (which) {
                    0 -> saveData()
                    1 -> {
                        val jsonStr = loadData()
                        val history =
                            JSON.parseObject(jsonStr, object : TypeReference<History>() {})
                        if (history.isNullOrEmpty()) {
                            showErrorToast("未找到历史记录，请先进行保存")
                            return@setItems
                        }
                        val keys = mutableListOf<String>()
                        val valueIndexes = mutableListOf<MutableList<String>>()
                        history.forEach { pair ->
                            keys.add(pair.key)
                            valueIndexes.add(MutableList(pair.value.size) { it.toString() })
                        }
                        val historyPickerBinding =
                            DialogHistoryPickerBinding.inflate(layoutInflater)
                        (historyPickerBinding.actvHistoryDate as MaterialAutoCompleteTextView).setSimpleItems(
                            keys.toTypedArray()
                        )
                        var key = ""
                        var listIndex = -1
                        historyPickerBinding.actvHistoryDate.setOnItemClickListener { _, _, position, _ ->
                            (historyPickerBinding.actvHistoryIndex as MaterialAutoCompleteTextView).setSimpleItems(
                                valueIndexes[position].toTypedArray()
                            )
                            key = keys[position]
                        }
                        historyPickerBinding.actvHistoryIndex.setOnItemClickListener { _, _, position, _ ->
                            listIndex = position
                        }
                        MaterialAlertDialogBuilder(this).setView(historyPickerBinding.root)
                            .setTitle("筛选").setPositiveButton("确认") { _, _ ->
                                if (historyPickerBinding.actvHistoryDate.text.isEmpty() || historyPickerBinding.actvHistoryIndex.text.isEmpty()) {
                                    showErrorToast("选项不完整")
                                    return@setPositiveButton
                                }
                                // 创建意图对象
                                val itt = Intent(this, HistoryActivity::class.java)
                                // 设置传递键值对
//                                itt.putExtra("history_map_json", jsonStr)
                                itt.putExtra("history_map", history as Serializable)
                                itt.putExtra("key", key)
                                itt.putExtra("list_index", listIndex)
                                // 激活意图
                                startActivity(itt)
                            }.show()
                    }
                }
            }.show()
        }
    }

    override fun onResume() {
        super.onResume()
        readSettings()  // 读取设置
    }

    private fun readSettings() {
        // 获取 SharedPreferences 对象
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // 读取字符串值，如果找不到对应的键，则返回默认值
        multiScanPrefix = sharedPreferences.getString("multi_scan_prefix", null)
        multiPicPrefix = sharedPreferences.getString("multi_pic_prefix", null)
        multiCodePrefix = sharedPreferences.getString("multi_code_prefix", null)
        // 读取布尔值ccc
        bParse = sharedPreferences.getBoolean("parse", true)
        bAutoSaveResult = sharedPreferences.getBoolean("auto_save_result", false)

    }

    private fun handleViewImage(intent: Intent) {
        val imgUri = intent.data
        imgUri?.let {
            scanPic(uri = it)
            if (bAutoSaveResult) {
                saveData()
            }
        }
    }

    private fun handleSendImage(intent: Intent) {
        val imgUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        imgUri?.let {
            scanPic(uri = it)
            if (bAutoSaveResult) {
                saveData()
            }
        }
    }

    private fun handleSendMultipleImages(intent: Intent) {
        val imgUris = intent.getParcelableArrayListExtra<Uri?>(Intent.EXTRA_STREAM)
        imgUris?.let {
            scanPics(it)
            if (bAutoSaveResult) {
                saveData()
            }
        }
    }

    private fun saveData() {
        val file = File(applicationContext.filesDir, "history.json")
        val history = if (!file.exists()) {
            file.createNewFile()    // 如果不存在则新建文件
            sortedMapOf()
        } else {
            val jsonStr = file.readText()
            JSON.parseObject(jsonStr, object : TypeReference<History>() {})
        }
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
        val dateAsKey = sdf.format(Date(System.currentTimeMillis()))
        val strListAsValue = history.getOrElse(dateAsKey) { mutableListOf() }
        strListAsValue.add(bd.tvOutput.text.toString())
        history[dateAsKey] = strListAsValue
        val newJsonStr = JSON.toJSONString(history)
        file.writeText(newJsonStr)
        showInfoToast("日期：$dateAsKey\n索引：${strListAsValue.size - 1}\n保存成功")
    }

    private fun loadData(): String {
        val file = File(applicationContext.filesDir, "history.json")
        return if (file.exists()) file.readText() else ""
    }

    private fun scanPic(
        bitmap: Bitmap? = null, uri: Uri? = null, multiPicIdx: Int = -1, multiPicAmt: Int = -1
    ) {
        if (bitmap == null && uri == null) {
            return
        }
        val img = bitmap ?: try {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("图片读取失败")
            return
        }
        val frame = HmsScanFrame(img)
        val option =
            HmsScanFrameOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).setMultiMode(true)
                .setPhotoMode(true).setParseResult(bParse) // 默认值应为false，华为API文档有误
                .create()
        val results = ScanUtil.decode(this, frame, option).hmsScans
        if (results.isNullOrEmpty()) {
            printResults(results, multiPicIdx, multiPicAmt, true)
        } else {
            printResults(results, multiPicIdx, multiPicAmt)
        }
    }

    private fun scanPics(uris: List<Uri>) {
        for (i in uris.indices) {
            scanPic(uri = uris[i], multiPicIdx = i, multiPicAmt = uris.size)
        }
    }

    private fun printResults(
        results: Array<HmsScan>,
        multiPicIdx: Int = -1,
        multiPicAmt: Int = -1,
        emptyRes: Boolean = false
    ) {
        val codeAmt = results.size
        val newText = SpannableStringBuilder()
        if (bd.tvOutput.text.toString() != WAIT_FOR_SCAN) {
            newText.append(bd.tvOutput.text)
        }
        if (multiPicAmt == -1 || multiPicIdx == 0) {
            var prefix = "---------- 第 $scanCnt 次识别 ----------"
            if (!multiScanPrefix.isNullOrEmpty()) {
                val strSplit = multiScanPrefix!!.split("{n}")
                prefix = "${strSplit[0]}$scanCnt${strSplit[1]}"
            }
            newText.appendMySpan("$prefix\n", "#7400FF", 0.8)
            scanCnt++
        }
        if (multiPicIdx == 0) {
            newText.appendMySpan("检测到多图，数量：$multiPicAmt\n", "#1565C0", 0.8)
        }
        if (multiPicIdx != -1) {
            var prefix = "---------- 图 ${multiPicIdx + 1} ----------"
            if (!multiPicPrefix.isNullOrEmpty()) {
                val strSplit = multiPicPrefix!!.split("{n}")
                prefix = "${strSplit[0]}${multiPicIdx + 1}${strSplit[1]}"
            }
            newText.appendMySpan("$prefix\n", "#1565C0", 0.8)
        }
        if (emptyRes) {
            newText.appendLine("无结果")
        } else {
            if (codeAmt > 1) {
                newText.appendMySpan("检测到多码，数量：$codeAmt\n", "#F57C00", 0.8)
                for (i in 0 until codeAmt) {
                    var prefix = "---------- 码 ${i + 1} ----------"
                    if (!multiCodePrefix.isNullOrEmpty()) {
                        val strSplit = multiCodePrefix!!.split("{n}")
                        prefix = "${strSplit[0]}${multiPicIdx + 1}${strSplit[1]}"
                    }
                    newText.appendMySpan("$prefix\n", "#F57C00", 0.8)
                    newText.append(concatCodeInfo(results[i]))
                }
            } else {
                newText.append(concatCodeInfo(results[0]))
            }
        }
        bd.tvOutput.text = newText
        // 滑动界面到最下方
        bd.nsvTextview.post {
            bd.nsvTextview.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun SpannableStringBuilder.appendMySpan(
        str: String, colorHex: String? = null, relativeFontSize: Double? = null
    ): SpannableStringBuilder {
        val start = this.length
        val end = this.length + str.length
        this.append(str)
        colorHex?.let {
            this[start, end] = ForegroundColorSpan(Color.parseColor(colorHex))
        }
        relativeFontSize?.let {
            this[start, end] = RelativeSizeSpan(it.toFloat())
        }
        return this
    }

    /**
     * Apply for permissions.
     */
    private fun reqPermAndScan(hwScan: Boolean) {
        val permissions = mutableListOf<String>().apply {
            add(android.Manifest.permission.CAMERA)
            add(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    android.Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
            )
        }

        PermissionHelper.requestPermissions(
            this,
            PermissionHelper.PermissionConfig(
                permissions = permissions,
                explainReasonTitle = "扫码必须使用以下权限"
            ),
            onGranted = {
                if (hwScan) {
                    val opt = HmsScanAnalyzerOptions.Creator()
                        .setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
                        .setParseResult(bParse)
                        .create()
                    ScanUtil.startScan(this@MainActivity, HW_SCAN_REQ_CODE, opt)
                } else {
                    val file = File("${externalCacheDir}${File.separator}tmp_take_photo.png")
                    if (file.exists()) {
                        file.delete()
                    }
                    file.createNewFile()
                    file.deleteOnExit()
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "com.jackcui.barcodetoolbox.fileprovider",
                        file
                    )
                    saveImageUri = uri
                    takePhotoLauncher.launch(uri)
                }
            }
        )
    }



    private val scanResultParser = ScanResultParser { desc, text, iconRes, listener ->
        bd.extendedFabAction.contentDescription = desc
        bd.extendedFabAction.text = text
        bd.extendedFabAction.setIconResource(iconRes)
        bd.extendedFabAction.setOnClickListener(listener)
        bd.extendedFabAction.show()
    }

    private fun concatCodeInfo(res: HmsScan): String {
        return scanResultParser.parse(res)
    }

    /**
     * Event for receiving the activity result.
     *
     * @param requestCode Request code.
     * @param resultCode  Result code.
     * @param data        Result.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            // HW扫码回调
            HW_SCAN_REQ_CODE -> {
                if (resultCode != RESULT_OK || data == null) {
                    return
                }
                val res = data.getParcelableExtra<HmsScan>(ScanUtil.RESULT)
                res?.let {
                    printResults(arrayOf(it))
                }
            }
            // 使用Intent连接Wifi的回调
            WIFI_INTENT_REQ_CODE -> {
                if (resultCode != RESULT_OK) {
                    showErrorToast("$ssid - 热点添加失败")
                } else {
                    showInfoToast("$ssid - 热点添加成功")
                }
            }
        }
    }
}