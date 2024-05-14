package com.jackcui.barcodetoolbox

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Intents.Insert
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
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.hjq.toast.ToastParams
import com.hjq.toast.Toaster
import com.hjq.toast.style.CustomToastStyle
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScan.AddressInfo
import com.huawei.hms.ml.scan.HmsScan.EmailContent
import com.huawei.hms.ml.scan.HmsScan.TelPhoneNumber
import com.huawei.hms.ml.scan.HmsScan.WiFiConnectionInfo
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions
import com.huawei.hms.ml.scan.HmsScanFrame
import com.huawei.hms.ml.scan.HmsScanFrameOptions
import com.jackcui.barcodetoolbox.WifiUtils.Companion.ssid
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
        XXPermissions.with(this).permission(Permission.CAMERA, Permission.READ_MEDIA_IMAGES)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (!allGranted) {
                        return
                    }
                    if (hwScan) {
                        val opt =
                            HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
                                .setParseResult(bParse).create()
                        ScanUtil.startScan(this@MainActivity, HW_SCAN_REQ_CODE, opt)
                    } else {
                        val file =
                            File.createTempFile("tmp", ".jpg", this@MainActivity.externalCacheDir)
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity, "com.jackcui.barcodetoolbox.fileprovider", file
                        )
                        saveImageUri = uri
                        takePhotoLauncher.launch(uri)
                    }
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    if (doNotAskAgain) {
                        showErrorToast("权限请求被永久拒绝，请在系统设置中手动授权\n本应用仅申请必要权限，请放心授权")
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(this@MainActivity, permissions)
                    } else {
                        showErrorToast("权限请求被拒绝，请允许授予权限以正常使用此应用\n本应用仅申请必要权限，请放心授权")
                    }
                }
            })
    }

    /**
     * 重置配置 fontScale：保持字体比例不变，始终为 1.
     */

//    override fun attachBaseContext(newBase: Context) {
//        super.attachBaseContext(newBase)
//        overrideFontScale(newBase)
//    }

