package com.jackcui.codesc

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.jackcui.codesc.MainActivity.Companion.showSnackbar

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val multiScanPre: EditTextPreference? = findPreference("multi_scan_prefix")
            multiScanPre?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue.toString().isEmpty() || newValue.toString().contains("{n}")) {
                    true
                } else {
                    showSnackbar(
                        requireView(),
                        "输入值无效，必须包含 {n} 用以指示当前扫描次数",
                        Snackbar.LENGTH_LONG
                    )
                    false
                }
            }
            val multiPicPre: EditTextPreference? = findPreference("multi_pic_prefix")
            multiPicPre?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue.toString().isEmpty() || newValue.toString().contains("{n}")) {
                    true
                } else {
                    showSnackbar(
                        requireView(),
                        "输入值无效，必须包含 {n} 用以指示当前图片张数",
                        Snackbar.LENGTH_LONG
                    )
                    false
                }
            }
            val multiCodePre: EditTextPreference? = findPreference("multi_code_prefix")
            multiCodePre?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue.toString().isEmpty() || newValue.toString().contains("{n}")) {
                    true
                } else {
                    showSnackbar(
                        requireView(),
                        "输入值无效，必须包含 {n} 用以指示当前码个数",
                        Snackbar.LENGTH_LONG
                    )
                    false
                }
            }
        }
    }
}