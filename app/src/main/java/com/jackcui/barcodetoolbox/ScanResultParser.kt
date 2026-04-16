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

    fun parse(res: HmsScan): String {
        val scanType = res.scanType
        val scanTypeForm = res.scanTypeForm
        val newText = StringBuilder()

        appendScanType(scanType, newText)
        appendScanFormContent(scanTypeForm, res, newText)

        return newText.toString()
    }

    private fun appendScanType(scanType: Int, newText: StringBuilder) {
        when (scanType) {
            HmsScan.QRCODE_SCAN_TYPE -> newText.append("QR 码 - ")
            HmsScan.AZTEC_SCAN_TYPE -> newText.append("AZTEC 码 - ")
            HmsScan.DATAMATRIX_SCAN_TYPE -> newText.append("Data Matrix 码 - ")
            HmsScan.PDF417_SCAN_TYPE -> newText.append("PDF417 码 - ")
            HmsScan.CODE93_SCAN_TYPE -> newText.append("Code93 码 - ")
            HmsScan.CODE39_SCAN_TYPE -> newText.append("Code39 码 - ")
            HmsScan.CODE128_SCAN_TYPE -> newText.append("Code128 码 - ")
            HmsScan.EAN13_SCAN_TYPE -> newText.append("EAN13 码 - ")
            HmsScan.EAN8_SCAN_TYPE -> newText.append("EAN8 码 - ")
            HmsScan.ITF14_SCAN_TYPE -> newText.append("ITF14 码 - ")
            HmsScan.UPCCODE_A_SCAN_TYPE -> newText.append("UPC_A 码 - ")
            HmsScan.UPCCODE_E_SCAN_TYPE -> newText.append("UPC_E 码 - ")
            HmsScan.CODABAR_SCAN_TYPE -> newText.append("Codabar 码 - ")
            HmsScan.WX_SCAN_TYPE -> newText.append("微信码")
            HmsScan.MULTI_FUNCTIONAL_SCAN_TYPE -> newText.append("多功能码")
        }
    }

    private fun appendScanFormContent(scanTypeForm: Int, res: HmsScan, newText: StringBuilder) {
        when (scanTypeForm) {
            HmsScan.ARTICLE_NUMBER_FORM -> appendArticleNumber(res, newText)
            HmsScan.CONTACT_DETAIL_FORM -> appendContactDetail(res, newText)
            HmsScan.DRIVER_INFO_FORM -> appendDriverInfo(res, newText)
            HmsScan.EMAIL_CONTENT_FORM -> appendEmailContent(res, newText)
            HmsScan.EVENT_INFO_FORM -> appendEventInfo(res, newText)
            HmsScan.ISBN_NUMBER_FORM -> appendIsbnNumber(res, newText)
            HmsScan.LOCATION_COORDINATE_FORM -> appendLocationCoordinate(res, newText)
            HmsScan.PURE_TEXT_FORM -> appendPureText(res, newText)
            HmsScan.SMS_FORM -> appendSmsContent(res, newText)
            HmsScan.TEL_PHONE_NUMBER_FORM -> appendTelPhoneNumber(res, newText)
            HmsScan.URL_FORM -> appendUrl(res, newText)
            HmsScan.WIFI_CONNECT_INFO_FORM -> appendWifiConnectionInfo(res, newText)
            HmsScan.OTHER_FORM -> appendOtherForm(res, newText)
        }
    }

    private fun appendArticleNumber(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("产品信息：")
        newText.appendLine(res.originalValue)
    }

    private fun appendContactDetail(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("联系人：")
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
            newText.append("姓名： ")
            newText.appendLine(fullName)
        }
        if (!tels.isNullOrEmpty()) {
            newText.appendLine("电话：")
            for (tel in tels) {
                val row = ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                }
                when (tel.useType) {
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
                val phoneNum = tel.telPhoneNumber
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
                when (email.addressType) {
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
                when (addrInfo.addressType) {
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
            "新建联系人",
            "新建联系人",
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
            it.context.startActivity(itt)
        }
    }

    private fun appendDriverInfo(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("驾照信息：")
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

    private fun appendEmailContent(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("Email：")
        val email = res.emailContent
        val addrInfo = email.addressInfo
        val subjectInfo = email.subjectInfo
        val bodyInfo = email.bodyInfo.substringBeforeLast(";;")

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

        onSetFabAction(
            "发送邮件",
            "发送邮件",
            R.drawable.outline_email_24
        ) {
            val itt = Intent(Intent.ACTION_SENDTO)
            itt.setData(Uri.parse("mailto:$addrInfo"))
            itt.putExtra(Intent.EXTRA_SUBJECT, subjectInfo)
            itt.putExtra(Intent.EXTRA_TEXT, bodyInfo)
            try {
                it.context.startActivity(itt)
            } catch (e: ActivityNotFoundException) {
                showErrorToast("未检测到邮箱应用")
            }
        }
    }

    private fun appendEventInfo(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("日历事件：")
        val tmp = res.eventInfo
        val abstractInfo = tmp.abstractInfo
        val theme = tmp.theme
        val beginTimeInfo = tmp.beginTime
        val closeTimeInfo = tmp.closeTime
        val sponsor = tmp.sponsor
        val placeInfo = tmp.placeInfo
        val condition = tmp.condition

        if (!abstractInfo.isNullOrEmpty()) {
            newText.append("描述： ")
            newText.appendLine(abstractInfo)
        }
        if (!theme.isNullOrEmpty()) {
            newText.append("摘要： ")
            newText.appendLine(theme)
        }
        beginTimeInfo?.let {
            newText.append("开始时间： ")
            newText.appendLine(it.originalValue)
        }
        closeTimeInfo?.let {
            newText.append("结束时间： ")
            newText.appendLine(it.originalValue)
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

    private fun appendIsbnNumber(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("ISBN 号：")
        newText.appendLine(res.originalValue)
    }

    private fun appendLocationCoordinate(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("坐标：")
        val tmp = res.locationCoordinate
        val latitude = tmp.latitude
        val longitude = tmp.longitude

        newText.append("纬度： ")
        newText.appendLine(latitude)
        newText.append("经度： ")
        newText.appendLine(longitude)

        onSetFabAction(
            "打开地图并定位",
            "定位",
            R.drawable.outline_location_on_24
        ) {
            val uri = Uri.parse("geo:$latitude,$longitude")
            val itt = Intent(Intent.ACTION_VIEW, uri)
            try {
                it.context.startActivity(Intent.createChooser(itt, "选择一个地图应用以查看坐标位置"))
            } catch (e: ActivityNotFoundException) {
                showErrorToast("未检测到地图应用")
            }
        }
    }

    private fun appendPureText(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("文本：")
        newText.appendLine(res.originalValue)
    }

    private fun appendSmsContent(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("短信：")
        val tmp = res.smsContent
        val destPhoneNumber = tmp.destPhoneNumber
        val smsBody = tmp.msgContent

        if (!destPhoneNumber.isNullOrEmpty()) {
            newText.append("收信人： ")
            newText.appendLine(destPhoneNumber)
        }
        if (!smsBody.isNullOrEmpty()) {
            newText.append("内容： ")
            newText.appendLine(smsBody)
        }

        onSetFabAction(
            "发送短信",
            "发送短信",
            R.drawable.outline_sms_24
        ) {
            val uri = Uri.parse("smsto:$destPhoneNumber")
            val itt = Intent(Intent.ACTION_SENDTO, uri)
            itt.putExtra("sms_body", smsBody)
            it.context.startActivity(itt)
        }
    }

    private fun appendTelPhoneNumber(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("电话号码：")
        val tmp = res.telPhoneNumber

        when (tmp.useType) {
            TelPhoneNumber.CELLPHONE_NUMBER_USE_TYPE -> newText.append("手机： ")
            TelPhoneNumber.RESIDENTIAL_USE_TYPE -> newText.append("住家： ")
            TelPhoneNumber.OFFICE_USE_TYPE -> newText.append("办公： ")
            TelPhoneNumber.FAX_USE_TYPE -> newText.append("传真： ")
            TelPhoneNumber.OTHER_USE_TYPE -> newText.append("其他： ")
        }
        newText.appendLine(tmp.telPhoneNumber)

        onSetFabAction(
            "拨打电话",
            "拨打电话",
            R.drawable.baseline_phone_forwarded_24
        ) {
            val itt = Intent(Intent.ACTION_DIAL)
            itt.setData(Uri.parse("tel:${tmp.telPhoneNumber}"))
            it.context.startActivity(itt)
        }
    }

    private fun appendUrl(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("URL 链接：")
        val tmp = res.linkUrl
        val theme = tmp.theme
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

    private fun appendWifiConnectionInfo(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("Wi-Fi 信息：")
        val tmp = res.wiFiConnectionInfo
        val ssid = tmp.ssidNumber
        val pwd = tmp.password
        val cipherMode = tmp.cipherMode

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
            WiFiConnectionInfo.WPA_MODE_TYPE -> newText.appendLine("WPA/WPA2")
            WiFiConnectionInfo.WEP_MODE_TYPE -> newText.appendLine("WEP")
            WiFiConnectionInfo.NO_PASSWORD_MODE_TYPE -> newText.appendLine("开放")
            WiFiConnectionInfo.SAE_MODE_TYPE -> newText.appendLine("WPA3")
        }
        newText.append("隐藏： ")
        newText.appendLine(if (res.originalValue.contains("H:true", ignoreCase = true)) "是" else "否")

        onSetFabAction(
            "连接热点",
            "连接热点",
            R.drawable.baseline_wifi_find_24
        ) {
            val wifiHelper = WifiHelper(it.context)
            PermissionHelper.requestPermissions(
                it.context as androidx.fragment.app.FragmentActivity,
                PermissionHelper.PermissionConfig(
                    permissions = listOf(WifiHelper.PERMISSION),
                    explainReasonTitle = "连接热点必须使用以下权限"
                ),
                onGranted = { wifiHelper.connectWifi(ssid, pwd) }
            )
        }
    }

    private fun appendOtherForm(res: HmsScan, newText: StringBuilder) {
        newText.appendLine("未知类型信息：")
        newText.appendLine(res.originalValue)
    }
}
