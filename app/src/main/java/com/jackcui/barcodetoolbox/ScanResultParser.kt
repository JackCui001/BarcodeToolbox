package com.jackcui.barcodetoolbox

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Intents.Insert
import android.view.View
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScan.AddressInfo
import com.huawei.hms.ml.scan.HmsScan.EmailContent
import com.huawei.hms.ml.scan.HmsScan.TelPhoneNumber
import com.huawei.hms.ml.scan.HmsScan.WiFiConnectionInfo
import com.jackcui.barcodetoolbox.MainActivity.Companion.showErrorToast

class ScanResultParser(
    private val onSetFabAction: (String, String, Int, View.OnClickListener) -> Unit
) {

    fun parse(context: android.content.Context, res: HmsScan): String {
        val scanType = res.scanType
        val scanTypeForm = res.scanTypeForm
        val newText = StringBuilder()

        appendScanType(context, scanType, newText)
        appendScanFormContent(context, scanTypeForm, res, newText)

        return newText.toString()
    }

    private fun appendScanType(context: android.content.Context, scanType: Int, newText: StringBuilder) {
        when (scanType) {
            HmsScan.QRCODE_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_qr) + " - ")
            HmsScan.AZTEC_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_aztec) + " - ")
            HmsScan.DATAMATRIX_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_datamatrix) + " - ")
            HmsScan.PDF417_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_pdf417) + " - ")
            HmsScan.CODE93_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_code93) + " - ")
            HmsScan.CODE39_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_code39) + " - ")
            HmsScan.CODE128_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_code128) + " - ")
            HmsScan.EAN13_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_ean13) + " - ")
            HmsScan.EAN8_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_ean8) + " - ")
            HmsScan.ITF14_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_itf14) + " - ")
            HmsScan.UPCCODE_A_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_upc_a) + " - ")
            HmsScan.UPCCODE_E_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_upc_e) + " - ")
            HmsScan.CODABAR_SCAN_TYPE -> newText.append(context.getString(R.string.label_scan_type_codabar) + " - ")
            HmsScan.WX_SCAN_TYPE -> newText.append(context.getString(R.string.scan_type_wx))
            HmsScan.MULTI_FUNCTIONAL_SCAN_TYPE -> newText.append(context.getString(R.string.scan_type_multi))
        }
    }

    private fun appendScanFormContent(context: android.content.Context, scanTypeForm: Int, res: HmsScan, newText: StringBuilder) {
        when (scanTypeForm) {
            HmsScan.ARTICLE_NUMBER_FORM -> appendArticleNumber(context, res, newText)
            HmsScan.CONTACT_DETAIL_FORM -> appendContactDetail(context, res, newText)
            HmsScan.DRIVER_INFO_FORM -> appendDriverInfo(context, res, newText)
            HmsScan.EMAIL_CONTENT_FORM -> appendEmailContent(context, res, newText)
            HmsScan.EVENT_INFO_FORM -> appendEventInfo(context, res, newText)
            HmsScan.ISBN_NUMBER_FORM -> appendIsbnNumber(context, res, newText)
            HmsScan.LOCATION_COORDINATE_FORM -> appendLocationCoordinate(context, res, newText)
            HmsScan.PURE_TEXT_FORM -> appendPureText(context, res, newText)
            HmsScan.SMS_FORM -> appendSmsContent(context, res, newText)
            HmsScan.TEL_PHONE_NUMBER_FORM -> appendTelPhoneNumber(context, res, newText)
            HmsScan.URL_FORM -> appendUrl(context, res, newText)
            HmsScan.WIFI_CONNECT_INFO_FORM -> appendWifiConnectionInfo(context, res, newText)
            HmsScan.OTHER_FORM -> appendOtherForm(context, res, newText)
        }
    }

    private fun appendArticleNumber(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_product))
        newText.appendLine(res.originalValue)
    }

    private fun appendContactDetail(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_contact))
        val tmp = res.contactDetail
        val peopleName = tmp.peopleName
        val tels = tmp.telPhoneNumbers
        val emailContentList = tmp.emailContents
        val contactLinks = tmp.contactLinks
        val company = tmp.company
        val title = tmp.title
        val addrInfoList = tmp.addressesInfos
        val note = tmp.note
        val data = arrayListOf<ContentValues>()

        peopleName?.run {
            newText.append(context.getString(R.string.hint_name) + "： ")
            newText.appendLine(fullName)
        }
        if (!tels.isNullOrEmpty()) {
            newText.appendLine(context.getString(R.string.label_phone) + "：")
            for (tel in tels) {
                val row = ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                }
                when (tel.useType) {
                    TelPhoneNumber.CELLPHONE_NUMBER_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_tel_cell))
                        row.put(Phone.TYPE, Phone.TYPE_MOBILE)
                    }
                    TelPhoneNumber.RESIDENTIAL_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_tel_home))
                        row.put(Phone.TYPE, Phone.TYPE_HOME)
                    }
                    TelPhoneNumber.OFFICE_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_tel_work))
                        row.put(Phone.TYPE, Phone.TYPE_WORK)
                    }
                    TelPhoneNumber.FAX_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_tel_fax))
                        row.put(Phone.TYPE, Phone.TYPE_FAX_WORK)
                    }
                    TelPhoneNumber.OTHER_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_tel_other))
                        row.put(Phone.TYPE, Phone.TYPE_OTHER)
                    }
                }
                val phoneNum = tel.telPhoneNumber
                newText.appendLine(phoneNum)
                row.put(Phone.NUMBER, phoneNum)
                data.add(row)
            }
        }
        if (!emailContentList.isNullOrEmpty()) {
            newText.appendLine(context.getString(R.string.hint_email) + "： ")
            for (email in emailContentList) {
                val row = ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                }
                when (email.addressType) {
                    EmailContent.RESIDENTIAL_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_email_home))
                        row.put(Email.TYPE, Email.TYPE_HOME)
                    }
                    EmailContent.OFFICE_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_email_work))
                        row.put(Email.TYPE, Email.TYPE_WORK)
                    }
                    EmailContent.OTHER_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_email_other))
                        row.put(Email.TYPE, Email.TYPE_OTHER)
                    }
                }
                val emailAddr = email.addressInfo
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
            newText.append(context.getString(R.string.hint_company) + "： ")
            newText.appendLine(company)
        }
        if (!title.isNullOrEmpty()) {
            newText.append(context.getString(R.string.hint_job_title) + "： ")
            newText.appendLine(title)
        }
        if (!addrInfoList.isNullOrEmpty()) {
            newText.appendLine(context.getString(R.string.hint_address) + "：")
            for (addrInfo in addrInfoList) {
                val row = ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                }
                when (addrInfo.addressType) {
                    AddressInfo.RESIDENTIAL_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_pos_home))
                        row.put(StructuredPostal.TYPE, StructuredPostal.TYPE_HOME)
                    }
                    AddressInfo.OFFICE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_pos_work))
                        row.put(StructuredPostal.TYPE, StructuredPostal.TYPE_WORK)
                    }
                    AddressInfo.OTHER_USE_TYPE -> {
                        newText.append("  " + context.getString(R.string.label_pos_other))
                        row.put(StructuredPostal.TYPE, StructuredPostal.TYPE_OTHER)
                    }
                }
                val addrStr = addrInfo.addressDetails.toList().joinToString()
                newText.appendLine(addrStr)
                row.put(StructuredPostal.FORMATTED_ADDRESS, addrStr)
                data.add(row)
            }
        }
        if (!note.isNullOrEmpty()) {
            newText.append("备注： ")
            newText.appendLine(note)
        }

        onSetFabAction(
            context.getString(R.string.action_add_contact),
            context.getString(R.string.action_add_contact),
            R.drawable.outline_person_add_alt_24
        ) {
            val itt = Intent(Insert.ACTION, ContactsContract.Contacts.CONTENT_URI)
            peopleName?.run {
                itt.putExtra(Insert.NAME, fullName)
            }
            if (!company.isNullOrEmpty()) {
                itt.putExtra(Insert.COMPANY, company)
            }
            if (!title.isNullOrEmpty()) {
                itt.putExtra(Insert.JOB_TITLE, title)
            }
            itt.putParcelableArrayListExtra(Insert.DATA, data)
            if (!note.isNullOrEmpty()) {
                itt.putExtra(Insert.NOTES, note)
            }
            context.startActivity(itt)
        }
    }

    private fun appendDriverInfo(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_driver))
        val tmp = res.driverInfo
        val familyName = tmp.familyName
        val middleName = tmp.middleName
        val givenName = tmp.givenName
        val sex = tmp.sex
        val dateOfBirth = tmp.dateOfBirth
        val countryOfIssue = tmp.countryOfIssue
        val certType = tmp.certificateType
        val certNum = tmp.certificateNumber
        val dateOfIssue = tmp.dateOfIssue
        val dateOfExpire = tmp.dateOfExpire
        val province = tmp.province
        val city = tmp.city
        val avenue = tmp.avenue
        val zipCode = tmp.zipCode

        if (!familyName.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_family_name))
            newText.appendLine(familyName)
        }
        if (!middleName.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_middle_name))
            newText.appendLine(middleName)
        }
        if (!givenName.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_given_name))
            newText.appendLine(givenName)
        }
        if (!sex.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_sex))
            newText.appendLine(sex)
        }
        if (!dateOfBirth.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_birth))
            newText.appendLine(dateOfBirth)
        }
        if (!countryOfIssue.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_country))
            newText.appendLine(countryOfIssue)
        }
        if (!certType.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_type))
            newText.appendLine(certType)
        }
        if (!certNum.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_num))
            newText.appendLine(certNum)
        }
        if (!dateOfIssue.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_issue))
            newText.appendLine(dateOfIssue)
        }
        if (!dateOfExpire.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_expire))
            newText.appendLine(dateOfExpire)
        }
        if (!province.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_province))
            newText.appendLine(province)
        }
        if (!city.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_city))
            newText.appendLine(city)
        }
        if (!avenue.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_avenue))
            newText.appendLine(avenue)
        }
        if (!zipCode.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_driver_zip))
            newText.appendLine(zipCode)
        }
    }

    private fun appendEmailContent(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_email))
        val email = res.emailContent
        val addrInfo = email.addressInfo
        val subjectInfo = email.subjectInfo
        val bodyInfo = email.bodyInfo.substringBeforeLast(";;")

        if (!addrInfo.isNullOrEmpty()) {
            newText.append(context.getString(R.string.hint_email_addr) + "： ")
            newText.appendLine(addrInfo)
        }
        if (!subjectInfo.isNullOrEmpty()) {
            newText.append(context.getString(R.string.hint_email_subject) + "： ")
            newText.appendLine(subjectInfo)
        }
        if (bodyInfo.isNotEmpty()) {
            newText.append(context.getString(R.string.hint_content) + "： ")
            newText.appendLine(bodyInfo)
        }

        onSetFabAction(
            context.getString(R.string.action_send_email),
            context.getString(R.string.action_send_email),
            R.drawable.outline_email_24
        ) {
            val itt = Intent(Intent.ACTION_SENDTO)
            itt.setData(Uri.parse("mailto:$addrInfo"))
            itt.putExtra(Intent.EXTRA_SUBJECT, subjectInfo)
            itt.putExtra(Intent.EXTRA_TEXT, bodyInfo)
            try {
                context.startActivity(itt)
            } catch (e: ActivityNotFoundException) {
                showErrorToast(context.getString(R.string.toast_error_no_email_app))
            }
        }
    }

    private fun appendEventInfo(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_event))
        val tmp = res.eventInfo
        val abstractInfo = tmp.abstractInfo
        val theme = tmp.theme
        val beginTimeInfo = tmp.beginTime
        val closeTimeInfo = tmp.closeTime
        val sponsor = tmp.sponsor
        val placeInfo = tmp.placeInfo
        val condition = tmp.condition

        if (!abstractInfo.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_event_desc))
            newText.appendLine(abstractInfo)
        }
        if (!theme.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_event_summary))
            newText.appendLine(theme)
        }
        beginTimeInfo?.let {
            newText.append(context.getString(R.string.label_event_start))
            newText.appendLine(it.originalValue)
        }
        closeTimeInfo?.let {
            newText.append(context.getString(R.string.label_event_end))
            newText.appendLine(it.originalValue)
        }
        if (!sponsor.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_event_organizer))
            newText.appendLine(sponsor)
        }
        if (!placeInfo.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_event_place))
            newText.appendLine(placeInfo)
        }
        if (!condition.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_event_status))
            newText.appendLine(condition)
        }
    }

    private fun appendIsbnNumber(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_isbn))
        newText.appendLine(res.originalValue)
    }

    private fun appendLocationCoordinate(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_location))
        val tmp = res.locationCoordinate
        val latitude = tmp.latitude
        val longitude = tmp.longitude

        newText.append(context.getString(R.string.hint_latitude) + "： ")
        newText.appendLine(latitude)
        newText.append(context.getString(R.string.hint_longitude) + "： ")
        newText.appendLine(longitude)

        onSetFabAction(
            context.getString(R.string.action_open_map),
            context.getString(R.string.btn_location),
            R.drawable.outline_location_on_24
        ) {
            val uri = Uri.parse("geo:$latitude,$longitude")
            val itt = Intent(Intent.ACTION_VIEW, uri)
            try {
                context.startActivity(Intent.createChooser(itt, context.getString(R.string.select_map_app)))
            } catch (e: ActivityNotFoundException) {
                showErrorToast(context.getString(R.string.toast_error_no_map_app))
            }
        }
    }

    private fun appendPureText(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_text))
        newText.appendLine(res.originalValue)
    }

    private fun appendSmsContent(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_sms))
        val tmp = res.smsContent
        val destPhoneNumber = tmp.destPhoneNumber
        val smsBody = tmp.msgContent

        if (!destPhoneNumber.isNullOrEmpty()) {
            newText.append(context.getString(R.string.hint_sms_phone) + "： ")
            newText.appendLine(destPhoneNumber)
        }
        if (!smsBody.isNullOrEmpty()) {
            newText.append(context.getString(R.string.hint_content) + "： ")
            newText.appendLine(smsBody)
        }

        onSetFabAction(
            context.getString(R.string.action_send_sms),
            context.getString(R.string.action_send_sms),
            R.drawable.outline_sms_24
        ) {
            val uri = Uri.parse("smsto:$destPhoneNumber")
            val itt = Intent(Intent.ACTION_SENDTO, uri)
            itt.putExtra("sms_body", smsBody)
            context.startActivity(itt)
        }
    }

    private fun appendTelPhoneNumber(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_phone))
        val tmp = res.telPhoneNumber

        when (tmp.useType) {
            TelPhoneNumber.CELLPHONE_NUMBER_USE_TYPE -> newText.append(context.getString(R.string.label_tel_cell))
            TelPhoneNumber.RESIDENTIAL_USE_TYPE -> newText.append(context.getString(R.string.label_tel_home))
            TelPhoneNumber.OFFICE_USE_TYPE -> newText.append(context.getString(R.string.label_tel_work))
            TelPhoneNumber.FAX_USE_TYPE -> newText.append(context.getString(R.string.label_tel_fax))
            TelPhoneNumber.OTHER_USE_TYPE -> newText.append(context.getString(R.string.label_tel_other))
        }
        newText.appendLine(tmp.telPhoneNumber)

        onSetFabAction(
            context.getString(R.string.action_dial),
            context.getString(R.string.action_dial),
            R.drawable.baseline_phone_forwarded_24
        ) {
            val itt = Intent(Intent.ACTION_DIAL)
            itt.setData(Uri.parse("tel:${tmp.telPhoneNumber}"))
            context.startActivity(itt)
        }
    }

    private fun appendUrl(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_url))
        val tmp = res.linkUrl
        val theme = tmp.theme
        val linkValue = tmp.linkValue

        if (!theme.isNullOrEmpty()) {
            newText.append(context.getString(R.string.hint_email_subject) + "： ")
            newText.appendLine(theme)
        }
        if (!linkValue.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_form_url) + "： ")
            newText.appendLine(linkValue)
        }
    }

    private fun appendWifiConnectionInfo(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_wifi))
        val tmp = res.wiFiConnectionInfo
        val ssid = tmp.ssidNumber
        val pwd = tmp.password
        val cipherMode = tmp.cipherMode

        if (!ssid.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_wifi_ssid))
            newText.appendLine(ssid)
        }
        if (!pwd.isNullOrEmpty()) {
            newText.append(context.getString(R.string.label_wifi_pwd))
            newText.appendLine(pwd)
        }
        newText.append(context.getString(R.string.label_wifi_cipher))
        when (cipherMode) {
            WiFiConnectionInfo.WPA_MODE_TYPE -> newText.appendLine("WPA/WPA2")
            WiFiConnectionInfo.WEP_MODE_TYPE -> newText.appendLine("WEP")
            WiFiConnectionInfo.NO_PASSWORD_MODE_TYPE -> newText.appendLine(context.getString(R.string.wifi_no_password))
            WiFiConnectionInfo.SAE_MODE_TYPE -> newText.appendLine("WPA3")
        }
        newText.append(context.getString(R.string.label_wifi_hidden_status))
        newText.appendLine(if (res.originalValue.contains("H:true", ignoreCase = true)) context.getString(R.string.yes) else context.getString(R.string.no))

        onSetFabAction(
            context.getString(R.string.action_connect_wifi),
            context.getString(R.string.action_connect_wifi),
            R.drawable.baseline_wifi_find_24
        ) {
            val wifiHelper = WifiHelper(context)
            PermissionHelper.requestPermissions(
                context as androidx.fragment.app.FragmentActivity,
                PermissionHelper.PermissionConfig(
                    permissions = listOf(WifiHelper.PERMISSION),
                    explainReasonTitle = context.getString(R.string.perm_wifi_hotspot)
                ),
                onGranted = { wifiHelper.connectWifi(ssid, pwd) }
            )
        }
    }

    private fun appendOtherForm(context: android.content.Context, res: HmsScan, newText: StringBuilder) {
        newText.appendLine(context.getString(R.string.label_form_unknown))
        newText.appendLine(res.originalValue)
    }
}