//    private fun overrideFontScale(context: Context?) {
//        if (context == null) {
//            return
//        }
//        val configuration = context.resources.configuration
//        configuration.fontScale = 1f
//        applyOverrideConfiguration(configuration)
//    }

    private fun concatCodeInfo(res: HmsScan): String {
        val scanType = res.getScanType()
        val scanTypeForm = res.getScanTypeForm()
        val newText = StringBuilder()
        when (scanType) {
            HmsScan.QRCODE_SCAN_TYPE -> {
                newText.append("QR 码 - ")
            }

            HmsScan.AZTEC_SCAN_TYPE -> {
                newText.append("AZTEC 码 - ")
            }

            HmsScan.DATAMATRIX_SCAN_TYPE -> {
                newText.append("Data Matrix 码 - ")
            }

            HmsScan.PDF417_SCAN_TYPE -> {
                newText.append("PDF417 码 - ")
            }

            HmsScan.CODE93_SCAN_TYPE -> {
                newText.append("Code93 码 - ")
            }

            HmsScan.CODE39_SCAN_TYPE -> {
                newText.append("Code39 码 - ")
            }

            HmsScan.CODE128_SCAN_TYPE -> {
                newText.append("Code128 码 - ")
            }

            HmsScan.EAN13_SCAN_TYPE -> {
                newText.append("EAN13 码 - ")
            }

            HmsScan.EAN8_SCAN_TYPE -> {
                newText.append("EAN8 码 - ")
            }

            HmsScan.ITF14_SCAN_TYPE -> {
                newText.append("ITF14 码 - ")
            }

            HmsScan.UPCCODE_A_SCAN_TYPE -> {
                newText.append("UPC_A 码 - ")
            }

            HmsScan.UPCCODE_E_SCAN_TYPE -> {
                newText.append("UPC_E 码 - ")
            }

            HmsScan.CODABAR_SCAN_TYPE -> {
                newText.append("Codabar 码 - ")
            }

            HmsScan.WX_SCAN_TYPE -> {
                newText.append("微信码")
            }

            HmsScan.MULTI_FUNCTIONAL_SCAN_TYPE -> {
                newText.append("多功能码")
            }
        }
        when (scanTypeForm) {
            HmsScan.ARTICLE_NUMBER_FORM -> {
                newText.appendLine("产品信息：")
                newText.appendLine(res.getOriginalValue())
            }

            HmsScan.CONTACT_DETAIL_FORM -> {
                newText.appendLine("联系人：")
                val tmp = res.getContactDetail()
                val peopleName = tmp.getPeopleName()
                val tels = tmp.getTelPhoneNumbers()
                val emailContentList = tmp.emailContents
                val contactLinks = tmp.getContactLinks()
                val company = tmp.getCompany()
                val title = tmp.getTitle()
                val addrInfoList = tmp.getAddressesInfos()
                val note = tmp.getNote()
                val data = arrayListOf<ContentValues>()   // 用于插入联系人
                peopleName?.run {
                    newText.append("姓名： ")
                    newText.appendLine(getFullName())
                }
                if (!tels.isNullOrEmpty()) {
                    newText.appendLine("电话：")
                    for (tel in tels) {
                        val row = ContentValues().apply {
                            put(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        }
                        when (tel.getUseType()) {
                            TelPhoneNumber.CELLPHONE_NUMBER_USE_TYPE -> {
                                newText.append("  手机： ")
                                row.put(Phone.TYPE, Phone.TYPE_MOBILE)
                            }

                            TelPhoneNumber.RESIDENTIAL_USE_TYPE -> {
                                newText.append("  住家： ")
                                row.put(Phone.TYPE, Phone.TYPE_HOME)
                            }

                            TelPhoneNumber.OFFICE_USE_TYPE -> {
                                newText.append("  办公： ")
                                row.put(Phone.TYPE, Phone.TYPE_WORK)
                            }

                            TelPhoneNumber.FAX_USE_TYPE -> {
                                newText.append("  传真： ")
                                row.put(Phone.TYPE, Phone.TYPE_FAX_WORK)
                            }

                            TelPhoneNumber.OTHER_USE_TYPE -> {
                                newText.append("  其他： ")
                                row.put(Phone.TYPE, Phone.TYPE_OTHER)
                            }
                        }
                        val phoneNum = tel.getTelPhoneNumber()
                        newText.appendLine(phoneNum)

                        row.put(Phone.NUMBER, phoneNum)
                        data.add(row)
                    }
                }
                if (!emailContentList.isNullOrEmpty()) {
                    newText.appendLine("邮箱： ")
                    for (email in emailContentList) {
                        val row = ContentValues().apply {
                            put(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                        }
                        when (email.getAddressType()) {
                            EmailContent.RESIDENTIAL_USE_TYPE -> {
                                newText.append("  住家： ")
                                row.put(Email.TYPE, Email.TYPE_HOME)
                            }

                            EmailContent.OFFICE_USE_TYPE -> {
                                newText.append("  办公： ")
                                row.put(Email.TYPE, Email.TYPE_WORK)
                            }

                            EmailContent.OTHER_USE_TYPE -> {
                                newText.append("  其他： ")
                                row.put(Email.TYPE, Email.TYPE_OTHER)
                            }
                        }
                        val emailAddr = email.getAddressInfo()
                        newText.appendLine(emailAddr)

                        row.put(Email.ADDRESS, emailAddr)
                        data.add(row)
                    }
                }
                if (!contactLinks.isNullOrEmpty()) {
                    newText.append("URL： ")
                    newText.appendLine(contactLinks.toList().joinToString())
                }
                if (!company.isNullOrEmpty()) {
                    newText.append("公司： ")
                    newText.appendLine(company)
                }
                if (!title.isNullOrEmpty()) {
                    newText.append("职位： ")
                    newText.appendLine(title)
                }
                if (!addrInfoList.isNullOrEmpty()) {
                    newText.appendLine("地址：")
                    for (addrInfo in addrInfoList) {
                        val row = ContentValues().apply {
                            put(ContactsContract.Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                        }
                        when (addrInfo.getAddressType()) {
                            AddressInfo.RESIDENTIAL_USE_TYPE -> {
                                newText.append("  住家： ")
                                row.put(StructuredPostal.TYPE, StructuredPostal.TYPE_HOME)
                            }

                            AddressInfo.OFFICE_TYPE -> {
                                newText.append("  办公： ")
                                row.put(StructuredPostal.TYPE, StructuredPostal.TYPE_WORK)
                            }

                            AddressInfo.OTHER_USE_TYPE -> {
                                newText.append("  其他： ")
                                row.put(StructuredPostal.TYPE, StructuredPostal.TYPE_OTHER)
                            }
                        }
                        val addrStr = addrInfo.getAddressDetails().toList().joinToString()
                        newText.appendLine(addrStr)

                        row.put(StructuredPostal.FORMATTED_ADDRESS, addrStr)
                        data.add(row)
                    }
                }
                if (!note.isNullOrEmpty()) {
                    newText.append("备注： ")
                    newText.appendLine(note)
                }

                // 设置动作按钮
                bd.extendedFabAction.contentDescription = "新建联系人"
                bd.extendedFabAction.text = "新建联系人"
                bd.extendedFabAction.setIconResource(R.drawable.outline_person_add_alt_24)
                bd.extendedFabAction.setOnClickListener {
                    val itt = Intent(
                        Insert.ACTION,
                        ContactsContract.Contacts.CONTENT_URI
                    )
                    // 姓名信息
                    peopleName?.run {
                        itt.putExtra(Insert.NAME, getFullName())
                    }
                    // 公司信息
                    if (!company.isNullOrEmpty()) {
                        itt.putExtra(Insert.COMPANY, company)
                    }
                    // 职位信息
                    if (!title.isNullOrEmpty()) {
                        itt.putExtra(Insert.JOB_TITLE, title)
                    }
                    // 电话信息 + 邮箱信息 + 地址信息
                    itt.putParcelableArrayListExtra(Insert.DATA, data)
                    Insert.NOTES
                    // 备注信息
                    if (!note.isNullOrEmpty()) {
                        itt.putExtra(Insert.NOTES, note)
                    }
                    startActivity(itt)
                }
                bd.extendedFabAction.show()
            }

            HmsScan.DRIVER_INFO_FORM -> {
                newText.appendLine("驾照信息：")
                val tmp = res.getDriverInfo()
                val familyName = tmp.getFamilyName()
                val middleName = tmp.getMiddleName()
                val givenName = tmp.getGivenName()
                val sex = tmp.getSex()
                val dateOfBirth = tmp.getDateOfBirth()
                val countryOfIssue = tmp.getCountryOfIssue()
                val certType = tmp.getCertificateType()
                val certNum = tmp.getCertificateNumber()
                val dateOfIssue = tmp.getDateOfIssue()
                val dateOfExpire = tmp.getDateOfExpire()
                val province = tmp.getProvince()
                val city = tmp.getCity()
                val avenue = tmp.getAvenue()
                val zipCode = tmp.getZipCode()
                if (!familyName.isNullOrEmpty()) {
                    newText.append("姓： ")
                    newText.appendLine(familyName)
                }
                if (!middleName.isNullOrEmpty()) {
                    newText.append("中间名： ")
                    newText.appendLine(middleName)
                }
                if (!givenName.isNullOrEmpty()) {
                    newText.append("名： ")
                    newText.appendLine(givenName)
                }
                if (!sex.isNullOrEmpty()) {
                    newText.append("性别： ")
                    newText.appendLine(sex)
                }
                if (!dateOfBirth.isNullOrEmpty()) {
                    newText.append("出生日期： ")
                    newText.appendLine(dateOfBirth)
                }
                if (!countryOfIssue.isNullOrEmpty()) {
                    newText.append("驾照发放国： ")
                    newText.appendLine(countryOfIssue)
                }
                if (!certType.isNullOrEmpty()) {
                    newText.append("驾照类型： ")
                    newText.appendLine(certType)
                }
                if (!certNum.isNullOrEmpty()) {
                    newText.append("驾照号码： ")
                    newText.appendLine(certNum)
                }
                if (!dateOfIssue.isNullOrEmpty()) {
                    newText.append("发证日期： ")
                    newText.appendLine(dateOfIssue)
                }
                if (!dateOfExpire.isNullOrEmpty()) {
                    newText.append("过期日期： ")
                    newText.appendLine(dateOfExpire)
                }
                if (!province.isNullOrEmpty()) {
                    newText.append("省/州： ")
                    newText.appendLine(province)
                }
                if (!city.isNullOrEmpty()) {
                    newText.append("城市： ")
                    newText.appendLine(city)
                }
                if (!avenue.isNullOrEmpty()) {
                    newText.append("街道： ")
                    newText.appendLine(avenue)
                }
                if (!zipCode.isNullOrEmpty()) {
                    newText.append("邮政编码： ")
                    newText.appendLine(zipCode)
                }
            }

            HmsScan.EMAIL_CONTENT_FORM -> {
                newText.appendLine("Email：")
                val email = res.getEmailContent()
                val addrInfo = email.getAddressInfo()
                val subjectInfo = email.getSubjectInfo()
                val bodyInfo = email.getBodyInfo().substringBeforeLast(";;")
                if (!addrInfo.isNullOrEmpty()) {
                    newText.append("收件邮箱： ")
                    newText.appendLine(addrInfo)
                }
                if (!subjectInfo.isNullOrEmpty()) {
                    newText.append("主题： ")
                    newText.appendLine(subjectInfo)
                }
                if (bodyInfo.isNotEmpty()) {
                    newText.append("内容： ")
                    newText.appendLine(bodyInfo)
                }

                // 设置动作按钮
                bd.extendedFabAction.contentDescription = "发送邮件"
                bd.extendedFabAction.text = "发送邮件"
                bd.extendedFabAction.setIconResource(R.drawable.outline_email_24)
                bd.extendedFabAction.setOnClickListener {
                    val itt = Intent(Intent.ACTION_SENDTO)
                    itt.setData(Uri.parse("mailto:$addrInfo"))
                    itt.putExtra(Intent.EXTRA_SUBJECT, subjectInfo)
                    itt.putExtra(Intent.EXTRA_TEXT, bodyInfo)
                    try {
                        startActivity(itt)
                    } catch (e: ActivityNotFoundException) {
                        showErrorToast("未检测到邮箱应用")
                    }
                }
                bd.extendedFabAction.show()
            }

            HmsScan.EVENT_INFO_FORM -> {
                newText.appendLine("日历事件：")
                val tmp = res.getEventInfo()
                val abstractInfo = tmp.getAbstractInfo()
                val theme = tmp.getTheme()
                val beginTimeInfo = tmp.getBeginTime()
                val closeTimeInfo = tmp.getCloseTime()
                val sponsor = tmp.getSponsor()
                val placeInfo = tmp.getPlaceInfo()
                val condition = tmp.getCondition()
                if (!abstractInfo.isNullOrEmpty()) {
                    newText.append("描述： ")
                    newText.appendLine(abstractInfo)
                }
                if (!theme.isNullOrEmpty()) {
                    newText.append("摘要： ")
                    newText.appendLine(theme)
                }
                if (beginTimeInfo != null) {
                    newText.append("开始时间： ")
                    newText.appendLine(beginTimeInfo.originalValue)
                }
                if (closeTimeInfo != null) {
                    newText.append("开始时间： ")
                    newText.appendLine(closeTimeInfo.originalValue)
                }
                if (!sponsor.isNullOrEmpty()) {
                    newText.append("组织者： ")
                    newText.appendLine(sponsor)
                }
                if (!placeInfo.isNullOrEmpty()) {
                    newText.append("地点： ")
                    newText.appendLine(placeInfo)
                }
                if (!condition.isNullOrEmpty()) {
                    newText.append("状态： ")
                    newText.appendLine(condition)
                }
            }

            HmsScan.ISBN_NUMBER_FORM -> {
                newText.appendLine("ISBN 号：")
                newText.appendLine(res.getOriginalValue())
            }

            HmsScan.LOCATION_COORDINATE_FORM -> {
                newText.appendLine("坐标：")
                val tmp = res.getLocationCoordinate()
                val latitude = tmp.getLatitude()
                val longitude = tmp.getLongitude()
                newText.append("纬度： ")
                newText.appendLine(latitude)
                newText.append("经度： ")
                newText.appendLine(longitude)

                // 设置动作按钮
                bd.extendedFabAction.contentDescription = "打开地图并定位"
                bd.extendedFabAction.text = "定位"
                bd.extendedFabAction.setIconResource(R.drawable.outline_location_on_24)
                bd.extendedFabAction.setOnClickListener {
                    // 打开地图应用查看坐标位置
                    val uri = Uri.parse("geo:$latitude,$longitude")
                    val itt = Intent(Intent.ACTION_VIEW, uri)
                    // 查看本机是否存在地图应用
                    try {
                        startActivity(Intent.createChooser(itt, "选择一个地图应用以查看坐标位置"))
                    } catch (e: ActivityNotFoundException) {
                        showErrorToast("未检测到地图应用")
                    }
                }
                bd.extendedFabAction.show()
            }

            HmsScan.PURE_TEXT_FORM -> {
                newText.appendLine("文本：")
                newText.appendLine(res.getOriginalValue())
            }

            HmsScan.SMS_FORM -> {
                newText.appendLine("短信：")
                val tmp = res.getSmsContent()
                val destPhoneNumber = tmp.getDestPhoneNumber()
                val smsBody = tmp.getMsgContent()
                if (!destPhoneNumber.isNullOrEmpty()) {
                    newText.append("收信人： ")
                    newText.appendLine(destPhoneNumber)
                }
                if (!smsBody.isNullOrEmpty()) {
                    newText.append("内容： ")
                    newText.appendLine(smsBody)
                }

                // 设置动作按钮
                bd.extendedFabAction.contentDescription = "发送短信"
                bd.extendedFabAction.text = "发送短信"
                bd.extendedFabAction.setIconResource(R.drawable.outline_sms_24)
                bd.extendedFabAction.setOnClickListener {
                    val uri = Uri.parse("smsto:$destPhoneNumber")
                    val itt = Intent(Intent.ACTION_SENDTO, uri)
                    itt.putExtra("sms_body", smsBody)
                    startActivity(itt)
                }
                bd.extendedFabAction.show()
            }

            HmsScan.TEL_PHONE_NUMBER_FORM -> {
                newText.appendLine("电话号码：")
                val tmp = res.getTelPhoneNumber()
                when (tmp.getUseType()) {
                    TelPhoneNumber.CELLPHONE_NUMBER_USE_TYPE -> {
                        newText.append("手机： ")
                    }

                    TelPhoneNumber.RESIDENTIAL_USE_TYPE -> {
                        newText.append("住家： ")
                    }

                    TelPhoneNumber.OFFICE_USE_TYPE -> {
                        newText.append("办公： ")
                    }

                    TelPhoneNumber.FAX_USE_TYPE -> {
                        newText.append("传真： ")
                    }

                    TelPhoneNumber.OTHER_USE_TYPE -> {
                        newText.append("其他： ")
                    }
                }
                newText.appendLine(tmp.getTelPhoneNumber())

                // 设置动作按钮
                bd.extendedFabAction.contentDescription = "拨打电话"
                bd.extendedFabAction.text = "拨打电话"
                bd.extendedFabAction.setIconResource(R.drawable.baseline_phone_forwarded_24)
                bd.extendedFabAction.setOnClickListener {
                    val itt = Intent(Intent.ACTION_DIAL)
                    itt.setData(Uri.parse("tel:${tmp.getTelPhoneNumber()}"))
                    startActivity(itt)
                }
                bd.extendedFabAction.show()
            }

            HmsScan.URL_FORM -> {
                newText.appendLine("URL 链接：")
                val tmp = res.getLinkUrl()
                val theme = tmp.getTheme()
                val linkValue = tmp.linkValue
                if (!theme.isNullOrEmpty()) {
                    newText.append("标题： ")
                    newText.appendLine(theme)
                }
                if (!linkValue.isNullOrEmpty()) {
                    newText.append("链接： ")
                    newText.appendLine(linkValue)
                }
            }

            HmsScan.WIFI_CONNECT_INFO_FORM -> {
                newText.appendLine("Wi-Fi 信息：")
                val tmp = res.wiFiConnectionInfo
                val ssid = tmp.getSsidNumber()
                val pwd = tmp.getPassword()
                val cipherMode = tmp.getCipherMode()
                if (!ssid.isNullOrEmpty()) {
                    newText.append("接入点名称： ")
                    newText.appendLine(ssid)
                }
                if (!pwd.isNullOrEmpty()) {
                    newText.append("密码： ")
                    newText.appendLine(pwd)
                }
                newText.append("加密方式： ")
                when (cipherMode) {
                    WiFiConnectionInfo.WPA_MODE_TYPE -> {
                        newText.appendLine("WPA/WPA2")
                    }

                    WiFiConnectionInfo.WEP_MODE_TYPE -> {
                        newText.appendLine("WEP")
                    }

                    WiFiConnectionInfo.NO_PASSWORD_MODE_TYPE -> {
                        newText.appendLine("开放")
                    }

                    WiFiConnectionInfo.SAE_MODE_TYPE -> {
                        newText.appendLine("WPA3")
                    }
                }
                newText.append("隐藏： ")
                if (res.getOriginalValue().contains("H:true", ignoreCase = true)) {
                    newText.appendLine("是")
                } else {
                    newText.appendLine("否")
                }

                // 设置动作按钮
                bd.extendedFabAction.contentDescription = "连接热点"
                bd.extendedFabAction.text = "连接热点"
                bd.extendedFabAction.setIconResource(R.drawable.baseline_wifi_find_24)
                bd.extendedFabAction.setOnClickListener {
                    val wifiUtils = WifiUtils(this)
                    XXPermissions.with(this).permission(WifiUtils.PERMISSION)
                        .request(object : OnPermissionCallback {
                            override fun onGranted(
                                permissions: MutableList<String>, allGranted: Boolean
                            ) {
                                wifiUtils.connectWifi(ssid, pwd)
                            }

                            override fun onDenied(
                                permissions: MutableList<String>, doNotAskAgain: Boolean
                            ) {
                                if (doNotAskAgain) {
                                    showErrorToast("权限请求被永久拒绝，请在系统设置中手动授权\n本应用仅申请必要权限，请放心授权")
                                    // 如果是被永久拒绝就跳转到应用权限系统设置页面
                                    XXPermissions.startPermissionActivity(
                                        this@MainActivity, permissions
                                    )
                                } else {
                                    showErrorToast("位置权限请求被拒绝，此功能必须使用此权限\n本应用仅申请必要权限，请放心授权")
                                }
                            }
                        })
                }
                bd.extendedFabAction.show()
            }

            HmsScan.OTHER_FORM -> {
                newText.appendLine("未知类型信息：")
                newText.appendLine(res.getOriginalValue())
            }
        }
        return newText.toString()
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