package com.jackcui.barcodetoolbox

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.jackcui.barcodetoolbox.MainActivity.Companion.showErrorToast

class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private fun validatePrefixPreference(key: String, errorMessage: String): Boolean {
            val preference: EditTextPreference? = findPreference(key)
            preference?.setOnPreferenceChangeListener { _, newValue ->
                val value = newValue.toString()
                if (value.isEmpty() || value.contains("{n}")) {
                    true
                } else {
                    showErrorToast(errorMessage)
                    false
                }
            }
            return true
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            validatePrefixPreference(
                "multi_scan_prefix",
                "输入值无效，必须包含 {n} 用以指示当前扫描次数"
            )
            validatePrefixPreference(
                "multi_pic_prefix",
                "输入值无效，必须包含 {n} 用以指示当前图片张数"
            )
            validatePrefixPreference(
                "multi_code_prefix",
                "输入值无效，必须包含 {n} 用以指示当前码个数"
            )

            findPreference<androidx.preference.ListPreference>("language")?.setOnPreferenceChangeListener { _, _ ->
                restartApp()
                true
            }
        }

        private fun restartApp() {
            val intent =
                activity?.packageManager?.getLaunchIntentForPackage(activity?.packageName ?: "")
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity?.startActivity(intent ?: Intent(activity, MainActivity::class.java))
            activity?.finishAffinity()
        }
    }
}