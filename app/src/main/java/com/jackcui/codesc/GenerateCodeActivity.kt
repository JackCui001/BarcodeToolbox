package com.jackcui.codesc

import android.Manifest
import android.content.ContentValues
import android.content.DialogInterface
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.jackcui.codesc.MainActivity.Companion.TAG
import com.jackcui.codesc.MainActivity.Companion.showSnackbar
import com.jackcui.codesc.databinding.ActivityGenerateCodeBinding
import com.jackcui.codesc.databinding.DialogContactBinding
import com.jackcui.codesc.databinding.DialogCoordBinding
import com.jackcui.codesc.databinding.DialogEmailBinding
import com.jackcui.codesc.databinding.DialogPhoneBinding
import com.jackcui.codesc.databinding.DialogSmsBinding
import com.jackcui.codesc.databinding.DialogWifiBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class GenerateCodeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGenerateCodeBinding
    private lateinit var codeTypeStrArr: Array<String>
    private var showCodeTypeInfo = true
    private var codePreviewIdx = 0
    private var codeBitmap: Bitmap? = null
    private var res = StringBuilder()

    private val reqPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                genCode()
            } else {
                AlertDialog.Builder(this).setTitle("提示")
                    .setMessage("权限授予失败，请允许授予权限以保存二维码到本地。\n本应用仅申请必要权限，请放心授权。")
                    .setNegativeButton("关闭") { _, _ ->
                    }.show()
            }
        }
    private val convertInfoDismissCallback = DialogInterface.OnDismissListener {
        if (res.isNotBlank()) {
            binding.etGenContent.setText(res)
            showSnackbar(binding.root, "特殊信息转换完成", Snackbar.LENGTH_SHORT)
            res.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding
        binding = ActivityGenerateCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "构建码"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 读取设置
        readSettings()

        val codeType = intArrayOf(
            HmsScan.QRCODE_SCAN_TYPE,
            HmsScan.AZTEC_SCAN_TYPE,
            HmsScan.PDF417_SCAN_TYPE,
            HmsScan.DATAMATRIX_SCAN_TYPE,
            HmsScan.UPCCODE_A_SCAN_TYPE,
            HmsScan.UPCCODE_E_SCAN_TYPE,
            HmsScan.ITF14_SCAN_TYPE,
            HmsScan.EAN8_SCAN_TYPE,
            HmsScan.EAN13_SCAN_TYPE,
            HmsScan.CODE39_SCAN_TYPE,
            HmsScan.CODE93_SCAN_TYPE,
            HmsScan.CODE128_SCAN_TYPE,
            HmsScan.CODABAR_SCAN_TYPE
        )
        codeTypeStrArr = resources.getStringArray(R.array.code)
        val maxBytes = resources.getIntArray(R.array.maxBytes)
        val hints = resources.getStringArray(R.array.hints)
        var selectedItemIdx = 0

        binding.tvLenInd.text = "剩余可用字节：${maxBytes[0]}"
        binding.etGenContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                binding.tvLenInd.text =
                    "剩余可用字节：${
                        maxBytes[selectedItemIdx] - s.toString().encodeToByteArray().size
                    }"
            }
        })

        binding.spnCodeType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectedItemIdx = parent?.selectedItemPosition ?: 0
                    binding.tvLenInd.text =
                        "剩余可用字节：${
                            maxBytes[selectedItemIdx] - binding.etGenContent.text.toString()
                                .encodeToByteArray().size
                        }"
                    if (showCodeTypeInfo) {
                        AlertDialog.Builder(this@GenerateCodeActivity)
                            .setTitle(codeTypeStrArr[selectedItemIdx])
                            .setMessage(hints[selectedItemIdx])
                            .setPositiveButton("确认") { dialog, _ -> dialog.dismiss() }.show()
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }


