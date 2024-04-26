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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
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
    private lateinit var bd: ActivityGenerateCodeBinding
    private lateinit var codeTypeStrArr: Array<String>
    private var showCodeTypeInfo = true
    private var codePreviewIdx = 0
    private var codeBitmap: Bitmap? = null
    private var res = StringBuilder()
    private var dlgContactBd: DialogContactBinding? = null
    private var dlgPhoneBd: DialogPhoneBinding? = null
    private var dlgSmsBd: DialogSmsBinding? = null
    private var dlgEmailBd: DialogEmailBinding? = null

    private val convertInfoDismissCallback = DialogInterface.OnDismissListener {
        if (res.isNotBlank()) {
            bd.tietGenContent.setText(res)
            showInfoToast("特殊信息转换完成")
            res.clear()
        }
    }

    // 定义Activity result launcher
    private val contactLau = registerForActivityResult(ActivityResultContracts.PickContact()) {
        it?.let { uri ->
            dlgContactBd?.run {
                // 清空旧数据
                tietContactName.setText("")
                actvContactMobilePhone.setText("")
                actvContactOfficePhone.setText("")
                actvContactHomePhone.setText("")
                (actvContactMobilePhone as MaterialAutoCompleteTextView).setSimpleItems(
                    emptyArray()
                )
                (actvContactOfficePhone as MaterialAutoCompleteTextView).setSimpleItems(
                    emptyArray()
                )
                (actvContactHomePhone as MaterialAutoCompleteTextView).setSimpleItems(
                    emptyArray()
                )
                actvContactAddr.setText("")
                (actvContactAddr as MaterialAutoCompleteTextView).setSimpleItems(
                    emptyArray()
                )
                actvContactEmail.setText("")
                (actvContactEmail as MaterialAutoCompleteTextView).setSimpleItems(
                    emptyArray()
                )
                actvContactWebsite.setText("")
                (actvContactWebsite as MaterialAutoCompleteTextView).setSimpleItems(
                    emptyArray()
                )
                tietContactCompany.setText("")
                tietContactJobTitle.setText("")

                // 读取新数据并赋值
                val contactUtils = ContactUtils()
                val contactInfo = contactUtils.getContactInfo(this@GenerateCodeActivity, it)
                contactInfo?.let {
                    Log.d(TAG, it.toString())
                    if (it.name.isNotBlank()) {
                        tietContactName.setText(it.name)
                    }
                    if (it.company.isNotBlank()) {
                        tietContactCompany.setText(it.company)
                    }
                    if (it.jobTitle.isNotBlank()) {
                        tietContactJobTitle.setText(it.jobTitle)
                    }
                    val phoneNumberCnt = it.phoneNumbers.size
                    if (phoneNumberCnt > 0) {
                        tilContactHomePhone.helperText = "已导入 $phoneNumberCnt 个号码，请手动选择"
                        val phoneNumArr = it.phoneNumbers.toTypedArray()
                        actvContactMobilePhone.setSimpleItems(
                            phoneNumArr
                        )
                        actvContactOfficePhone.setSimpleItems(
                            phoneNumArr
                        )
                        actvContactHomePhone.setSimpleItems(
                            phoneNumArr
                        )
                    } else {
                        tilContactHomePhone.helperText = null
                    }

                    val emailCnt = it.emails.size
                    if (emailCnt > 0) {
                        tilContactEmail.helperText = "已导入 $emailCnt 个邮箱，请手动选择"
                        actvContactEmail.setSimpleItems(
                            it.emails.toTypedArray()
                        )
                    } else {
                        tilContactEmail.helperText = null
                    }

                    val websiteCnt = it.websites.size
                    if (websiteCnt > 0) {
                        tilContactWebsite.helperText = "已导入 $emailCnt 个网站，请手动选择"
                        actvContactWebsite.setSimpleItems(
                            it.websites.toTypedArray()
                        )
                    } else {
                        tilContactWebsite.helperText = null
                    }

                    val addressCnt = it.addresses.size
                    if (addressCnt > 0) {
                        tilContactAddr.helperText = "已导入 $addressCnt 个地址，请手动选择"
                        actvContactAddr.setSimpleItems(
                            it.addresses.toTypedArray()
                        )
                    } else {
                        tilContactAddr.helperText = null
                    }
                }
            }
        }
    }

    private val phoneLau = registerForActivityResult(ActivityResultContracts.PickContact()) {
        it?.let {
            dlgPhoneBd?.run {
                // 清空旧数据
                actvPhoneNumber.setText("")
                (actvPhoneNumber as MaterialAutoCompleteTextView).setSimpleItems(emptyArray())
                // 读取新数据并赋值
                val contactUtils = ContactUtils()
                val contactInfo = contactUtils.getContactInfo(this@GenerateCodeActivity, it)
                contactInfo?.let {
                    val phoneNumberCnt = it.phoneNumbers.size
                    if (phoneNumberCnt > 0) {
                        tilPhoneNumber.helperText = "已导入 $phoneNumberCnt 个号码，请手动选择"
                        actvPhoneNumber.setSimpleItems(
                            it.phoneNumbers.toTypedArray()
                        )
                    } else {
                        tilPhoneNumber.helperText = null
                    }
                }
            }
        }
    }

    private val smsLau = registerForActivityResult(ActivityResultContracts.PickContact()) {
        it?.let {
            dlgSmsBd?.run {
                // 清空旧数据
                actvSmsPhone.setText("")
                (actvSmsPhone as MaterialAutoCompleteTextView).setSimpleItems(emptyArray())
                // 读取新数据并赋值
                val contactUtils = ContactUtils()
                val contactInfo = contactUtils.getContactInfo(this@GenerateCodeActivity, it)
                contactInfo?.let {
                    val phoneNumberCnt = it.phoneNumbers.size
                    if (phoneNumberCnt > 0) {
                        tilSmsPhone.helperText = "已导入 $phoneNumberCnt 个号码，请手动选择"
                        actvSmsPhone.setSimpleItems(
                            it.phoneNumbers.toTypedArray()
                        )
                    } else {
                        tilSmsPhone.helperText = null
                    }
                }
            }
        }
    }

    private val emailLau = registerForActivityResult(ActivityResultContracts.PickContact()) {
        it?.let {
            dlgEmailBd?.run {
                // 清空旧数据
                actvEmailAddr.setText("")
                (actvEmailAddr as MaterialAutoCompleteTextView).setSimpleItems(emptyArray())
                // 读取新数据并赋值
                val contactUtils = ContactUtils()
                val contactInfo = contactUtils.getContactInfo(this@GenerateCodeActivity, it)
                contactInfo?.let {
                    val emailCnt = it.emails.size
                    if (emailCnt > 0) {
                        tilEmailAddr.helperText = "已导入 $emailCnt 个邮箱地址，请手动选择"
                        actvEmailAddr.setSimpleItems(
                            it.emails.toTypedArray()
                        )
                    } else {
                        tilEmailAddr.helperText = null
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding
        bd = ActivityGenerateCodeBinding.inflate(layoutInflater)
        setContentView(bd.root)

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

        bd.tvLenInd.text = "剩余可用字节：N/A"
        bd.tietGenContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence, start: Int, count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence, start: Int, before: Int, count: Int
            ) {
            }

            override fun afterTextChanged(s: Editable) {
                if (selectedItemIdx == -1) {
                    return
                }
                bd.tvLenInd.text = "剩余可用字节：${
                    maxBytes[selectedItemIdx] - s.toString().encodeToByteArray().size
                }"
            }
        })

        bd.actvCodeType.setOnItemClickListener { _, _, position, _ ->
            selectedItemIdx = position
            bd.tvLenInd.text = "剩余可用字节：${
                maxBytes[selectedItemIdx] - bd.tietGenContent.text.toString()
                    .encodeToByteArray().size
            }"
            if (showCodeTypeInfo) {
                MaterialAlertDialogBuilder(this@GenerateCodeActivity).setTitle(
                    codeTypeStrArr[selectedItemIdx]
                ).setMessage(hints[selectedItemIdx])
                    .setPositiveButton("确认") { dialog, _ -> dialog.dismiss() }.show()
            }
        }

