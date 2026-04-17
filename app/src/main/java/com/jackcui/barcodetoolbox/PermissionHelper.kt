package com.jackcui.barcodetoolbox

import androidx.fragment.app.FragmentActivity
import com.jackcui.barcodetoolbox.MainActivity.Companion.showErrorToast
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.request.ForwardScope

object PermissionHelper {

    data class PermissionConfig(
        val permissions: List<String>,
        val explainReasonTitle: String,
        val explainReasonPositiveText: String = "OK",
        val explainReasonNegativeText: String = "Cancel",
        val forwardToSettingsTitle: String = "Permission permanently denied\nYou need to go to app settings to manually enable permissions",
        val forwardToSettingsPositiveText: String = "Settings",
        val forwardToSettingsNegativeText: String = "Cancel"
    )

    fun requestPermissions(
        activity: FragmentActivity,
        config: PermissionConfig,
        onGranted: () -> Unit,
        onDenied: (() -> Unit)? = null
    ) {
        PermissionX.init(activity)
            .permissions(config.permissions)
            .explainReasonBeforeRequest()
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    config.explainReasonTitle,
                    config.explainReasonPositiveText,
                    config.explainReasonNegativeText
                )
            }
            .onForwardToSettings { scope: ForwardScope, deniedList: MutableList<String> ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    config.forwardToSettingsTitle,
                    config.forwardToSettingsPositiveText,
                    config.forwardToSettingsNegativeText
                )
            }
            .request { allGranted, _, _ ->
                if (allGranted) {
                    onGranted()
                } else {
                    showErrorToast(activity.getString(R.string.toast_error_permission_denied))
                    onDenied?.invoke()
                }
            }
    }
}
