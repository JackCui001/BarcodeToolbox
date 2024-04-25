package com.jackcui.codesc

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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.jackcui.codesc.MainActivity.Companion.TAG
import com.jackcui.codesc.MainActivity.Companion.showErrorToast
import com.jackcui.codesc.MainActivity.Companion.showInfoToast
import com.jackcui.codesc.databinding.ActivityGenerateCodeBinding
import com.jackcui.codesc.databinding.DialogContactBinding
import com.jackcui.codesc.databinding.DialogCoordBinding
import com.jackcui.codesc.databinding.DialogEmailBinding
import com.jackcui.codesc.databinding.DialogPhoneBinding
import com.jackcui.codesc.databinding.DialogSmsBinding
import com.jackcui.codesc.databinding.DialogWifiBinding
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

    private val convertInfoDismissCallback = DialogInterface.OnDismissListener {
        if (res.isNotBlank()) {
            binding.tietGenContent.setText(res)
            showInfoToast("特殊信息转换完成")
            res.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding
        binding = ActivityGenerateCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        supportActionBar?.title = "构建码"
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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
        var selectedItemIdx = -1

        binding.tvLenInd.text = "剩余可用字节：N/A"
        binding.tietGenContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (selectedItemIdx == -1) {
                    return
                }
                binding.tvLenInd.text = "剩余可用字节：${
                    maxBytes[selectedItemIdx] - s.toString().encodeToByteArray().size
                }"
            }
        })

        binding.actvCodeType.setOnItemClickListener { _, _, position, _ ->
            selectedItemIdx = position
            binding.tvLenInd.text = "剩余可用字节：${
                maxBytes[selectedItemIdx] - binding.tietGenContent.text.toString()
                    .encodeToByteArray().size
            }"
            if (showCodeTypeInfo) {
                MaterialAlertDialogBuilder(this@GenerateCodeActivity).setTitle(codeTypeStrArr[selectedItemIdx])
                    .setMessage(hints[selectedItemIdx])
                    .setPositiveButton("确认") { dialog, _ -> dialog.dismiss() }.show()
            }
        }

