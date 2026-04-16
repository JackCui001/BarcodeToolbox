package com.jackcui.barcodetoolbox

import androidx.fragment.app.FragmentActivity
import com.jackcui.barcodetoolbox.MainActivity.Companion.showErrorToast
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.request.ForwardScope

object PermissionHelper {

    data class PermissionConfig(
        val permissions: List<String>,
        val explainReasonTitle: String,
        val explainReasonPositiveText: String = "好的",
        val explainReasonNegativeText: String = "取消",
        val forwardToSettingsTitle: String = "授权请求被永久拒绝\n您需要去应用程序设置中手动开启权限",
        val forwardToSettingsPositiveText: String = "跳转到设置",
        val forwardToSettingsNegativeText: String = "取消"
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
                    showErrorToast("授权请求被拒绝")
                    onDenied?.invoke()
                }
            }
    }
}
