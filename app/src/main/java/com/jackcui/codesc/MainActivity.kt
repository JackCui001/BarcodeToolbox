package com.jackcui.codesc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.text.set
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScan.AddressInfo
import com.huawei.hms.ml.scan.HmsScan.TelPhoneNumber
import com.huawei.hms.ml.scan.HmsScan.WiFiConnectionInfo
import com.huawei.hms.ml.scan.HmsScanFrame
import com.huawei.hms.ml.scan.HmsScanFrameOptions
import com.jackcui.codesc.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    //    private lateinit var prefix: String

    private var scanCnt = 0
    private var parse = true


    companion object {
        /**
         * Define requestCode.
         */
        const val MULTIPLE_FILE_CHOICE_REQ_CODE = 1
        const val REQUEST_SCAN_PERM_REQ_CODE = 2
        const val CAM_SCAN_REQ_CODE = 3
        const val TAG = "BarcodeScanner"
        const val WAIT_FOR_SCAN = "等待识别"
        const val WAIT_FOR_SCAN_COLOR_HEX = "#7400FF"
        const val INVOKED_BY_INTENT_VIEW = "【由外部应用打开文件调用】"
        const val INVOKED_BY_INTENT_SEND = "【由外部应用分享文件调用】"

        fun showToast(context: Context, msg: String, duration: Int) {
            GlobalScope.launch(Dispatchers.Main)
            {
                Toast.makeText(context, msg, duration).show()
            }
        }

        fun showSnackbar(view: View, msg: String, duration: Int) {
            GlobalScope.launch(Dispatchers.Main)
            {
                Snackbar.make(view, msg, duration).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init str
        binding.tvOutput.text =
            SpannableStringBuilder().appendMySpan(WAIT_FOR_SCAN, WAIT_FOR_SCAN_COLOR_HEX, null)

        // 读取设置
        readSettings()

        // Get intent, action and MIME type
        val intent = intent
        val action = intent.action
        val type = intent.type
        Log.d(TAG, intent.toString())

        if (type != null) {
            if (!type.startsWith("image/")) {
                showSnackbar(binding.fabSetting, "导入了错误的文件类型！", Snackbar.LENGTH_LONG)
            } else if (action == Intent.ACTION_SEND) {
                binding.tvOutput.text = INVOKED_BY_INTENT_SEND
                handleSendImage(intent) // Handle single image being sent
            } else if (action == Intent.ACTION_SEND_MULTIPLE) {
                binding.tvOutput.text = INVOKED_BY_INTENT_SEND
                handleSendMultipleImages(intent) // Handle multiple images being sent
            } else if (action == Intent.ACTION_VIEW) {
                binding.tvOutput.text = INVOKED_BY_INTENT_VIEW
                handleViewImage(intent) // Handle single image being viewed
            }
        }

        // 处理选项1点击事件
        binding.fabClear.setOnClickListener {
            Log.d(TAG, "tvOutput Cleared")
            showSnackbar(binding.fabSetting, "输出信息已清空", Snackbar.LENGTH_SHORT)
            binding.tvOutput.text =
                SpannableStringBuilder().appendMySpan(WAIT_FOR_SCAN, WAIT_FOR_SCAN_COLOR_HEX, null)
            scanCnt = 0
        }


        // 处理选项2点击事件
        binding.fabScanCam.setOnClickListener {
            reqPermAndScan()
        }

        // 处理选项3点击事件
        binding.fabScanPic.setOnClickListener {
            val chooseFile = Intent(Intent.ACTION_GET_CONTENT)
            chooseFile.type = "image/*" //选择图片
            chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) //设置可以多选文件
            val chooser = Intent.createChooser(chooseFile, "选择图片")
            startActivityForResult(chooser, MULTIPLE_FILE_CHOICE_REQ_CODE)
        }

        // 处理选项4点击事件
        binding.fabGenCode.setOnClickListener {
            startActivity(Intent(this, GenerateCodeActivity::class.java))
        }

        // 处理选项5点击事件
        binding.fabSetting.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
//        prefix = sharedPreferences.getString("prefix", "null").toString()
        // 读取布尔值
        parse = sharedPreferences.getBoolean("parse", true)
    }

    private fun handleViewImage(intent: Intent) {
        val imgUri = intent.data
        imgUri?.let {
            scanPic(it)
        }
    }

    private fun handleSendImage(intent: Intent) {
        val imgUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        imgUri?.let {
            scanPic(it)
        }
    }

    private fun handleSendMultipleImages(intent: Intent) {
        val imgUris = intent.getParcelableArrayListExtra<Uri?>(Intent.EXTRA_STREAM)
        imgUris?.let {
            scanPics(it)
        }
    }

    private fun scanPic(uri: Uri, multiPicIdx: Int = -1, multiPicAmt: Int = -1) {
        val img: Bitmap
        try {
            img = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            showSnackbar(binding.fabSetting, "图片读取失败！", Snackbar.LENGTH_LONG)
            return
        }
        val frame = HmsScanFrame(img)
        val option = HmsScanFrameOptions.Creator()
            .setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
            .setMultiMode(true)
            .setPhotoMode(true)
            .setParseResult(true) // 默认值应为false，华为API文档有误
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
            scanPic(uris[i], i, uris.size)
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
        if (binding.tvOutput.text.toString() != WAIT_FOR_SCAN) {
            newText.append(binding.tvOutput.text)
        }
        if (multiPicAmt == -1 || multiPicIdx == 0) {
            newText.appendMySpan("---------- 第 ${++scanCnt} 次识别 ----------\n", "#7400FF", 0.8)
        }
        if (multiPicIdx == 0) {
            newText.appendMySpan("检测到多图，数量：$multiPicAmt\n", "#1565C0", 0.8)
        }
        if (multiPicIdx != -1) {
            newText.appendMySpan("---------- 图 ${multiPicIdx + 1} ----------\n", "#1565C0", 0.8)
        }
        if (emptyRes) {
            newText.append("无结果\n")
        } else {
            if (codeAmt > 1) {
                newText.appendMySpan("检测到多码，数量：$codeAmt\n", "#F57C00", 0.8)
                for (i in 0 until codeAmt) {
                    newText.appendMySpan("---------- 码 ${i + 1} ----------\n", "#F57C00", 0.8)
                    newText.append(concatCodeInfo(results[i]))
                }
            } else {
                newText.append(concatCodeInfo(results[0]))
            }
        }
        binding.tvOutput.text = newText
    }

    private fun SpannableStringBuilder.appendMySpan(
        str: String,
        colorHex: String? = null,
        relativeFontSize: Double? = null
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
    private fun reqPermAndScan() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQUEST_SCAN_PERM_REQ_CODE)
    }

    /**
     * Call back the permission application result. If the permission application is successful, the barcode scanning view will be displayed.
     *
     * @param requestCode   Permission application code.
     * @param permissions   Permission array.
     * @param grantResults: Permission application result array.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in grantResults.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "${permissions[i]} granted: false")
                AlertDialog.Builder(this).setTitle("提示")
                    .setMessage("权限授予失败，请允许授予权限以正常使用此应用。\n本应用仅申请必要权限，请放心授权。")
                    .setNegativeButton("关闭") { _, _ ->
                    }.show()
                return
            }
        }
        // Default View Mode
        if (requestCode == REQUEST_SCAN_PERM_REQ_CODE) {
            ScanUtil.startScan(this, CAM_SCAN_REQ_CODE, null)
        }
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
        if (resultCode != RESULT_OK || data == null) {
            return
        }
        if (requestCode == CAM_SCAN_REQ_CODE) {
            val res = data.getParcelableExtra<HmsScan>(ScanUtil.RESULT)
            res?.let {
                printResults(arrayOf(it))
            }
        }
        if (requestCode == MULTIPLE_FILE_CHOICE_REQ_CODE) {
            val cd = data.clipData
            val multiFile = cd != null
            if (multiFile) {
                for (i in 0 until cd!!.itemCount) {
                    val item = cd.getItemAt(i)
                    scanPic(item.uri, i, cd.itemCount)
                }
            } else {
                data.data?.let {
                    scanPic(it)
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        overrideFontScale(newBase)
    }

    /**
     * 重置配置 fontScale：保持字体比例不变，始终为 1.
     */
    private fun overrideFontScale(context: Context?) {
        if (context == null) {
            return
        }
        val configuration = context.resources.configuration
        configuration.fontScale = 1f
        applyOverrideConfiguration(configuration)
    }

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
        if (scanTypeForm == HmsScan.ARTICLE_NUMBER_FORM) {
            newText.append("产品信息：")
            newText.append(res.getOriginalValue())
            newText.append("\n")
        } else if (scanTypeForm == HmsScan.CONTACT_DETAIL_FORM) {
            newText.append("联系人：\n")
            if (parse) {
                val tmp = res.getContactDetail()
                val peopleName = tmp.getPeopleName()
                val tels = tmp.getTelPhoneNumbers()
                val emailContentList = tmp.emailContents
                val contactLinks = tmp.getContactLinks()
                val company = tmp.getCompany()
                val title = tmp.getTitle()
                val addrInfoList = tmp.getAddressesInfos()
                val note = tmp.getNote()
                if (peopleName != null) {
                    newText.append("姓名： ")
                    newText.append(peopleName.getFullName())
                    newText.append("\n")
                }
                if (!tels.isNullOrEmpty()) {
                    newText.append("电话：\n")
                    for (tel in tels) {
                        when (tel.getUseType()) {
                            TelPhoneNumber.CELLPHONE_NUMBER_USE_TYPE -> {
                                newText.append("  手机： ")
                            }

                            TelPhoneNumber.RESIDENTIAL_USE_TYPE -> {
                                newText.append("  住家： ")
                            }

                            TelPhoneNumber.OFFICE_USE_TYPE -> {
                                newText.append("  办公： ")
                            }

                            TelPhoneNumber.FAX_USE_TYPE -> {
                                newText.append("  传真： ")
                            }

                            TelPhoneNumber.OTHER_USE_TYPE -> {
                                newText.append("  其他： ")
                            }
                        }
                        newText.append(tel.getTelPhoneNumber())
                        newText.append("\n")
                    }
                }
                if (!emailContentList.isNullOrEmpty()) {
                    newText.append("邮箱： ")
                    val emails = ArrayList<String>()
                    for (email in emailContentList) {
                        emails.add(email.getAddressInfo())
                    }
                    newText.append(emails.joinToString())
                    newText.append("\n")
                }
                if (!contactLinks.isNullOrEmpty()) {
                    newText.append("URL： ")
                    newText.append(contactLinks.toList().joinToString())
                    newText.append("\n")
                }
                if (!company.isNullOrEmpty()) {
                    newText.append("公司： ")
                    newText.append(company)
                    newText.append("\n")
                }
                if (!title.isNullOrEmpty()) {
                    newText.append("职位： ")
                    newText.append(title)
                    newText.append("\n")
                }
                if (!addrInfoList.isNullOrEmpty()) {
                    newText.append("地址：\n")
                    for (addrInfo in addrInfoList) {
                        when (addrInfo.getAddressType()) {
                            AddressInfo.RESIDENTIAL_USE_TYPE -> {
                                newText.append("  住家： ")
                            }

                            AddressInfo.OFFICE_TYPE -> {
                                newText.append("  办公： ")
                            }

                            AddressInfo.OTHER_USE_TYPE -> {
                                newText.append("  其他： ")
                            }
                        }
                        newText.append(addrInfo.getAddressDetails().toList().joinToString())
                        newText.append("\n")
                    }
                }
                if (!note.isNullOrEmpty()) {
                    newText.append("备注： ")
                    newText.append(note)
                    newText.append("\n")
                }
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.DRIVER_INFO_FORM) {
            newText.append("驾照信息：\n")
            if (parse) {
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
                    newText.append(familyName)
                    newText.append("\n")
                }
                if (!middleName.isNullOrEmpty()) {
                    newText.append("中间名： ")
                    newText.append(middleName)
                    newText.append("\n")
                }
                if (!givenName.isNullOrEmpty()) {
                    newText.append("名： ")
                    newText.append(givenName)
                    newText.append("\n")
                }
                if (!sex.isNullOrEmpty()) {
                    newText.append("性别： ")
                    newText.append(sex)
                    newText.append("\n")
                }
                if (!dateOfBirth.isNullOrEmpty()) {
                    newText.append("出生日期： ")
                    newText.append(dateOfBirth)
                    newText.append("\n")
                }
                if (!countryOfIssue.isNullOrEmpty()) {
                    newText.append("驾照发放国： ")
                    newText.append(countryOfIssue)
                    newText.append("\n")
                }
                if (!certType.isNullOrEmpty()) {
                    newText.append("驾照类型： ")
                    newText.append(certType)
                    newText.append("\n")
                }
                if (!certNum.isNullOrEmpty()) {
                    newText.append("驾照号码： ")
                    newText.append(certNum)
                    newText.append("\n")
                }
                if (!dateOfIssue.isNullOrEmpty()) {
                    newText.append("发证日期： ")
                    newText.append(dateOfIssue)
                    newText.append("\n")
                }
                if (!dateOfExpire.isNullOrEmpty()) {
                    newText.append("过期日期： ")
                    newText.append(dateOfExpire)
                    newText.append("\n")
                }
                if (!province.isNullOrEmpty()) {
                    newText.append("省/州： ")
                    newText.append(province)
                    newText.append("\n")
                }
                if (!city.isNullOrEmpty()) {
                    newText.append("城市： ")
                    newText.append(city)
                    newText.append("\n")
                }
                if (!avenue.isNullOrEmpty()) {
                    newText.append("街道： ")
                    newText.append(avenue)
                    newText.append("\n")
                }
                if (!zipCode.isNullOrEmpty()) {
                    newText.append("邮政编码： ")
                    newText.append(zipCode)
                    newText.append("\n")
                }
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.EMAIL_CONTENT_FORM) {
            newText.append("E-mail：\n")
            if (parse) {
                val email = res.getEmailContent()
                val addrInfo = email.getAddressInfo()
                val subjectInfo = email.getSubjectInfo()
                val bodyInfo = email.getBodyInfo()
                if (!addrInfo.isNullOrEmpty()) {
                    newText.append("收件邮箱： ")
                    newText.append(addrInfo)
                    newText.append("\n")
                }
                if (!subjectInfo.isNullOrEmpty()) {
                    newText.append("主题： ")
                    newText.append(subjectInfo)
                    newText.append("\n")
                }
                if (!bodyInfo.isNullOrEmpty()) {
                    newText.append("内容： ")
                    newText.append(bodyInfo.substringBeforeLast(";;"))
                    newText.append("\n")
                }
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.EVENT_INFO_FORM) {
            newText.append("日历事件：\n")
            if (parse) {
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
                    newText.append(abstractInfo)
                    newText.append("\n")
                }
                if (!theme.isNullOrEmpty()) {
                    newText.append("摘要： ")
                    newText.append(theme)
                    newText.append("\n")
                }
                if (beginTimeInfo != null) {
                    newText.append("开始时间： ")
                    newText.append(beginTimeInfo.originalValue)
                    newText.append("\n")
                }
                if (closeTimeInfo != null) {
                    newText.append("开始时间： ")
                    newText.append(closeTimeInfo.originalValue)
                    newText.append("\n")
                }
                if (!sponsor.isNullOrEmpty()) {
                    newText.append("组织者： ")
                    newText.append(sponsor)
                    newText.append("\n")
                }
                if (!placeInfo.isNullOrEmpty()) {
                    newText.append("地点： ")
                    newText.append(placeInfo)
                    newText.append("\n")
                }
                if (!condition.isNullOrEmpty()) {
                    newText.append("状态： ")
                    newText.append(condition)
                    newText.append("\n")
                }
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.ISBN_NUMBER_FORM) {
            newText.append("ISBN 号：\n")
            newText.append(res.getOriginalValue())
            newText.append("\n")
        } else if (scanTypeForm == HmsScan.LOCATION_COORDINATE_FORM) {
            newText.append("坐标：\n")
            if (parse) {
                val tmp = res.getLocationCoordinate()
                val latitude = tmp.getLatitude()
                val longitude = tmp.getLongitude()
                newText.append("经度： ")
                newText.append(longitude)
                newText.append("\n")
                newText.append("纬度： ")
                newText.append(latitude)
                newText.append("\n")
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.PURE_TEXT_FORM) {
            newText.append("文本：\n")
            newText.append(res.getOriginalValue())
            newText.append("\n")
        } else if (scanTypeForm == HmsScan.SMS_FORM) {
            newText.append("短信：\n")
            if (parse) {
                val tmp = res.getSmsContent()
                val destPhoneNumber = tmp.getDestPhoneNumber()
                val msgContent = tmp.getMsgContent()
                if (!destPhoneNumber.isNullOrEmpty()) {
                    newText.append("收信人： ")
                    newText.append(destPhoneNumber)
                    newText.append("\n")
                }
                if (!msgContent.isNullOrEmpty()) {
                    newText.append("内容： ")
                    newText.append(msgContent)
                    newText.append("\n")
                }
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.TEL_PHONE_NUMBER_FORM) {
            newText.append("电话号码：\n")
            if (parse) {
                val tmp = res.getTelPhoneNumber()
                if (tmp != null) {
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
                    newText.append(tmp.getTelPhoneNumber())
                    newText.append("\n")
                }
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.URL_FORM) {
            newText.append("URL 链接：\n")
            if (parse) {
                val tmp = res.getLinkUrl()
                val theme = tmp.getTheme()
                val linkValue = tmp.linkValue
                if (!theme.isNullOrEmpty()) {
                    newText.append("标题： ")
                    newText.append(theme)
                    newText.append("\n")
                }
                if (!linkValue.isNullOrEmpty()) {
                    newText.append("链接： ")
                    newText.append(linkValue)
                    newText.append("\n")
                }
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.WIFI_CONNECT_INFO_FORM) {
            newText.append("Wi-Fi 信息：\n")
            if (parse) {
                val tmp = res.wiFiConnectionInfo
                val ssid = tmp.getSsidNumber()
                val pwd = tmp.getPassword()
                val cipherMode = tmp.getCipherMode()
                if (!ssid.isNullOrEmpty()) {
                    newText.append("接入点名称： ")
                    newText.append(ssid)
                    newText.append("\n")
                }
                if (!pwd.isNullOrEmpty()) {
                    newText.append("密码： ")
                    newText.append(pwd)
                    newText.append("\n")
                }
                newText.append("加密方式： ")
                when (cipherMode) {
                    WiFiConnectionInfo.WPA_MODE_TYPE -> {
                        newText.append("WPA/WPA2")
                    }

                    WiFiConnectionInfo.WEP_MODE_TYPE -> {
                        newText.append("WEP")
                    }

                    WiFiConnectionInfo.NO_PASSWORD_MODE_TYPE -> {
                        newText.append("开放")
                    }

                    WiFiConnectionInfo.SAE_MODE_TYPE -> {
                        newText.append("WPA3")
                    }
                }
                newText.append("\n")
                newText.append("隐藏： ")
                if (res.getOriginalValue().contains("H: true", ignoreCase = true)) {
                    newText.append("是")
                } else {
                    newText.append("否")
                }
                newText.append("\n")
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.OTHER_FORM) {
            newText.append("未知类型信息：\n")
            newText.append(res.getOriginalValue())
            newText.append("\n")
        }
        return newText.toString()
    }
}