//        inputDialog.setOnDismissListener {
//            (wifiInputDialogBinding.root.parent as ViewGroup).removeView(wifiInputDialogBinding.root)
//        }

        binding.btnSpInfoConvert.setOnClickListener {
            val items = arrayOf("Wi-Fi", "E-mail", "电话号码", "短信", "联系人", "坐标")

            AlertDialog.Builder(this).setTitle("请选择一种信息类型")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> showWifiDialog(res)
                        1 -> showEmailDialog(res)
                        2 -> showPhoneDialog(res)
                        3 -> showSmsDialog(res)
                        4 -> showContactDialog(res)
                        5 -> showCoordDialog(res)
                    }
                }.show()
        }

        binding.btnGenCode.setOnClickListener {
            val lenIndicatorText = binding.tvLenInd.text
            if (lenIndicatorText.substring(lenIndicatorText.indexOf("：") + 1).toInt() < 0) {
                showSnackbar(it, "超出字节数限制，无法生成预览！", Snackbar.LENGTH_LONG)
                return@setOnClickListener
            }
            val content = binding.etGenContent.text.toString()
            if (content.isEmpty()) {
                showSnackbar(binding.root, "请先输入要生成的文本内容！", Snackbar.LENGTH_LONG)
                return@setOnClickListener
            }
            val type = codeType[selectedItemIdx]
            val width = 400
            val height = 400
            try {
                codeBitmap = ScanUtil.buildBitmap(content, type, width, height, null)
                GlobalScope.launch(Dispatchers.Main) {
                    binding.ivCodePreview.setImageBitmap(codeBitmap)
                }
                showSnackbar(it, "生成预览成功，长按图片以保存", Snackbar.LENGTH_LONG)
                codePreviewIdx = selectedItemIdx
            } catch (e: Exception) {
                e.printStackTrace()
                showSnackbar(
                    binding.root,
                    "发生异常！请仔细检查输入内容是否满足特定码要求！",
                    Snackbar.LENGTH_LONG
                )
            }
        }

        binding.ivCodePreview.setOnLongClickListener {
            reqPermAndGenCode()
            true
        }
    }

    private fun reqPermAndGenCode() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            reqPermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            genCode()
        }
    }

    private fun genCode() {
//        GlobalScope.launch(Dispatchers.IO) {
        if (codeBitmap == null) {
            showSnackbar(binding.root, "请先生成预览！", Snackbar.LENGTH_SHORT)
            return
        }

        // Add a specific media item
        val resolver = applicationContext.contentResolver

        // Find all image files on the primary external storage device
        val imageCollection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

        // Get time
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.CHINA)

        // Create a new image file
        val fileName = "${sdf.format(Date())}_${codeTypeStrArr[codePreviewIdx]}.png"
        val filePath: String
        var filePathAndName: String
        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativeFilePath =
                    "${Environment.DIRECTORY_PICTURES}${File.separator}CodeScanner"
                filePathAndName =
                    "${Environment.getExternalStorageDirectory().path}${File.separator}${Environment.DIRECTORY_PICTURES}${File.separator}CodeScanner${File.separator}$fileName"
                put(MediaStore.Images.Media.RELATIVE_PATH, relativeFilePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                filePath =
                    "${Environment.getExternalStorageDirectory().path}${File.separator}${Environment.DIRECTORY_PICTURES}${File.separator}CodeScanner"
                filePathAndName = filePath + "${File.separator}$fileName"
                val folder = File(filePath)
                if (!folder.exists()) {
                    folder.mkdirs()
                }
                put(MediaStore.MediaColumns.DATA, filePathAndName)
            }
        }
        Log.d(TAG, "genCode: $filePathAndName")

        // Keep a handle to the new image's URI in case you need to modify it later
        val imageUri = resolver.insert(imageCollection, imageDetails)

        // Write data into the pending image file
        imageUri?.let {
            // Method 1
//            resolver.openFileDescriptor(it, "w", null).use { pfd ->
//                pfd?.let {
//                    FileOutputStream(it.fileDescriptor).use {
//                        codeBitmap?.compress(Bitmap.CompressFormat.PNG, 100, it)
//                    }
//                }
//            }

            // Method 2
            resolver.openOutputStream(it).use { os ->
                os?.let { codeBitmap?.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }

        // Clear pending flag (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageDetails.clear()
            imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
            imageUri?.let {
                resolver.update(imageUri, imageDetails, null, null)
            }
        }

        if (imageUri != null) {
            showSnackbar(binding.root, "图片已保存到：$filePathAndName", Snackbar.LENGTH_LONG)
        } else {
            showSnackbar(binding.root, "发生异常，图片保存失败！", Snackbar.LENGTH_LONG)
        }

        // Refresh MediaStore
//            MediaScannerConnection.scanFile(
//                this@GenerateCodeActivity,
//                arrayOf(filePath),
//                arrayOf("image/png")
//            ) { path, uri ->
//                Log.d(TAG, "Refresh MediaStore:\n$path\n$uri")
//            }
//        }
    }

    private fun showWifiDialog(wifiStr: StringBuilder) {
        var cipherType = "WPA"
        val wifiBinding = DialogWifiBinding.inflate(layoutInflater)
        wifiBinding.spnCipherType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedItem = parent?.selectedItem.toString()
                    if (selectedItem == "无") {
                        wifiBinding.etWifiPwd.setText("")
                        wifiBinding.etWifiPwd.hint = "*当前加密方式无需密码*"
                        wifiBinding.etWifiPwd.isEnabled = false
                    } else {
                        wifiBinding.etWifiPwd.isEnabled = true
                        wifiBinding.etWifiPwd.hint = "密码"
                    }
                    cipherType = selectedItem
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        wifiStr.append("WIFI:")
        val dialog = AlertDialog.Builder(this).setView(wifiBinding.root).setTitle("Wi-Fi")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showWifiDialog: OnCancel")
                wifiStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (wifiBinding.etWifiSsid.text.isBlank()) {
                wifiBinding.etWifiSsid.error = "SSID不能为空"
                return@setOnClickListener
            }
            if (wifiBinding.etWifiPwd.isEnabled && wifiBinding.etWifiPwd.text.isBlank()) {
                wifiBinding.etWifiPwd.error = "密码不能为空"
                return@setOnClickListener
            }
            if (cipherType != "无") {
                wifiStr.append("T:$cipherType;")
            }
            wifiStr.append("S:${wifiBinding.etWifiSsid.text};")
            if (wifiBinding.etWifiPwd.isEnabled) {
                wifiStr.append("P:${wifiBinding.etWifiPwd.text};")
            }
            wifiStr.append("H:${wifiBinding.etWifiHidden.isChecked};")
            dialog.dismiss()
        }
    }

    private fun showEmailDialog(emailStr: StringBuilder) {
        val emailBinding = DialogEmailBinding.inflate(layoutInflater)
        emailStr.append("MATMSG:")
        val dialog = AlertDialog.Builder(this).setView(emailBinding.root).setTitle("E-mail")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showEmailDialog: OnCancel")
                emailStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (emailBinding.etEmailAddr.text.isBlank()) {
                emailBinding.etEmailAddr.error = "收件邮箱不能为空"
                return@setOnClickListener
            }
            if (emailBinding.etEmailSubject.text.isBlank()) {
                emailBinding.etEmailSubject.error = "主题不能为空"
                return@setOnClickListener
            }
            if (emailBinding.etEmailContent.text.isBlank()) {
                emailBinding.etEmailContent.error = "内容不能为空"
                return@setOnClickListener
            }
            emailStr.append("TO:${emailBinding.etEmailAddr.text};SUB:${emailBinding.etEmailSubject.text};BODY:${emailBinding.etEmailContent.text};;")
            dialog.dismiss()
        }
    }

    private fun showPhoneDialog(phoneStr: StringBuilder) {
        val phoneBinding = DialogPhoneBinding.inflate(layoutInflater)
        phoneStr.append("TEL:")
        val dialog = AlertDialog.Builder(this).setView(phoneBinding.root).setTitle("电话号码")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showPhoneDialog: OnCancel")
                phoneStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (phoneBinding.etPhoneNumber.text.isBlank()) {
                phoneBinding.etPhoneNumber.error = "电话号码不能为空"
                return@setOnClickListener
            }
            phoneStr.append("${phoneBinding.etPhoneNumber.text}")
            dialog.dismiss()
        }
    }

    private fun showSmsDialog(smsStr: StringBuilder) {
        val smsBinding = DialogSmsBinding.inflate(layoutInflater)
        smsStr.append("SMSTO:")
        val dialog = AlertDialog.Builder(this).setView(smsBinding.root).setTitle("短信")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showSmsDialog: OnCancel")
                smsStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (smsBinding.etSmsPhone.text.isBlank()) {
                smsBinding.etSmsPhone.error = "收件邮箱不能为空"
                return@setOnClickListener
            }
            if (smsBinding.etSmsContent.text.isBlank()) {
                smsBinding.etSmsContent.error = "内容不能为空"
                return@setOnClickListener
            }
            smsStr.append("${smsBinding.etSmsPhone.text}:${smsBinding.etSmsContent.text}")
            dialog.dismiss()
        }
    }

    private fun showContactDialog(contactStr: StringBuilder) {
        val contactBinding = DialogContactBinding.inflate(layoutInflater)
        contactStr.append("BEGIN:VCARD\nVERSION:3.0\n")
        val dialog = AlertDialog.Builder(this).setView(contactBinding.root).setTitle("联系人")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showContactDialog: OnCancel")
                contactStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val firstName = contactBinding.etContactFirstName.text
            val lastName = contactBinding.etContactLastName.text
            val org = contactBinding.etContactOrg.text
            val jobTitle = contactBinding.etContactJobTitle.text
            val street = contactBinding.etContactStreet.text
            val city = contactBinding.etContactCity.text
            val region = contactBinding.etContactRegion.text
            val postcode = contactBinding.etContactPostcode.text
            val country = contactBinding.etContactCountry.text
            val officePhone = contactBinding.etContactOfficePhone.text
            val mobilePhone = contactBinding.etContactMobilePhone.text
            val fax = contactBinding.etContactFax.text
            val email = contactBinding.etContactEmail.text
            val url = contactBinding.etContactWebsite.text
            if (firstName.isBlank() && lastName.isBlank() && org.isBlank() && jobTitle.isBlank() &&
                street.isBlank() && city.isBlank() && region.isBlank() && postcode.isBlank() &&
                country.isBlank() && officePhone.isBlank() && mobilePhone.isBlank() && fax.isBlank() &&
                email.isBlank() && url.isBlank()
            ) {
                dialog.cancel()
                showSnackbar(binding.root, "请至少输入一项信息！", Snackbar.LENGTH_SHORT)
            }
            if (firstName.isNotBlank() || lastName.isNotBlank()) {
                contactStr.append("N:")
                if (firstName.isNotBlank() && lastName.isNotBlank()) {
                    contactStr.append("$lastName;$firstName\n")
                } else if (lastName.isNotBlank()) {
                    contactStr.append("$lastName;\n")
                } else {
                    contactStr.append(";$firstName\n")
                }
            }
            if (firstName.isNotBlank() || lastName.isNotBlank()) {
                contactStr.append("FN:")
                if (firstName.isNotBlank() && lastName.isNotBlank()) {
                    contactStr.append("$firstName $lastName\n")
                } else if (lastName.isNotBlank()) {
                    contactStr.append(" $lastName\n")
                } else {
                    contactStr.append("$firstName\n")
                }
            }
            if (org.isNotBlank()) {
                contactStr.append("ORG:$org\n")
            }
            if (jobTitle.isNotBlank()) {
                contactStr.append("TITLE:$jobTitle\n")
            }
            if (street.isNotBlank() || city.isNotBlank() || region.isNotBlank() || postcode.isNotBlank() || country.isNotBlank()) {
                contactStr.append("ADR:;")
                contactStr.append(if (street.isNotBlank()) ";$street" else ";")
                contactStr.append(if (city.isNotBlank()) ";$city" else ";")
                contactStr.append(if (region.isNotBlank()) ";$region" else ";")
                contactStr.append(if (postcode.isNotBlank()) ";$postcode" else ";")
                contactStr.append(if (country.isNotBlank()) ";$country" else ";")
                contactStr.append("\n")
            }
            if (officePhone.isNotBlank()) {
                contactStr.append("TEL;WORK;VOICE:$officePhone\n")
            }
            if (mobilePhone.isNotBlank()) {
                contactStr.append("TEL;CELL:$mobilePhone\n")
            }
            if (fax.isNotBlank()) {
                contactStr.append("TEL;FAX:$fax\n")
            }
            if (email.isNotBlank()) {
                contactStr.append("EMAIL;WORK;INTERNET:$email\n")
            }
            if (url.isNotBlank()) {
                contactStr.append("URL:$url\n")
            }
            contactStr.append("END:VCARD")
            dialog.dismiss()
        }
    }

    private fun showCoordDialog(coordStr: StringBuilder) {
        val coordBinding = DialogCoordBinding.inflate(layoutInflater)
        coordStr.append("GEO:")
        val dialog = AlertDialog.Builder(this).setView(coordBinding.root).setTitle("坐标")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showCoordDialog: OnCancel")
                coordStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (coordBinding.etCoordLongitude.text.isBlank()) {
                coordBinding.etCoordLongitude.error = "经度不能为空"
                return@setOnClickListener
            }
            if (coordBinding.etCoordLatitude.text.isBlank()) {
                coordBinding.etCoordLatitude.error = "纬度不能为空"
                return@setOnClickListener
            }
            coordStr.append("${coordBinding.etCoordLatitude.text},${coordBinding.etCoordLongitude.text}")
            dialog.dismiss()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        super.onOptionsItemSelected(item)
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        readSettings()  // 读取设置
    }

    private fun readSettings() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        showCodeTypeInfo = sharedPreferences.getBoolean("showCodeTypeInfo", true)
    }
}