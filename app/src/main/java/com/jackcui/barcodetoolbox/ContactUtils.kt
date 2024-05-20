package com.jackcui.barcodetoolbox

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.database.getStringOrNull
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

class ContactUtils(context: Context) {
    data class ContactInfo(
        var name: String = "",
        var phoneNumbers: MutableSet<String> = mutableSetOf(),
        var emails: MutableSet<String> = mutableSetOf(),
        var websites: MutableSet<String> = mutableSetOf(),
        var addresses: MutableSet<String> = mutableSetOf(),
        var company: String = "",
        var jobTitle: String = ""
    )

    companion object {
        const val TAG = "ContactUtils"
        val PERMISSION = arrayOf(Permission.READ_CONTACTS)
    }

    private val mContext = context

    /**
     * 遍历Uri指定的联系人信息
     */
    fun getContactInfo(contactUri: Uri): ContactInfo? {
        val bGranted = XXPermissions.isGranted(mContext, PERMISSION)
        if (!bGranted) {
            return null
        }
        var contactInfo: ContactInfo? = null
        val contentResolver = mContext.contentResolver
        val cursor = contentResolver.query(contactUri, null, null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                // 获取联系人的唯一ID
                val id = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                // 使用唯一ID查询联系人的所有信息
                contactInfo = queryContactInfo(contentResolver, id)
            }
        }
        return contactInfo
    }

    private fun queryContactInfo(contentResolver: ContentResolver, contactId: String): ContactInfo {
        // 查询联系人的所有信息
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            "${ContactsContract.Data.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )

        val contactInfo = ContactInfo()

        cursor?.use {
            while (it.moveToNext()) {
                val mimeType =
                    it.getString(it.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE))
                when (mimeType) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        contactInfo.name =
                            it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME))
                    }

                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        contactInfo.phoneNumbers.add(
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Phone.NUMBER
                                )
                            ).replace("\\s".toRegex(), "")
                        )
                    }

                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        contactInfo.emails.add(
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Email.ADDRESS
                                )
                            ).replace("\\s".toRegex(), "")
                        )
                    }

                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        contactInfo.company = it.getStringOrNull(
                            it.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Organization.COMPANY
                            )
                        ) ?: ""
                        contactInfo.jobTitle = it.getStringOrNull(
                            it.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Organization.TITLE
                            )
                        ) ?: ""
                    }

                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> {
                        contactInfo.websites.add(
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Website.URL
                                )
                            )
                        )
                    }

                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        contactInfo.addresses.add(
                            it.getString(
                                it.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS
                                )
                            )
                        )
                    }
                }
            }
        }
        return contactInfo
    }
}