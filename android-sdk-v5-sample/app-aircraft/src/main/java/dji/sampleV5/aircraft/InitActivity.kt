package dji.sampleV5.aircraft

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.utils.common.LogUtils
import dji.v5.utils.common.PermissionUtil
import dji.v5.utils.common.ToastUtils

/**
 * MSDK 注册激活
 */
open class InitActivity : AppCompatActivity() {

    val tag: String = "MSDK 注册"
    private val permissionArray = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.KILL_BACKGROUND_PROCESSES,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        checkPermissionAndRequest()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkPermission()) {
            handleAfterPermissionPermitted()
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkPermission()) {
            handleAfterPermissionPermitted()
        }
    }

    private fun handleAfterPermissionPermitted() {
        registerApp()
    }


    private fun registerApp(){
        SDKManager.getInstance().init(this, object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                LogUtils.i(tag, "注册成功!")
                ToastUtils.showToast("注册成功")
            }

            override fun onRegisterFailure(error: IDJIError?) {
                LogUtils.i(tag, "注册失败: (错误代码: ${error?.errorCode()}, 原因: ${error?.description()})")
                ToastUtils.showToast("注册失败: (errorCode: ${error?.errorCode()}, 原因: ${error?.description()})")
            }

            override fun onProductDisconnect(product: Int) {
                LogUtils.i(tag, "设备: $product 断开连接")
                ToastUtils.showToast("设备: $product 断开连接")
            }

            override fun onProductConnect(product: Int) {
                LogUtils.i(tag, "设备: $product 已连接")
                ToastUtils.showToast("设备: $product 已连接")
            }

            override fun onProductChanged(product: Int) {
                LogUtils.i(tag, "设备: $product 改变")
                ToastUtils.showToast("设备: $product 改变")
            }

            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                LogUtils.i(tag, "初始化: ${event?.name}")
                ToastUtils.showToast("初始化: ${event?.name}")
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                LogUtils.i(tag, "Database Download Progress current: $current, total: $total")
                ToastUtils.showToast("Database Download Progress current: $current, total: $total")
            }
        })
    }


    private fun checkPermissionAndRequest() {
        for (i in permissionArray.indices) {
            if (!PermissionUtil.isPermissionGranted(this, permissionArray[i])) {
                requestPermission()
                break
            }
        }
    }

    private fun checkPermission(): Boolean {
        for (i in permissionArray.indices) {
            if (PermissionUtil.isPermissionGranted(this, permissionArray[i])) {
                return true
            }
        }
        return false
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            result?.entries?.forEach {
                if (it.value == false) {
                    requestPermission()
                    return@forEach
                }
            }
        }

    private fun requestPermission() {
        requestPermissionLauncher.launch(permissionArray)
    }

    override fun onDestroy() {
        super.onDestroy()

        ToastUtils.destroy()
    }
}