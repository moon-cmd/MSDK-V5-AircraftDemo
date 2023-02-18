package dji.sampleV5.aircraft

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils

import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dji.sampleV5.moduleaircraft.models.WayPointV3VM
import dji.sampleV5.modulecommon.models.LoginVM
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.account.LoginInfo
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.utils.common.LogUtils
import dji.v5.utils.common.PermissionUtil
import dji.v5.utils.common.ToastUtils
import dji.v5.ux.core.base.DJISDKModel
import dji.v5.ux.core.communication.ObservableInMemoryKeyedStore
import dji.v5.ux.map.MapWidgetModel

/**
 * MSDK 注册激活
 */
abstract class InitSdkActivity : AppCompatActivity(){

    protected val tag: String = "MSDK 注册"
    protected  val  loginTag: String = "登录"
    protected val loginVM: LoginVM by viewModels()
    var mainHandler = Handler(Looper.getMainLooper())
    open var userInfo: LoginInfo? = null


    private val permissionArray = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.KILL_BACKGROUND_PROCESSES,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
//        Manifest.permission.MANAGE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ToastUtils.init(this)

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        // 许可检查及sdk初始化
        checkPermissionAndRequest()
        // 用户登录状态初始化，必须先登录
        initLoginState()
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
        LogUtils.i("开始注册App...")
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


    /**
     * 连接飞机信息获取
     */
    open fun getProductInstance(): MapWidgetModel? {
        return MapWidgetModel(
            DJISDKModel.getInstance(),
            ObservableInMemoryKeyedStore.getInstance()
        );
    }



    /**
     * 登录状态初始化
     */
    private fun initLoginState() {

        // 尝试获取已有的登录信息
        userInfo = loginVM.getLoginInfo()
        // 登录状态监听
        loginVM.addLoginStateChangeListener {
            mainHandler.post {
                it?.run {

                    // 刷新用户信息
                    userInfo = it

                    // 加载用户信息
                    loadAccountInfo(it)

                }
            }
        }

        // 用户未登录，需要先登录才能使用
        if(TextUtils.isEmpty(userInfo?.account)){
            loginAccount()
        }else{
            ToastUtils.showToast("${getAccountName()}登录成功!")

            LogUtils.i(loginTag, "${getAccountName()}登录成功!")
        }

    }

    /**
     * 账户登录
     */
    open fun loginAccount(){
        loginVM.loginAccount(this, object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                ToastUtils.showToast("${getAccountName()}登录成功!")

                LogUtils.i(loginTag, "${getAccountName()}登录成功!")
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("登录失败: $error")

                LogUtils.e(loginTag, "登录失败：${error}")
            }

        })
    }

    /**
     * 账户退出登录
     */
    open fun logoutAccount(){
        loginVM.logoutAccount(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                ToastUtils.showToast("${getAccountName()}登录成功!")

                LogUtils.i(loginTag, "${getAccountName()}登录成功!")
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("登录失败: $error")

                LogUtils.e(loginTag, "登录失败：${error}")
            }

        })
    }

    /**
     * 账户名获取
     */
    open fun getAccountName(): String?{
        if(TextUtils.isEmpty(userInfo?.account)){
            return ""
        }
//        if(userInfo?.account?.contains(".", ignoreCase = true) == true){
//            return userInfo?.account?.indexOf(".")?.let { userInfo?.account?.substring(0, it) }
//        }
        return userInfo?.account;
    }

    /**
     * 初始化账户信息
     */
    abstract fun loadAccountInfo(accountInfo:LoginInfo?)
}