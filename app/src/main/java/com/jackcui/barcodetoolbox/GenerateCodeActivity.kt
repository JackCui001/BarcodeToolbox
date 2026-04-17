package com.jackcui.barcodetoolbox

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
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.jackcui.barcodetoolbox.MainActivity.Companion.TAG
import com.jackcui.barcodetoolbox.MainActivity.Companion.showErrorToast
import com.jackcui.barcodetoolbox.MainActivity.Companion.showInfoToast
import com.jackcui.barcodetoolbox.databinding.ActivityGenerateCodeBinding
import com.jackcui.barcodetoolbox.databinding.DialogContactBinding
import com.jackcui.barcodetoolbox.databinding.DialogCoordBinding
import com.jackcui.barcodetoolbox.databinding.DialogEmailBinding
import com.jackcui.barcodetoolbox.databinding.DialogPhoneBinding
import com.jackcui.barcodetoolbox.databinding.DialogSmsBinding
import com.jackcui.barcodetoolbox.databinding.DialogWifiBinding
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
    private var dlgWifiBd: DialogWifiBinding? = null
    private var dlgCoordBd: DialogCoordBinding? = null

    enum class TaskType {
        CONTACT,
        PHONE,
        SMS,
        EMAIL
    }

    private val convertInfoDismissCallback = DialogInterface.OnDismissListener {
        if (res.isNotBlank()) {
            bd.tietGenContent.setText(res)
            showInfoToast(getString(R.string.toast_info_convert_success))
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
                val contactHelper = ContactHelper(this@GenerateCodeActivity)
                val contactInfo = contactHelper.getContactInfo(uri)
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
                        tilContactHomePhone.helperText = getString(R.string.helper_import_count, phoneNumberCnt)
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
                        tilContactEmail.helperText = getString(R.string.helper_import_email_count, emailCnt)
                        actvContactEmail.setSimpleItems(
                            it.emails.toTypedArray()
                        )
                    } else {
                        tilContactEmail.helperText = null
                    }

                    val websiteCnt = it.websites.size
                    if (websiteCnt > 0) {
                        tilContactWebsite.helperText = getString(R.string.helper_import_website_count, websiteCnt)
                        actvContactWebsite.setSimpleItems(
                            it.websites.toTypedArray()
                        )
                    } else {
                        tilContactWebsite.helperText = null
                    }

                    val addressCnt = it.addresses.size
                    if (addressCnt > 0) {
                        tilContactAddr.helperText = getString(R.string.helper_import_addr_count, addressCnt)
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
                val contactHelper = ContactHelper(this@GenerateCodeActivity)
                val contactInfo = contactHelper.getContactInfo(it)
                contactInfo?.let {
                    val phoneNumberCnt = it.phoneNumbers.size
                    if (phoneNumberCnt > 0) {
                        tilPhoneNumber.helperText = getString(R.string.helper_import_count, phoneNumberCnt)
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
                val contactHelper = ContactHelper(this@GenerateCodeActivity)
                val contactInfo = contactHelper.getContactInfo(it)
                contactInfo?.let {
                    val phoneNumberCnt = it.phoneNumbers.size
                    if (phoneNumberCnt > 0) {
                        tilSmsPhone.helperText = getString(R.string.helper_import_count, phoneNumberCnt)
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
                val contactHelper = ContactHelper(this@GenerateCodeActivity)
                val contactInfo = contactHelper.getContactInfo(it)
                contactInfo?.let {
                    val emailCnt = it.emails.size
                    if (emailCnt > 0) {
                        tilEmailAddr.helperText = getString(R.string.helper_import_email_addr_count, emailCnt)
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

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding
        bd = ActivityGenerateCodeBinding.inflate(layoutInflater)
        setContentView(bd.root)

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

        bd.tvLenInd.text = getString(R.string.unit_byte_available, "N/A")
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
                bd.tvLenInd.text = getString(R.string.unit_byte_available, (maxBytes[selectedItemIdx] - s.toString().encodeToByteArray().size).toString())
            }
        })

        bd.actvCodeType.setOnItemClickListener { _, _, position, _ ->
            selectedItemIdx = position
            bd.tvLenInd.text = getString(R.string.unit_byte_available, (maxBytes[selectedItemIdx] - bd.tietGenContent.text.toString()
                    .encodeToByteArray().size).toString())
            if (showCodeTypeInfo) {
                MaterialAlertDialogBuilder(this@GenerateCodeActivity).setTitle(
                    codeTypeStrArr[selectedItemIdx]
                ).setMessage(hints[selectedItemIdx])
                    .setPositiveButton(getString(R.string.btn_confirm)) { dialog, _ -> dialog.dismiss() }.show()
            }
        }

        bd.btnSpInfoConvert.setOnClickListener {
            val items = arrayOf("Wi-Fi", "Email", getString(R.string.dialog_title_contact), getString(R.string.dialog_title_phone), getString(R.string.dialog_title_sms), getString(R.string.dialog_title_coord))

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_title_select_type))
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> showWifiDialog(res)
                        1 -> showEmailDialog(res)
                        2 -> showContactDialog(res)
                        3 -> showPhoneDialog(res)
                        4 -> showSmsDialog(res)
                        5 -> showCoordDialog(res)
                    }
                }
                .show()
        }

        bd.btnGenCode.setOnClickListener {
            val codeTypeText = bd.actvCodeType.text
            if (codeTypeText.isNullOrEmpty()) {
                showErrorToast(getString(R.string.toast_error_no_code_type))
                return@setOnClickListener
            }
            val lenIndicatorText = bd.tvLenInd.text
            val availableBytes = try {
                val text = bd.tvLenInd.text.toString()
                // Parsing depends on format, safer to just re-calculate or use a regex
                val match = Regex("\\d+").find(text)
                match?.value?.toInt() ?: 0
            } catch (e: Exception) { 0 }
            if (availableBytes < 0) {
                showErrorToast(getString(R.string.toast_error_over_limit))
                return@setOnClickListener
            }
            val content = bd.tietGenContent.text
            if (content.isNullOrEmpty()) {
                showErrorToast(getString(R.string.toast_error_empty_content))
                return@setOnClickListener
            }
            val type = codeType[selectedItemIdx]
            val width = 400
            val height = 400
            try {
                codeBitmap = ScanUtil.buildBitmap(content.toString(), type, width, height, null)
                bd.ivCodePreview.setImageBitmap(codeBitmap)
                showInfoToast(getString(R.string.toast_info_preview_success))
                codePreviewIdx = selectedItemIdx
            } catch (e: Exception) {
                e.printStackTrace()
                showErrorToast(getString(R.string.toast_error_exception))
            }
        }

        bd.ivCodePreview.setOnLongClickListener {
            if (codeBitmap == null) {
                showErrorToast(getString(R.string.toast_error_need_preview))
                return@setOnLongClickListener true
            }
            reqPermAndMakeFile()
            true
        }
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
            "${Environment.getExternalStorageDirectory().path}${File.separator}${Environment.DIRECTORY_PICTURES}${File.separator}$TAG"
        val filePathAndName = "$filePath${File.separator}$filename"
        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}${File.separator}$TAG"
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
            showInfoToast(getString(R.string.toast_info_save_success, filePathAndName))
        } else {
            showErrorToast(getString(R.string.toast_error_save_failed))
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
        dlgWifiBd = DialogWifiBinding.inflate(layoutInflater)
        wifiStr.append("WIFI:")
        val dialog = MaterialAlertDialogBuilder(this).setView(dlgWifiBd!!.root).setTitle(getString(R.string.dialog_title_wifi))
            .setPositiveButton(getString(R.string.btn_confirm), null).setNeutralButton(getString(R.string.btn_search_nearby), null)
            .setOnCancelListener {
                Log.d(TAG, "showWifiDialog: OnCancel")
                wifiStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dlgWifiBd?.run {
            actvWifiCipher.setOnItemClickListener { _, _, position, _ ->
                cipherText = cipherArr[position]
                if (cipherText == getString(R.string.helper_wifi_no_pwd)) {
                    tietWifiPwd.setText("")
                    tilWifiPwd.helperText = getString(R.string.helper_wifi_no_pwd)
                    tilWifiPwd.isEnabled = false
                } else {
                    tilWifiPwd.helperText = null
                    tilWifiPwd.isEnabled = true
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dlgWifiBd?.run {
                var ok = true
                if (actvWifiSsid.text.isNullOrBlank()) {
                    tilWifiSsid.error = getString(R.string.error_field_required_ssid)
                    // Wait, I didn't add validation strings. I'll use common ones if available.
                    // For now I'll just keep it or add them. I'll add them to strings.xml later.
                    ok = false
                } else {
                    tilWifiSsid.error = null
                }
                if (tilWifiPwd.isEnabled && tietWifiPwd.text.isNullOrBlank()) {
                    tilWifiPwd.error = getString(R.string.error_field_required_pwd)
                    ok = false
                } else {
                    tilWifiPwd.error = null
                }
                if (actvWifiCipher.text.isNullOrEmpty()) {
                    tilWifiCipher.error = getString(R.string.error_field_required_cipher)
                    ok = false
                } else {
                    tilWifiCipher.error = null
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
                wifiStr.append("S:${actvWifiSsid.text};")
                if (tilWifiPwd.isEnabled) {
                    wifiStr.append("P:${tietWifiPwd.text};")
                }
                wifiStr.append("H:${cbWifiHidden.isChecked};")
                wifiStr.append(";")
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            scanWifi()
        }
    }

    private fun showEmailDialog(emailStr: StringBuilder) {
        dlgEmailBd = DialogEmailBinding.inflate(layoutInflater)
        emailStr.append("MATMSG:")
        val dialog = MaterialAlertDialogBuilder(this).setView(dlgEmailBd!!.root).setTitle(getString(R.string.dialog_title_email))
            .setPositiveButton(getString(R.string.btn_confirm), null).setNeutralButton(getString(R.string.btn_import_contact), null)
            .setOnCancelListener {
                Log.d(TAG, "showEmailDialog: OnCancel")
                emailStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dlgEmailBd?.run {
                var ok = true
                if (actvEmailAddr.text.isNullOrBlank()) {
                    tilEmailAddr.error = getString(R.string.error_field_required_email)
                    ok = false
                } else {
                    tilEmailAddr.error = null
                }
                if (tietEmailSubject.text.isNullOrBlank()) {
                    tilEmailSubject.error = getString(R.string.error_field_required_subject)
                    ok = false
                } else {
                    tilEmailSubject.error = null
                }
                if (tietEmailContent.text.isNullOrBlank()) {
                    tilEmailContent.error = getString(R.string.error_field_required_content)
                    ok = false
                } else {
                    tilEmailContent.error = null
                }
                if (!ok) {
                    return@setOnClickListener
                }
                emailStr.append("TO:${actvEmailAddr.text};SUB:${tietEmailSubject.text};BODY:${tietEmailContent.text};;")
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            execContactTask(TaskType.EMAIL)
        }
    }

    private fun showPhoneDialog(phoneStr: StringBuilder) {
        dlgPhoneBd = DialogPhoneBinding.inflate(layoutInflater)
        phoneStr.append("TEL:")
        val dialog =
            MaterialAlertDialogBuilder(this).setView(dlgPhoneBd!!.root).setTitle(getString(R.string.dialog_title_phone))
                .setPositiveButton(getString(R.string.btn_confirm), null).setNeutralButton(getString(R.string.btn_import_contact), null)
                .setOnCancelListener {
                    Log.d(TAG, "showPhoneDialog: OnCancel")
                    phoneStr.clear()
                }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dlgPhoneBd?.run {
                if (actvPhoneNumber.text.isNullOrBlank()) {
                    tilPhoneNumber.error = getString(R.string.error_field_required_phone)
                    return@setOnClickListener
                }
                phoneStr.append("${actvPhoneNumber.text}")
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            execContactTask(TaskType.PHONE)
        }
    }

    private fun showSmsDialog(smsStr: StringBuilder) {
        dlgSmsBd = DialogSmsBinding.inflate(layoutInflater)
        smsStr.append("SMSTO:")
        val dialog = MaterialAlertDialogBuilder(this).setView(dlgSmsBd!!.root).setTitle(getString(R.string.dialog_title_sms))
            .setPositiveButton(getString(R.string.btn_confirm), null).setNeutralButton(getString(R.string.btn_import_contact), null)
            .setOnCancelListener {
                Log.d(TAG, "showSmsDialog: OnCancel")
                smsStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dlgSmsBd?.run {
                var ok = true
                if (actvSmsPhone.text.isNullOrBlank()) {
                    tilSmsPhone.error = getString(R.string.error_field_required_email) // Wait, SMS phone or email? R.string.error_field_required_phone was better.
                    // But checking back, it said SMS phone to use R.string.hint_sms_phone.
                    tilSmsPhone.error = getString(R.string.error_field_required_phone)
                    ok = false
                } else {
                    tilSmsPhone.error = null
                }
                if (tietSmsContent.text.isNullOrBlank()) {
                    tilSmsContent.error = getString(R.string.error_field_required_content)
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
            execContactTask(TaskType.SMS)
        }
    }

    private fun showContactDialog(contactStr: StringBuilder) {
        dlgContactBd = DialogContactBinding.inflate(layoutInflater)
        contactStr.appendLine("BEGIN:VCARD\nVERSION:3.0")
        val dialog =
            MaterialAlertDialogBuilder(this).setView(dlgContactBd!!.root).setTitle(getString(R.string.dialog_title_contact))
                .setPositiveButton(getString(R.string.btn_confirm), null).setNeutralButton(getString(R.string.btn_import_contact), null)
                .setOnCancelListener {
                    Log.d(TAG, "showContactDialog: OnCancel")
                    contactStr.clear()
                }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            execContactTask(TaskType.CONTACT)
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
                    showErrorToast(getString(R.string.toast_error_at_least_one))
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
        dlgCoordBd = DialogCoordBinding.inflate(layoutInflater)
        coordStr.append("GEO:")
        val dialog = MaterialAlertDialogBuilder(this).setView(dlgCoordBd!!.root).setTitle(getString(R.string.dialog_title_coord))
            .setPositiveButton(getString(R.string.btn_confirm), null).setNeutralButton(getString(R.string.btn_location), null).setOnCancelListener {
                Log.d(TAG, "showCoordDialog: OnCancel")
                coordStr.clear()
            }.setOnDismissListener(convertInfoDismissCallback).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dlgCoordBd?.run {
                var ok = true
                if (tietCoordLongitude.text.isNullOrBlank()) {
                    tilCoordLongitude.error = getString(R.string.error_field_required_longitude)
                    ok = false
                } else {
                    tilCoordLongitude.error = null
                }
                if (tietCoordLatitude.text.isNullOrBlank()) {
                    tilCoordLatitude.error = getString(R.string.error_field_required_latitude)
                    ok = false
                } else {
                    tilCoordLatitude.error = null
                }
                if (!ok) {
                    return@setOnClickListener
                }
                coordStr.append("${tietCoordLatitude.text},${tietCoordLongitude.text}")
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            PermissionHelper.requestPermissions(
                this,
                PermissionHelper.PermissionConfig(
                    permissions = listOf(LocationHelper.PERMISSION),
                    explainReasonTitle = getString(R.string.perm_loc_coord)
                ),
                onGranted = {
                    dlgCoordBd?.run {
                        tilCoordLongitude.helperText = getString(R.string.helper_locating)
                        val locationHelper = LocationHelper(this@GenerateCodeActivity)
                        locationHelper.getLocationInfo {
                            tilCoordLongitude.helperText = getString(R.string.helper_location_success)
                            tietCoordLatitude.setText(it.latitude.toString())
                            tietCoordLongitude.setText(it.longitude.toString())
                        }
                    }
                }
            )
        }
    }

    /**
     * 请求写存储权限，并保存二维码图片
     */
    private fun reqPermAndMakeFile() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        PermissionHelper.requestPermissions(
            this,
            PermissionHelper.PermissionConfig(
                permissions = listOf(permission),
                explainReasonTitle = getString(R.string.perm_save_pic)
            ),
            onGranted = { writeCodePicFile() }
        )
    }

    /**
     * 申请联系人权限，执行相应任务
     * @param type
     * 详情见 enum class TaskType
     */
    private fun execContactTask(type: TaskType) {
        PermissionHelper.requestPermissions(
            this,
            PermissionHelper.PermissionConfig(
                permissions = listOf(ContactHelper.PERMISSION),
                explainReasonTitle = getString(R.string.perm_contact)
            ),
            onGranted = {
                when (type) {
                    TaskType.CONTACT -> contactLau.launch(null)
                    TaskType.PHONE -> phoneLau.launch(null)
                    TaskType.SMS -> smsLau.launch(null)
                    TaskType.EMAIL -> emailLau.launch(null)
                }
            }
        )
    }

    /**
     * 申请位置权限，扫描周围热点
     */
    private fun scanWifi() {
        PermissionHelper.requestPermissions(
            this,
            PermissionHelper.PermissionConfig(
                permissions = listOf(WifiHelper.PERMISSION),
                explainReasonTitle = getString(R.string.perm_loc_hotspot)
            ),
            onGranted = {
                val wifiHelper = WifiHelper(this@GenerateCodeActivity)
                val ssidMap = wifiHelper.scanWifiAndGetInfo()
                if (ssidMap.isNotEmpty()) {
                    dlgWifiBd?.run {
                        val ssids = ssidMap.keys.toTypedArray()
                        (actvWifiSsid as MaterialAutoCompleteTextView).setSimpleItems(ssids)
                        tilWifiSsid.helperText = getString(R.string.helper_search_wifi_count, ssids.size)
                    }
                }
            }
        )
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