//        inputDialog.setOnDismissListener {
//            (wifiInputDialogBinding.root.parent as ViewGroup).removeView(wifiInputDialogBinding.root)
//        }

        binding.btnSpInfoConvert.setOnClickListener {
            val items = arrayOf("Wi-Fi", "E-mail", "电话号码", "短信", "联系人", "坐标")

            MaterialAlertDialogBuilder(this).setTitle("请选择一种信息类型")
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
            val codeTypeText = binding.actvCodeType.text
            if (codeTypeText.isNullOrEmpty()) {
                showErrorToast("未选择码类型，无法生成浏览")
                return@setOnClickListener
            }
            val lenIndicatorText = binding.tvLenInd.text
            if (lenIndicatorText.substring(lenIndicatorText.indexOf("：") + 1).toInt() < 0) {
                showErrorToast("超出字节数限制，无法生成预览")
                return@setOnClickListener
            }
            val content = binding.tietGenContent.text
            if (content.isNullOrEmpty()) {
                showErrorToast("文本内容为空，无法生成浏览")
                return@setOnClickListener
            }
            val type = codeType[selectedItemIdx]
            val width = 400
            val height = 400
            try {
                codeBitmap = ScanUtil.buildBitmap(content.toString(), type, width, height, null)
                binding.ivCodePreview.setImageBitmap(codeBitmap)
                showInfoToast("生成预览成功，长按图片以保存")
                codePreviewIdx = selectedItemIdx
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast("发生异常，请仔细检查输入内容是否满足特定码要求")
            }
        }

        binding.ivCodePreview.setOnLongClickListener {
            reqPermAndWrite2File()
            true
        }
    }

    private fun reqPermAndWrite2File() {
        XXPermissions.with(this).permission(Permission.WRITE_EXTERNAL_STORAGE)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (!allGranted) {
                        return
                    }
                    writeCodePicFile()
                }

                override fun onDenied(
                    permissions: MutableList<String>, doNotAskAgain: Boolean
                ) {
                    if (doNotAskAgain) {
                        showErrorToast("权限请求被永久拒绝，请在系统设置中手动授权。\n本应用仅申请必要权限，请放心授权。")
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(
                            this@GenerateCodeActivity, permissions
                        )
                    } else {
                        showErrorToast("权限请求被拒绝，请允许授予权限以正常使用此应用。\n本应用仅申请必要权限，请放心授权。")
                    }
                }
            })
    }

    private fun writeCodePicFile() {
        if (codeBitmap == null) {
            showErrorToast("请先生成预览")
            return
        }

        // Add a specific media item
        val resolver = applicationContext.contentResolver

        // Find all image files on the primary external storage device
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        // Get time
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.CHINA)

        // Create a new image file
        val filename = "${sdf.format(Date())}_${codeTypeStrArr[codePreviewIdx]}.png"
        val filePath =
            "${Environment.getExternalStorageDirectory().path}${File.separator}${Environment.DIRECTORY_PICTURES}${File.separator}CodeScanner"
        val filePathAndName = "$filePath${File.separator}$filename"
        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}${File.separator}CodeScanner"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
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
            showInfoToast("图片已保存到：$filePathAndName")
        } else {
            showErrorToast("发生异常，图片保存失败")
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
        val cipherArr = resources.getStringArray(R.array.cipher)
        var cipherText = ""
        val wifiBinding = DialogWifiBinding.inflate(layoutInflater)
        wifiBinding.actvWifiCipher.setOnItemClickListener { _, _, position, _ ->
            cipherText = cipherArr[position]
            if (cipherText == "无") {
                wifiBinding.tietWifiPwd.setText("")
                wifiBinding.tilWifiPwd.helperText = "当前加密方式无需密码"
                wifiBinding.tilWifiPwd.isEnabled = false
            } else {
                wifiBinding.tilWifiPwd.helperText = null
                wifiBinding.tilWifiPwd.isEnabled = true
            }
        }
        wifiStr.append("WIFI:")
        val dialog = MaterialAlertDialogBuilder(this).setView(wifiBinding.root).setTitle("Wi-Fi")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showWifiDialog: OnCancel")
                wifiStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var ok = true
            if (wifiBinding.tietWifiSsid.text.isNullOrBlank()) {
                wifiBinding.tilWifiSsid.error = "SSID不能为空"
                ok = false
            } else {
                wifiBinding.tilWifiSsid.error = null
            }
            if (wifiBinding.tilWifiPwd.isEnabled && wifiBinding.tietWifiPwd.text.isNullOrBlank()) {
                wifiBinding.tilWifiPwd.error = "密码不能为空"
                ok = false
            } else {
                wifiBinding.tilWifiPwd.error = null
            }
            if (wifiBinding.actvWifiCipher.text.isNullOrEmpty()) {
                wifiBinding.tilWifiCipher.error = "加密方式不能为空"
                ok = false
            } else {
                wifiBinding.tilWifiCipher.error = null
            }
            if (!ok) {
                return@setOnClickListener
            }
            when (cipherText) {
                "WPA/WPA2" -> {
                    wifiStr.append("T:WPA;")
                }

                "WPA3" -> {
                    wifiStr.append("T:SAE;")
                }

                "WEP" -> {
                    wifiStr.append("T:WEP;")
                }
            }
            wifiStr.append("S:${wifiBinding.tietWifiSsid.text};")
            if (wifiBinding.tilWifiPwd.isEnabled) {
                wifiStr.append("P:${wifiBinding.tietWifiPwd.text};")
            }
            wifiStr.append("H:${wifiBinding.cbWifiHidden.isChecked};")
            wifiStr.append(";")
            dialog.dismiss()
        }
    }

    private fun showEmailDialog(emailStr: StringBuilder) {
        val emailBinding = DialogEmailBinding.inflate(layoutInflater)
        emailStr.append("MATMSG:")
        val dialog = MaterialAlertDialogBuilder(this).setView(emailBinding.root).setTitle("E-mail")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showEmailDialog: OnCancel")
                emailStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var ok = true
            if (emailBinding.tietEmailAddr.text.isNullOrBlank()) {
                emailBinding.tilEmailAddr.error = "收件邮箱不能为空"
                ok = false
            } else {
                emailBinding.tilEmailAddr.error = null
            }
            if (emailBinding.tietEmailSubject.text.isNullOrBlank()) {
                emailBinding.tilEmailSubject.error = "主题不能为空"
                ok = false
            } else {
                emailBinding.tilEmailSubject.error = null
            }
            if (emailBinding.tietEmailContent.text.isNullOrBlank()) {
                emailBinding.tilEmailContent.error = "内容不能为空"
                ok = false
            } else {
                emailBinding.tilEmailContent.error = null
            }
            if (!ok) {
                return@setOnClickListener
            }
            emailStr.append("TO:${emailBinding.tietEmailAddr.text};SUB:${emailBinding.tietEmailSubject.text};BODY:${emailBinding.tietEmailContent.text};;")
            dialog.dismiss()
        }
    }

    private fun showPhoneDialog(phoneStr: StringBuilder) {
        val phoneBinding = DialogPhoneBinding.inflate(layoutInflater)
        phoneStr.append("TEL:")
        val dialog =
            MaterialAlertDialogBuilder(this).setView(phoneBinding.root).setTitle("电话号码")
                .setPositiveButton("确认", null).setOnCancelListener {
                    Log.d(TAG, "showPhoneDialog: OnCancel")
                    phoneStr.clear()
                }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (phoneBinding.tietPhoneNumber.text.isNullOrBlank()) {
                phoneBinding.tilPhoneNumber.error = "电话号码不能为空"
                return@setOnClickListener
            }
            phoneStr.append("${phoneBinding.tietPhoneNumber.text}")
            dialog.dismiss()
        }
    }

    private fun showSmsDialog(smsStr: StringBuilder) {
        val smsBinding = DialogSmsBinding.inflate(layoutInflater)
        smsStr.append("SMSTO:")
        val dialog = MaterialAlertDialogBuilder(this).setView(smsBinding.root).setTitle("短信")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showSmsDialog: OnCancel")
                smsStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var ok = true
            if (smsBinding.tietSmsPhone.text.isNullOrBlank()) {
                smsBinding.tilSmsPhone.error = "收件邮箱不能为空"
                ok = false
            } else {
                smsBinding.tilSmsPhone.error = null
            }
            if (smsBinding.tietSmsContent.text.isNullOrBlank()) {
                smsBinding.tilSmsContent.error = "内容不能为空"
                ok = false
            } else {
                smsBinding.tilSmsContent.error = null
            }
            if (!ok) {
                return@setOnClickListener
            }
            smsStr.append("${smsBinding.tietSmsPhone.text}:${smsBinding.tietSmsContent.text}")
            dialog.dismiss()
        }
    }

    private fun showContactDialog(contactStr: StringBuilder) {
        val contactBinding = DialogContactBinding.inflate(layoutInflater)
        contactStr.append("BEGIN:VCARD\nVERSION:3.0\n")
        val dialog =
            MaterialAlertDialogBuilder(this).setView(contactBinding.root).setTitle("联系人")
                .setPositiveButton("确认", null).setOnCancelListener {
                    Log.d(TAG, "showContactDialog: OnCancel")
                    contactStr.clear()
                }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val lastName = contactBinding.tietContactLastName.text
            val firstName = contactBinding.tietContactFirstName.text
            val org = contactBinding.tietContactOrg.text
            val jobTitle = contactBinding.tietContactJobTitle.text
            val street = contactBinding.tietContactStreet.text
            val city = contactBinding.tietContactCity.text
            val region = contactBinding.tietContactRegion.text
            val postcode = contactBinding.tietContactPostcode.text
            val country = contactBinding.tietContactCountry.text
            val officePhone = contactBinding.tietContactOfficePhone.text
            val mobilePhone = contactBinding.tietContactMobilePhone.text
            val fax = contactBinding.tietContactFax.text
            val email = contactBinding.tietContactEmail.text
            val url = contactBinding.tietContactWebsite.text
            if (lastName.isNullOrBlank() && firstName.isNullOrBlank() && org.isNullOrBlank() && jobTitle.isNullOrBlank() && street.isNullOrBlank() && city.isNullOrBlank() && region.isNullOrBlank() && postcode.isNullOrBlank() && country.isNullOrBlank() && officePhone.isNullOrBlank() && mobilePhone.isNullOrBlank() && fax.isNullOrBlank() && email.isNullOrBlank() && url.isNullOrBlank()) {
                dialog.cancel()
                showErrorToast("至少需要输入一项信息")
            }
            if (!lastName.isNullOrBlank() || !firstName.isNullOrBlank()) {
                contactStr.append("N:")
                if (!lastName.isNullOrBlank() && !firstName.isNullOrBlank()) {
                    contactStr.append("$lastName;$firstName\n")
                } else if (!lastName.isNullOrBlank()) {
                    contactStr.append("$lastName;\n")
                } else {
                    contactStr.append(";$firstName\n")
                }
            }
            if (!firstName.isNullOrBlank() || !lastName.isNullOrBlank()) {
                contactStr.append("FN:")
                if (!firstName.isNullOrBlank()) {
                    contactStr.append("$firstName")
                }
                if (!lastName.isNullOrBlank()) {
                    contactStr.append(" $lastName")
                }
                contactStr.append('\n')
            }
            if (!org.isNullOrBlank()) {
                contactStr.append("ORG:$org\n")
            }
            if (!jobTitle.isNullOrBlank()) {
                contactStr.append("TITLE:$jobTitle\n")
            }
            if (!street.isNullOrBlank() || !city.isNullOrBlank() || !region.isNullOrBlank() || !postcode.isNullOrBlank() || !country.isNullOrBlank()) {
                contactStr.append("ADR:;")
                contactStr.append(if (!street.isNullOrBlank()) ";$street" else ";")
                contactStr.append(if (!city.isNullOrBlank()) ";$city" else ";")
                contactStr.append(if (!region.isNullOrBlank()) ";$region" else ";")
                contactStr.append(if (!postcode.isNullOrBlank()) ";$postcode" else ";")
                contactStr.append(if (!country.isNullOrBlank()) ";$country" else ";")
                contactStr.append("\n")
            }
            if (!officePhone.isNullOrBlank()) {
                contactStr.append("TEL;WORK;VOICE:$officePhone\n")
            }
            if (!mobilePhone.isNullOrBlank()) {
                contactStr.append("TEL;CELL:$mobilePhone\n")
            }
            if (!fax.isNullOrBlank()) {
                contactStr.append("TEL;FAX:$fax\n")
            }
            if (!email.isNullOrBlank()) {
                contactStr.append("EMAIL;WORK;INTERNET:$email\n")
            }
            if (!url.isNullOrBlank()) {
                contactStr.append("URL:$url\n")
            }
            contactStr.append("END:VCARD")
            dialog.dismiss()
        }
    }

    private fun showCoordDialog(coordStr: StringBuilder) {
        val coordBinding = DialogCoordBinding.inflate(layoutInflater)
        coordStr.append("GEO:")
        val dialog = MaterialAlertDialogBuilder(this).setView(coordBinding.root).setTitle("坐标")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showCoordDialog: OnCancel")
                coordStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var ok = true
            if (coordBinding.tietCoordLongitude.text.isNullOrBlank()) {
                coordBinding.tilCoordLongitude.error = "经度不能为空"
                ok = false
            } else {
                coordBinding.tilCoordLongitude.error = null
            }
            if (coordBinding.tietCoordLatitude.text.isNullOrBlank()) {
                coordBinding.tilCoordLatitude.error = "纬度不能为空"
                ok = false
            } else {
                coordBinding.tilCoordLatitude.error = null
            }
            if (!ok) {
                return@setOnClickListener
            }
            coordStr.append("${coordBinding.tietCoordLatitude.text},${coordBinding.tietCoordLongitude.text}")
            dialog.dismiss()
        }
    }

//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        super.onOptionsItemSelected(item)
//        when (item.itemId) {
//            android.R.id.home -> {
//                finish()
//                return true
//            }
//        }
//        return false
//    }

    override fun onResume() {
        super.onResume()
        readSettings()  // 读取设置
    }

    private fun readSettings() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        showCodeTypeInfo = sharedPreferences.getBoolean("show_code_type_info", true)
    }
}