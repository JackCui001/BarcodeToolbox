package com.jackcui.barcodesc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScan.AddressInfo
import com.huawei.hms.ml.scan.HmsScan.TelPhoneNumber
import com.huawei.hms.ml.scan.HmsScan.WiFiConnectionInfo
import com.huawei.hms.ml.scan.HmsScanFrame
import com.huawei.hms.ml.scan.HmsScanFrameOptions
import com.jackcui.barcodesc.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var scanCnt = 0

    companion object {
        /**
         * Define requestCode.
         */
        const val MULTIPLE_FILE_CHOICE_REQ_CODE = 1
        const val REQUEST_PERM_REQ_CODE = 2
        const val CAM_SCAN_REQ_CODE = 3
        const val TAG = "BarcodeScanner"
        const val WAIT_FOR_SCAN = "等待识别"
        const val CLEARED_WAIT_FOR_SCAN = "已清空 - 等待识别"
        const val INVOKED_BY_INTENT_VIEW = "【由外部应用打开文件调用】"
        const val INVOKED_BY_INTENT_SEND = "【由外部应用分享文件调用】"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textView.text = WAIT_FOR_SCAN

        // Get intent, action and MIME type
        val intent = intent
        val action = intent.action
        val type = intent.type
        Log.d(TAG, intent.toString())
        if (type != null) {
            if (!type.startsWith("image/")) {
                Toast.makeText(this, "选择了错误的文件类型！", Toast.LENGTH_LONG).show()
            } else if (action == Intent.ACTION_SEND) {
                binding.textView.text = INVOKED_BY_INTENT_SEND
                handleSendImage(intent) // Handle single image being sent
            } else if (action == Intent.ACTION_SEND_MULTIPLE) {
                binding.textView.text = INVOKED_BY_INTENT_SEND
                handleSendMultipleImages(intent) // Handle multiple images being sent
            } else if (action == Intent.ACTION_VIEW) {
                binding.textView.text = INVOKED_BY_INTENT_VIEW
                handleViewImage(intent) // Handle single image being viewed
            }
        }
        binding.clearTextButton.setOnClickListener {
            binding.textView.text = CLEARED_WAIT_FOR_SCAN
            Log.d(TAG, "文本栏已清空")
            scanCnt = 0
        }
        binding.selectPicButton.setOnClickListener {
            val chooseFile = Intent(Intent.ACTION_GET_CONTENT)
            chooseFile.type = "image/*" //选择图片
            chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) //设置可以多选文件
            val chooser = Intent.createChooser(chooseFile, "选择图片")
            startActivityForResult(chooser, MULTIPLE_FILE_CHOICE_REQ_CODE)
        }
        binding.scanButton.setOnClickListener {
            requestPermission()
        }
    }

    /**
     * Apply for permissions.
     */
    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES),
                REQUEST_PERM_REQ_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_PERM_REQ_CODE
            )
        }
    }

    private fun scanPic(uri: Uri) {
        val img: Bitmap
        try {
            img = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "图片读取失败！", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "未检测到条形码！", Toast.LENGTH_LONG).show()
            return
        }
        if (results.size == 1) {
            printResult(results[0])
        } else {
            printResults(results)
        }
    }

    private fun scanPics(uris: List<Uri>) {
        for (uri in uris) {
            scanPic(uri)
        }
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

    private fun concatCodeInfo(res: HmsScan): String {
        val parse = !(binding.disableParse.isChecked)
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
                    newText.append("姓名：")
                    newText.append(peopleName.getFullName())
                    newText.append("\n")
                }
                if (!tels.isNullOrEmpty()) {
                    newText.append("电话：")
                    for (tel in tels) {
                        when (tel.getUseType()) {
                            TelPhoneNumber.CELLPHONE_NUMBER_USE_TYPE -> {
                                newText.append("手机 - ")
                            }

                            TelPhoneNumber.RESIDENTIAL_USE_TYPE -> {
                                newText.append("住家 - ")
                            }

                            TelPhoneNumber.OFFICE_USE_TYPE -> {
                                newText.append("工作 - ")
                            }

                            TelPhoneNumber.FAX_USE_TYPE -> {
                                newText.append("传真 - ")
                            }

                            TelPhoneNumber.OTHER_USE_TYPE -> {
                                newText.append("其他 - ")
                            }
                        }
                        newText.append(tel.getTelPhoneNumber())
                        newText.append("\n")
                    }
                }
                if (!emailContentList.isNullOrEmpty()) {
                    newText.append("邮箱：")
                    val emails = ArrayList<String>()
                    for (email in emailContentList) {
                        emails.add(email.getAddressInfo())
                    }
                    newText.append(emails.joinToString())
                    newText.append("\n")
                }
                if (!contactLinks.isNullOrEmpty()) {
                    newText.append("URL：")
                    newText.append(contactLinks.toList().joinToString())
                    newText.append("\n")
                }
                if (!company.isNullOrEmpty()) {
                    newText.append("公司：")
                    newText.append(company)
                    newText.append("\n")
                }
                if (!title.isNullOrEmpty()) {
                    newText.append("职位：")
                    newText.append(title)
                    newText.append("\n")
                }
                if (!addrInfoList.isNullOrEmpty()) {
                    newText.append("地址：")
                    for (addrInfo in addrInfoList) {
                        when (addrInfo.getAddressType()) {
                            AddressInfo.RESIDENTIAL_USE_TYPE -> {
                                newText.append("住家 - ")
                            }

                            AddressInfo.OFFICE_TYPE -> {
                                newText.append("工作 - ")
                            }

                            AddressInfo.OTHER_USE_TYPE -> {
                                newText.append("其他 - ")
                            }
                        }
                        newText.append(addrInfo.getAddressDetails().toList().joinToString())
                        newText.append("\n")
                    }
                }
                if (!note.isNullOrEmpty()) {
                    newText.append("备注：")
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
                    newText.append("姓：")
                    newText.append(familyName)
                    newText.append("\n")
                }
                if (!middleName.isNullOrEmpty()) {
                    newText.append("中间名：")
                    newText.append(middleName)
                    newText.append("\n")
                }
                if (!givenName.isNullOrEmpty()) {
                    newText.append("名：")
                    newText.append(givenName)
                    newText.append("\n")
                }
                if (!sex.isNullOrEmpty()) {
                    newText.append("性别：")
                    newText.append(sex)
                    newText.append("\n")
                }
                if (!dateOfBirth.isNullOrEmpty()) {
                    newText.append("出生日期：")
                    newText.append(dateOfBirth)
                    newText.append("\n")
                }
                if (!countryOfIssue.isNullOrEmpty()) {
                    newText.append("驾照发放国：")
                    newText.append(countryOfIssue)
                    newText.append("\n")
                }
                if (!certType.isNullOrEmpty()) {
                    newText.append("驾照类型：")
                    newText.append(certType)
                    newText.append("\n")
                }
                if (!certNum.isNullOrEmpty()) {
                    newText.append("驾照号码：")
                    newText.append(certNum)
                    newText.append("\n")
                }
                if (!dateOfIssue.isNullOrEmpty()) {
                    newText.append("发证日期：")
                    newText.append(dateOfIssue)
                    newText.append("\n")
                }
                if (!dateOfExpire.isNullOrEmpty()) {
                    newText.append("过期日期：")
                    newText.append(dateOfExpire)
                    newText.append("\n")
                }
                if (!province.isNullOrEmpty()) {
                    newText.append("省/州：")
                    newText.append(province)
                    newText.append("\n")
                }
                if (!city.isNullOrEmpty()) {
                    newText.append("城市：")
                    newText.append(city)
                    newText.append("\n")
                }
                if (!avenue.isNullOrEmpty()) {
                    newText.append("街道：")
                    newText.append(avenue)
                    newText.append("\n")
                }
                if (!zipCode.isNullOrEmpty()) {
                    newText.append("邮政编码：")
                    newText.append(zipCode)
                    newText.append("\n")
                }
            } else {
                newText.append(res.getOriginalValue())
                newText.append("\n")
            }
        } else if (scanTypeForm == HmsScan.EMAIL_CONTENT_FORM) {
            newText.append("邮件信息：\n")
            if (parse) {
                val email = res.getEmailContent()
                val addrInfo = email.getAddressInfo()
                val subjectInfo = email.getSubjectInfo()
                val bodyInfo = email.getBodyInfo()
                if (!addrInfo.isNullOrEmpty()) {
                    newText.append("收件邮箱：")
                    newText.append(addrInfo)
                    newText.append("\n")
                }
                if (!subjectInfo.isNullOrEmpty()) {
                    newText.append("标题：")
                    newText.append(subjectInfo)
                    newText.append("\n")
                }
                if (!bodyInfo.isNullOrEmpty()) {
                    newText.append("内容：")
                    newText.append(bodyInfo)
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
                    newText.append("描述：")
                    newText.append(abstractInfo)
                    newText.append("\n")
                }
                if (!theme.isNullOrEmpty()) {
                    newText.append("摘要：")
                    newText.append(theme)
                    newText.append("\n")
                }
                if (beginTimeInfo != null) {
                    newText.append("开始时间：")
                    newText.append(beginTimeInfo.originalValue)
                    newText.append("\n")
                }
                if (closeTimeInfo != null) {
                    newText.append("开始时间：")
                    newText.append(closeTimeInfo.originalValue)
                    newText.append("\n")
                }
                if (!sponsor.isNullOrEmpty()) {
                    newText.append("组织者：")
                    newText.append(sponsor)
                    newText.append("\n")
                }
                if (!placeInfo.isNullOrEmpty()) {
                    newText.append("地点：")
                    newText.append(placeInfo)
                    newText.append("\n")
                }
                if (!condition.isNullOrEmpty()) {
                    newText.append("状态：")
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
            newText.append("位置信息：\n")
            if (parse) {
                val tmp = res.getLocationCoordinate()
                val latitude = tmp.getLatitude()
                val longitude = tmp.getLongitude()
                newText.append("经度：")
                newText.append(longitude)
                newText.append("\n")
                newText.append("纬度：")
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
                    newText.append("收件号码：")
                    newText.append(destPhoneNumber)
                    newText.append("\n")
                }
                if (!msgContent.isNullOrEmpty()) {
                    newText.append("内容：")
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
                            newText.append("手机：")
                        }

                        TelPhoneNumber.RESIDENTIAL_USE_TYPE -> {
                            newText.append("住家：")
                        }

                        TelPhoneNumber.OFFICE_USE_TYPE -> {
                            newText.append("工作：")
                        }

                        TelPhoneNumber.FAX_USE_TYPE -> {
                            newText.append("传真：")
                        }

                        TelPhoneNumber.OTHER_USE_TYPE -> {
                            newText.append("其他：")
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
                    newText.append("标题：")
                    newText.append(theme)
                    newText.append("\n")
                }
                if (!linkValue.isNullOrEmpty()) {
                    newText.append("链接：")
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
                    newText.append("SSID (网络名称)：")
                    newText.append(ssid)
                    newText.append("\n")
                }
                if (!pwd.isNullOrEmpty()) {
                    newText.append("密码：")
                    newText.append(pwd)
                    newText.append("\n")
                }
                newText.append("加密方式：")
                when (cipherMode) {
                    WiFiConnectionInfo.WPA_MODE_TYPE -> {
                        newText.append("WPA*")
                    }

                    WiFiConnectionInfo.WEP_MODE_TYPE -> {
                        newText.append("WEP")
                    }

                    WiFiConnectionInfo.NO_PASSWORD_MODE_TYPE -> {
                        newText.append("开放")
                    }

                    WiFiConnectionInfo.SAE_MODE_TYPE -> {
                        newText.append("WPA3-SAE")
                    }
                }
                newText.append("\n")
                newText.append("隐藏网络：")
                if (res.getOriginalValue().indexOf("H:true", ignoreCase = true) != -1) {
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
            newText.append("其他信息：\n")
            newText.append(res.getOriginalValue())
            newText.append("\n")
        }
        return newText.toString()
    }

    private fun printResult(res: HmsScan) {
        val newText = StringBuilder()
        val curText = binding.textView.text.toString()
        if (curText != WAIT_FOR_SCAN && curText != CLEARED_WAIT_FOR_SCAN) {
            newText.append(curText)
            newText.append("\n")
        }
        newText.append("---------- 第 ")
        newText.append(++scanCnt)
        newText.append(" 次识别 ----------\n")
        newText.append(concatCodeInfo(res))
        binding.textView.text = newText.toString()
    }

    private fun printResults(results: Array<HmsScan>) {
        val n = results.size
        val newText = StringBuilder()
        val curText = binding.textView.text.toString()
        if (curText != WAIT_FOR_SCAN && curText != CLEARED_WAIT_FOR_SCAN) {
            newText.append(curText)
            newText.append("\n")
        }
        newText.append("---------- 第 ")
        newText.append(++scanCnt)
        newText.append(" 次识别 ----------\n")
        newText.append("检测到多码，数量：")
        newText.append(n)
        newText.append("\n")
        for (i in 0 until n) {
            newText.append("---------- 码 ")
            newText.append(i + 1)
            newText.append(" ----------\n")
            newText.append(concatCodeInfo(results[i]))
        }
        binding.textView.text = newText.toString()
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
        if (grantResults.size < 2 || grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission granted: false")
            Toast.makeText(this, "权限授予失败，请重试！", Toast.LENGTH_LONG).show()
            return
        }
        // Default View Mode
        if (requestCode == REQUEST_PERM_REQ_CODE) {
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
            res?.let { printResult(it) }
        }
        if (requestCode == MULTIPLE_FILE_CHOICE_REQ_CODE) {
            val cd = data.clipData
            val multiFile = cd != null
            if (multiFile) {
                for (i in 0 until cd!!.itemCount) {
                    val item = cd.getItemAt(i)
                    scanPic(item.uri)
                }
            } else {
                data.data?.let { scanPic(it) }
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
}