//        inputDialog.setOnDismissListener {
//            (wifiInputDialogBinding.root.parent as ViewGroup).removeView(wifiInputDialogBinding.root)
//        }

        bd.btnSpInfoConvert.setOnClickListener {
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

        bd.btnGenCode.setOnClickListener {
            val codeTypeText = bd.actvCodeType.text
            if (codeTypeText.isNullOrEmpty()) {
                showErrorToast("未选择码类型，无法生成浏览")
                return@setOnClickListener
            }
            val lenIndicatorText = bd.tvLenInd.text
            if (lenIndicatorText.substring(lenIndicatorText.indexOf("：") + 1).toInt() < 0) {
                showErrorToast("超出字节数限制，无法生成预览")
                return@setOnClickListener
            }
            val content = bd.tietGenContent.text
            if (content.isNullOrEmpty()) {
                showErrorToast("文本内容为空，无法生成浏览")
                return@setOnClickListener
            }
            val type = codeType[selectedItemIdx]
            val width = 400
            val height = 400
            try {
                codeBitmap = ScanUtil.buildBitmap(content.toString(), type, width, height, null)
                bd.ivCodePreview.setImageBitmap(codeBitmap)
                showInfoToast("生成预览成功，长按图片以保存")
                codePreviewIdx = selectedItemIdx
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast("发生异常，请仔细检查输入内容是否满足特定码要求")
            }
        }

        bd.ivCodePreview.setOnLongClickListener {
            if (codeBitmap == null) {
                showErrorToast("请先生成预览")
                return@setOnLongClickListener true
            }
            reqPermAndWrite2File()
            true
        }
    }

    private fun reqPermAndWrite2File() {
        XXPermissions.with(this).permission(Permission.WRITE_EXTERNAL_STORAGE)
            .request(object : OnPermissionCallback {
                override fun onGranted(
                    permissions: MutableList<String>, allGranted: Boolean
                ) {
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
            showInfoToast("图片已保存至：$filePathAndName")
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
        val dlgWifiBd = DialogWifiBinding.inflate(layoutInflater)
        dlgWifiBd.actvWifiCipher.setOnItemClickListener { _, _, position, _ ->
            cipherText = cipherArr[position]
            if (cipherText == "无") {
                dlgWifiBd.tietWifiPwd.setText("")
                dlgWifiBd.tilWifiPwd.helperText = "当前加密方式无需密码"
                dlgWifiBd.tilWifiPwd.isEnabled = false
            } else {
                dlgWifiBd.tilWifiPwd.helperText = null
                dlgWifiBd.tilWifiPwd.isEnabled = true
            }
        }
        wifiStr.append("WIFI:")
        val dialog = MaterialAlertDialogBuilder(this).setView(dlgWifiBd.root).setTitle("Wi-Fi")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showWifiDialog: OnCancel")
                wifiStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var ok = true
            if (dlgWifiBd.tietWifiSsid.text.isNullOrBlank()) {
                dlgWifiBd.tilWifiSsid.error = "SSID不能为空"
                ok = false
            } else {
                dlgWifiBd.tilWifiSsid.error = null
            }
            if (dlgWifiBd.tilWifiPwd.isEnabled && dlgWifiBd.tietWifiPwd.text.isNullOrBlank()) {
                dlgWifiBd.tilWifiPwd.error = "密码不能为空"
                ok = false
            } else {
                dlgWifiBd.tilWifiPwd.error = null
            }
            if (dlgWifiBd.actvWifiCipher.text.isNullOrEmpty()) {
                dlgWifiBd.tilWifiCipher.error = "加密方式不能为空"
                ok = false
            } else {
                dlgWifiBd.tilWifiCipher.error = null
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
            wifiStr.append("S:${dlgWifiBd.tietWifiSsid.text};")
            if (dlgWifiBd.tilWifiPwd.isEnabled) {
                wifiStr.append("P:${dlgWifiBd.tietWifiPwd.text};")
            }
            wifiStr.append("H:${dlgWifiBd.cbWifiHidden.isChecked};")
            wifiStr.append(";")
            dialog.dismiss()
        }
    }

    private fun showEmailDialog(emailStr: StringBuilder) {
        val dlgEmailBd = DialogEmailBinding.inflate(layoutInflater)
        emailStr.append("MATMSG:")
        val dialog = MaterialAlertDialogBuilder(this).setView(dlgEmailBd.root).setTitle("E-mail")
            .setPositiveButton("确认", null).setNeutralButton("从通讯录获取", null)
            .setOnCancelListener {
                Log.d(TAG, "showEmailDialog: OnCancel")
                emailStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var ok = true
            if (dlgEmailBd.actvEmailAddr.text.isNullOrBlank()) {
                dlgEmailBd.tilEmailAddr.error = "收件邮箱不能为空"
                ok = false
            } else {
                dlgEmailBd.tilEmailAddr.error = null
            }
            if (dlgEmailBd.tietEmailSubject.text.isNullOrBlank()) {
                dlgEmailBd.tilEmailSubject.error = "主题不能为空"
                ok = false
            } else {
                dlgEmailBd.tilEmailSubject.error = null
            }
            if (dlgEmailBd.tietEmailContent.text.isNullOrBlank()) {
                dlgEmailBd.tilEmailContent.error = "内容不能为空"
                ok = false
            } else {
                dlgEmailBd.tilEmailContent.error = null
            }
            if (!ok) {
                return@setOnClickListener
            }
            emailStr.append("TO:${dlgEmailBd.actvEmailAddr.text};SUB:${dlgEmailBd.tietEmailSubject.text};BODY:${dlgEmailBd.tietEmailContent.text};;")
            dialog.dismiss()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            reqContactPermAndExecTask(3)
        }
    }

    private fun showPhoneDialog(phoneStr: StringBuilder) {
        dlgPhoneBd = DialogPhoneBinding.inflate(layoutInflater)
        phoneStr.append("TEL:")
        val dialog =
            MaterialAlertDialogBuilder(this).setView(dlgPhoneBd!!.root).setTitle("电话号码")
                .setPositiveButton("确认", null).setNeutralButton("从通讯录获取", null)
                .setOnCancelListener {
                    Log.d(TAG, "showPhoneDialog: OnCancel")
                    phoneStr.clear()
                }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dlgPhoneBd?.run {
                if (actvPhoneNumber.text.isNullOrBlank()) {
                    tilPhoneNumber.error = "电话号码不能为空"
                    return@setOnClickListener
                }
                phoneStr.append("${actvPhoneNumber.text}")
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            reqContactPermAndExecTask(1)
        }
    }

    private fun showSmsDialog(smsStr: StringBuilder) {
        dlgSmsBd = DialogSmsBinding.inflate(layoutInflater)
        smsStr.append("SMSTO:")
        val dialog = MaterialAlertDialogBuilder(this).setView(dlgSmsBd!!.root).setTitle("短信")
            .setPositiveButton("确认", null).setNeutralButton("从通讯录获取", null)
            .setOnCancelListener {
                Log.d(TAG, "showSmsDialog: OnCancel")
                smsStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dlgSmsBd?.run {
                var ok = true
                if (actvSmsPhone.text.isNullOrBlank()) {
                    tilSmsPhone.error = "收件邮箱不能为空"
                    ok = false
                } else {
                    tilSmsPhone.error = null
                }
                if (tietSmsContent.text.isNullOrBlank()) {
                    tilSmsContent.error = "内容不能为空"
                    ok = false
                } else {
                    tilSmsContent.error = null
                }
                if (!ok) {
                    return@setOnClickListener
                }
                smsStr.append("${actvSmsPhone.text}:${tietSmsContent.text}")
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            reqContactPermAndExecTask(2)
        }
    }

    private fun showContactDialog(contactStr: StringBuilder) {
        dlgContactBd = DialogContactBinding.inflate(layoutInflater)
        contactStr.appendLine("BEGIN:VCARD\nVERSION:3.0")
        val dialog =
            MaterialAlertDialogBuilder(this).setView(dlgContactBd!!.root).setTitle("联系人")
                .setPositiveButton("确认", null).setNeutralButton("从通讯录获取", null)
                .setOnCancelListener {
                    Log.d(TAG, "showContactDialog: OnCancel")
                    contactStr.clear()
                }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            reqContactPermAndExecTask(0)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dlgContactBd?.run {
                val name = tietContactName.text
                val mobilePhone = actvContactMobilePhone.text
                val officePhone = actvContactOfficePhone.text
                val homePhone = actvContactHomePhone.text
                val addr = actvContactAddr.text
                val email = actvContactEmail.text
                val url = actvContactWebsite.text
                val company = tietContactCompany.text
                val jobTitle = tietContactJobTitle.text
                if (name.isNullOrBlank() && officePhone.isNullOrBlank() && mobilePhone.isNullOrBlank() && homePhone.isNullOrBlank() && addr.isNullOrBlank() && email.isNullOrBlank() && company.isNullOrBlank() && jobTitle.isNullOrBlank() && url.isNullOrBlank()) {
                    dialog.cancel()
                    showErrorToast("至少需要输入一项信息")
                }
                if (!name.isNullOrBlank()) {
                    contactStr.appendLine("N:;$name")
                    contactStr.appendLine("FN:$name")
                }
                if (!company.isNullOrBlank()) {
                    contactStr.appendLine("ORG:$company")
                }
                if (!jobTitle.isNullOrBlank()) {
                    contactStr.appendLine("TITLE:$jobTitle")
                }
                if (!addr.isNullOrBlank()) {
                    contactStr.appendLine("ADR:;;$addr;;;;")
                }
                if (!mobilePhone.isNullOrBlank()) {
                    contactStr.appendLine("TEL;CELL:$mobilePhone")
                }
                if (!officePhone.isNullOrBlank()) {
                    contactStr.appendLine("TEL;WORK:$officePhone")
                }
                if (!homePhone.isNullOrBlank()) {
                    contactStr.appendLine("TEL;HOME:$homePhone")
                }
                if (!email.isNullOrBlank()) {
                    contactStr.appendLine("EMAIL:$email")
                }
                if (!url.isNullOrBlank()) {
                    contactStr.appendLine("URL:$url")
                }
                contactStr.appendLine("END:VCARD")
                dialog.dismiss()
            }
        }
    }

    private fun showCoordDialog(coordStr: StringBuilder) {
        val dlgCoordBd = DialogCoordBinding.inflate(layoutInflater)
        coordStr.append("GEO:")
        val dialog = MaterialAlertDialogBuilder(this).setView(dlgCoordBd.root).setTitle("坐标")
            .setPositiveButton("确认", null).setOnCancelListener {
                Log.d(TAG, "showCoordDialog: OnCancel")
                coordStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var ok = true
            if (dlgCoordBd.tietCoordLongitude.text.isNullOrBlank()) {
                dlgCoordBd.tilCoordLongitude.error = "经度不能为空"
                ok = false
            } else {
                dlgCoordBd.tilCoordLongitude.error = null
            }
            if (dlgCoordBd.tietCoordLatitude.text.isNullOrBlank()) {
                dlgCoordBd.tilCoordLatitude.error = "纬度不能为空"
                ok = false
            } else {
                dlgCoordBd.tilCoordLatitude.error = null
            }
            if (!ok) {
                return@setOnClickListener
            }
            coordStr.append("${dlgCoordBd.tietCoordLatitude.text},${dlgCoordBd.tietCoordLongitude.text}")
            dialog.dismiss()
        }
    }

    /**
     * 请求联系人权限并且执行相应任务
     * @param type
     * 0 - 用于联系人界面
     * 1 - 用于电话号码界面
     * 2 - 用于短信界面
     * 3 - 用于邮件界面
     */
    private fun reqContactPermAndExecTask(type: Int) {
        XXPermissions.with(this).permission(Permission.READ_CONTACTS)
            .request(object : OnPermissionCallback {
                override fun onGranted(
                    permissions: MutableList<String>, allGranted: Boolean
                ) {
                    when (type) {
                        0 -> contactLau.launch(null)
                        1 -> phoneLau.launch(null)
                        2 -> smsLau.launch(null)
                        3 -> emailLau.launch(null)
                    }
                }

                override fun onDenied(
                    permissions: MutableList<String>, doNotAskAgain: Boolean
                ) {
                    if (doNotAskAgain) {
                        showErrorToast("权限请求被永久拒绝，请在系统设置中手动授权\n本应用仅申请必要权限，请放心授权")
                        // 如果是被永久拒绝就跳转到应用权限系统设置页面
                        XXPermissions.startPermissionActivity(
                            this@GenerateCodeActivity, permissions
                        )
                    } else {
                        showErrorToast("权限请求被拒绝，请允许授予权限以正常使用此应用\n本应用仅申请必要权限，请放心授权")
                    }
                }
            })
    }

    override fun onResume() {
        super.onResume()
        readSettings()  // 读取设置
    }

    private fun readSettings() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        showCodeTypeInfo = sharedPreferences.getBoolean("show_code_type_info", true)
    }
}