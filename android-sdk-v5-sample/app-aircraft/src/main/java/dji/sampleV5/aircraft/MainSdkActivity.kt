package dji.sampleV5.aircraft

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.view.WindowManager
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import dji.sampleV5.map.ArcGISMapWidget
import dji.sampleV5.moduleaircraft.models.WayPointV3VM
import dji.sampleV5.modulecommon.util.DensityUtil
import dji.sampleV5.util.FileUtil
import dji.sampleV5.util.KmzManager
import dji.sampleV5.util.ResizeAnimation
import dji.sdk.keyvalue.value.common.CameraLensType
import dji.v5.common.video.channel.VideoChannelState
import dji.v5.common.video.channel.VideoChannelType
import dji.v5.common.video.interfaces.VideoChannelStateChangeListener
import dji.v5.common.video.stream.PhysicalDevicePosition
import dji.v5.common.video.stream.StreamSource
import dji.v5.manager.account.LoginInfo
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.utils.common.JsonUtil
import dji.v5.utils.common.LogUtils
import dji.v5.utils.common.ToastUtils
import dji.v5.ux.cameracore.widget.autoexposurelock.AutoExposureLockWidget
import dji.v5.ux.cameracore.widget.cameracontrols.CameraControlsWidget
import dji.v5.ux.cameracore.widget.cameracontrols.exposuresettings.ExposureSettingsPanel
import dji.v5.ux.cameracore.widget.cameracontrols.lenscontrol.LensControlWidget
import dji.v5.ux.cameracore.widget.focusexposureswitch.FocusExposureSwitchWidget
import dji.v5.ux.cameracore.widget.focusmode.FocusModeWidget
import dji.v5.ux.cameracore.widget.fpvinteraction.FPVInteractionWidget
import dji.v5.ux.core.base.SchedulerProvider.computation
import dji.v5.ux.core.extension.hide
import dji.v5.ux.core.panel.systemstatus.SystemStatusListPanelWidget
import dji.v5.ux.core.panel.topbar.TopBarPanelWidget
import dji.v5.ux.core.util.CameraUtil
import dji.v5.ux.core.util.CommonUtils
import dji.v5.ux.core.util.DataProcessor
import dji.v5.ux.core.widget.fpv.FPVStreamSourceListener
import dji.v5.ux.core.widget.fpv.FPVWidget
import dji.v5.ux.core.widget.hsi.HorizontalSituationIndicatorWidget
import dji.v5.ux.core.widget.hsi.PrimaryFlightDisplayWidget
import dji.v5.ux.core.widget.setting.SettingPanelWidget
import dji.v5.ux.core.widget.setting.SettingWidget
import dji.v5.ux.training.simulatorcontrol.SimulatorControlWidget
import dji.v5.ux.visualcamera.CameraNDVIPanelWidget
import dji.v5.ux.visualcamera.CameraVisiblePanelWidget
import dji.v5.ux.visualcamera.zoom.FocalZoomWidget
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.concurrent.TimeUnit


/**
 * 主界面：定点飞行，kmz航线飞行，mmpk加载,飞行时显示高度等信息，确认后飞行
 */
open class MainSdkActivity : InitSdkActivity(){

    //region 属性

    private val TAG = LogUtils.getTag(this)

    private var primaryFpvWidget: FPVWidget? = null
    private var fpvInteractionWidget: FPVInteractionWidget? = null
    private var secondaryFPVWidget: FPVWidget? = null
    private var systemStatusListPanelWidget: SystemStatusListPanelWidget? = null
    private var simulatorControlWidget: SimulatorControlWidget? = null
    private var pfvFlightDisplayWidget: PrimaryFlightDisplayWidget? = null
    private var settingWidget: SettingWidget? = null
    private var mapWidget: ArcGISMapWidget? = null
    private var mSettingPanelWidget: SettingPanelWidget? = null
    private var mDrawerLayout: DrawerLayout? = null

    private var fpvLayout: ConstraintLayout? = null
    private var widgetMainView:  ConstraintLayout? = null

    private var fpvViewExpand: ImageButton? = null

    private var compositeDisposable: CompositeDisposable? = null
    private val cameraSourceProcessor = DataProcessor.create(
        CameraSource(
            PhysicalDevicePosition.UNKNOWN,
            CameraLensType.UNKNOWN
        )
    )
    private var primaryChannelStateListener: VideoChannelStateChangeListener? = null
    private var secondaryChannelStateListener: VideoChannelStateChangeListener? = null

    // 地图/视频 切换
    private var isMapMini = true
    private var height = 0
    private var width = 0
    private var margin = 0
    private var deviceWidth = 0
    private var deviceHeight = 0

    private val wayPointV3VM: WayPointV3VM by viewModels()

    private var kmzManager:KmzManager? = null

    // 加载kml文件监听
    private lateinit var selectFileListener: (String, Int) -> Unit

    //endregion

    //region 生命周期

    override fun onCreate(savedInstanceState: Bundle?) {

        try {
            super.onCreate(savedInstanceState)
            ToastUtils.init(this)
            setContentView(R.layout.activity_main)

            //region 变量初始化
            height = DensityUtil.dip2px(this, 120F)
            width = DensityUtil.dip2px(this, 220F)
            margin = DensityUtil.dip2px(this, 12F)

            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val outPoint = Point()
            display.getRealSize(outPoint)
            deviceHeight = outPoint.y
            deviceWidth = outPoint.x

            //endregion

            // Setup top bar state callbacks
            val topBarPanel = findViewById<TopBarPanelWidget>(R.id.panel_top_bar)
            val systemStatusWidget = topBarPanel.systemStatusWidget
            if (systemStatusWidget != null) {
                systemStatusWidget.stateChangeCallback =
                    findViewById(R.id.widget_panel_system_status_list)
            }
            val simulatorIndicatorWidget = topBarPanel.simulatorIndicatorWidget
            if (simulatorIndicatorWidget != null) {
                simulatorIndicatorWidget.stateChangeCallback =
                    findViewById(R.id.widget_simulator_control)
            }
            mDrawerLayout = findViewById(R.id.root_view)
            settingWidget = topBarPanel.settingWidget
            primaryFpvWidget = findViewById(R.id.widget_primary_fpv)
            fpvInteractionWidget = findViewById(R.id.widget_fpv_interaction)
            secondaryFPVWidget = findViewById(R.id.widget_secondary_fpv)
            systemStatusListPanelWidget = findViewById(R.id.widget_panel_system_status_list)
            simulatorControlWidget = findViewById(R.id.widget_simulator_control)
            pfvFlightDisplayWidget = findViewById(R.id.widget_fpv_flight_display_widget)
            mapWidget = findViewById(R.id.widget_map)
            fpvLayout = findViewById(R.id.fpv_holder)
            mapWidget = findViewById(dji.v5.ux.R.id.widget_map)
            widgetMainView = findViewById(R.id.widget_mainView)
            fpvViewExpand = findViewById(R.id.btn_fpvExpand)


            initClickListener()
            kmzManager = KmzManager(wayPointV3VM, this)
            kmzManager?.setDrawLineEvent { points: List<com.esri.arcgisruntime.geometry.Point> -> mapWidget?.addPolyline(points) }
            mapWidget?.kmzManager = kmzManager
            selectFileListener = mapWidget?.selectFileListenerEvent!!
            MediaDataCenter.getInstance().videoStreamManager.addStreamSourcesListener { sources: List<StreamSource>? ->
                runOnUiThread { updateFPVWidgetSource(sources) }
            }


            primaryFpvWidget?.setOnFPVStreamSourceListener(object : FPVStreamSourceListener {
                override fun onStreamSourceUpdated(
                    devicePosition: PhysicalDevicePosition,
                    lensType: CameraLensType
                ) {
                    LogUtils.i(TAG, devicePosition, lensType)
                    cameraSourceProcessor.onNext(CameraSource(devicePosition, lensType))
                }
            })
            //小surfaceView放置在顶部，避免被大的遮挡
            secondaryFPVWidget?.setSurfaceViewZOrderOnTop(true)
            secondaryFPVWidget?.setSurfaceViewZOrderMediaOverlay(true)
            mapWidget?.setWidgetModel(getProductInstance())
            mapWidget?.setActiveContext(this)

        }catch (e:Exception){
            ToastUtils.showToast("页面初始化异常，${e.message}")
            LogUtils.e(TAG, "页面初始化异常，${e.message}，${e.stackTraceToString()}")
        }

    }

    override fun onResume() {

        try {
            super.onResume()
            mapWidget?.onResume()

            compositeDisposable = CompositeDisposable()
            compositeDisposable!!.add(
                systemStatusListPanelWidget!!.closeButtonPressed()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { pressed: Boolean ->
                        if (pressed) {
                            systemStatusListPanelWidget!!.hide()
                        }
                    })
            compositeDisposable!!.add(
                simulatorControlWidget!!.getUIStateUpdates()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { simulatorControlWidgetState: SimulatorControlWidget.UIState? ->
                        if (simulatorControlWidgetState is SimulatorControlWidget.UIState.VisibilityUpdated) {
                            if (simulatorControlWidgetState.isVisible) {
                                hideOtherPanels(simulatorControlWidget)
                            }
                        }
                    })
            compositeDisposable!!.add(cameraSourceProcessor.toFlowable()
                .observeOn(computation())
                .throttleLast(500, TimeUnit.MILLISECONDS)
                .subscribe { result: CameraSource ->
                    runOnUiThread { onCameraSourceUpdated(result.devicePosition, result.lensType) }
                }
            )
        }catch (e:Exception){
            ToastUtils.showToast("onResume异常，${e.message}")
            LogUtils.e(TAG, "onResume异常，${e.message},${e.stackTraceToString()}")
        }

    }

    override fun onPause() {
        try {
            if (compositeDisposable != null) {
                compositeDisposable!!.dispose()
                compositeDisposable = null
            }
            mapWidget?.onPause()
            super.onPause()
        }catch (e:Exception){
            ToastUtils.showToast("onPause异常，${e.message}")
            LogUtils.e(TAG, "onPause异常，${e.message},${e.stackTraceToString()}")
        }

    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            mapWidget?.onDestroy()

            MediaDataCenter.getInstance().videoStreamManager.clearAllStreamSourcesListeners()
            removeChannelStateListener()
        }catch (e:Exception){
            ToastUtils.showToast("onDestroy异常，${e.message}")
            LogUtils.e(TAG, "onDestroy异常，${e.message},${e.stackTraceToString()}")
        }
    }

    //endregion

    //region 事件初始化
    private fun initClickListener() {
        secondaryFPVWidget?.setOnClickListener { v: View? ->
            try{
                swapVideoSource()
            }catch (e:Exception){
                LogUtils.e(TAG, "secondaryFPVClick异常，${e.message}, ${e.stackTraceToString()}")
            }
        }

        initChannelStateListener()

        settingWidget?.setOnClickListener { v: View? ->
            try{
                toggleRightDrawer()
            }catch (e:Exception){
                ToastUtils.showToast("打开设置错误，${e.message}")
                LogUtils.e(TAG, "打开设置异常，${e.message}, ${e.stackTraceToString()}")
            }
        }

        mapWidget?.setExpandMapClickListener{v: View? ->
            try{
                onViewClick(mapWidget!!)
            }catch (e: Exception){
                ToastUtils.showToast("展开地图错误，${e.message}")
                LogUtils.e(TAG, "展开地图异常，${e.message}, ${e.stackTraceToString()}")
            }
        }

        fpvViewExpand?.setOnClickListener{v: View? ->
            try{
                onViewClick(fpvLayout!!)
            }catch (e: Exception){
                ToastUtils.showToast("展开视图窗口错误，${e.message}")
                LogUtils.e(TAG, "展开视图窗口错误，${e.message}, ${e.stackTraceToString()}")
            }
        }

    }

    private fun toggleRightDrawer() {
        if (mSettingPanelWidget == null) {
            val stub = findViewById<ViewStub>(R.id.manual_right_nav_setting_stub)
            mSettingPanelWidget = if (stub != null) {
                stub.inflate() as SettingPanelWidget
            } else {
                findViewById(R.id.manual_right_nav_setting)
            }
        }
        mDrawerLayout!!.openDrawer(GravityCompat.END)
    }


    /**
     * 用户信息加载
     */
    override fun loadAccountInfo(accountInfo: LoginInfo?) {

    }

    private fun hideOtherPanels(widget: View?) {
        val panels = arrayOf<View?>(
            simulatorControlWidget
        )
        for (panel in panels) {
            if (widget !== panel) {
                panel!!.visibility = View.GONE
            }
        }
    }

    private fun updateFPVWidgetSource(streamSources: List<StreamSource>?) {
        LogUtils.i(TAG, JsonUtil.toJson(streamSources))
        if (streamSources == null) {
            return
        }

        //没有数据
        if (streamSources.isEmpty()) {
            secondaryFPVWidget!!.visibility = View.GONE
            return
        }

        //仅一路数据
        if (streamSources.size == 1) {
            //这里仅仅做Widget的显示与否，source和channel的获取放到widget中
            secondaryFPVWidget!!.visibility = View.GONE
            return
        }
        secondaryFPVWidget!!.visibility = View.VISIBLE
    }

    private fun initChannelStateListener() {
        try{
            val primaryChannel =
                MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(
                    VideoChannelType.PRIMARY_STREAM_CHANNEL
                )
            val secondaryChannel =
                MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(
                    VideoChannelType.SECONDARY_STREAM_CHANNEL
                )
            if (primaryChannel != null) {
                primaryChannelStateListener =
                    VideoChannelStateChangeListener { from: VideoChannelState?, to: VideoChannelState ->
                        val primaryStreamSource =
                            primaryChannel.streamSource
                        if (VideoChannelState.ON == to && primaryStreamSource != null) {
                            runOnUiThread {
                                primaryFpvWidget!!.updateVideoSource(
                                    primaryStreamSource,
                                    VideoChannelType.PRIMARY_STREAM_CHANNEL
                                )
                            }
                        }
                    }
                primaryChannel.addVideoChannelStateChangeListener(primaryChannelStateListener)
            }
            if (secondaryChannel != null) {
                secondaryChannelStateListener =
                    VideoChannelStateChangeListener { from: VideoChannelState?, to: VideoChannelState ->
                        val secondaryStreamSource =
                            secondaryChannel.streamSource
                        if (VideoChannelState.ON == to && secondaryStreamSource != null) {
                            runOnUiThread {
                                secondaryFPVWidget!!.updateVideoSource(
                                    secondaryStreamSource,
                                    VideoChannelType.SECONDARY_STREAM_CHANNEL
                                )
                            }
                        }
                    }
                secondaryChannel.addVideoChannelStateChangeListener(secondaryChannelStateListener)
            }
        }catch (e: Exception){
            ToastUtils.showToast("视频初始化错误，${e.message}")
            LogUtils.e(TAG, "视频初始化错误，${e.message}, ${e.stackTraceToString()}")
        }
    }

    private fun removeChannelStateListener() {
        val primaryChannel =
            MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(
                VideoChannelType.PRIMARY_STREAM_CHANNEL
            )
        val secondaryChannel =
            MediaDataCenter.getInstance().videoStreamManager.getAvailableVideoChannel(
                VideoChannelType.SECONDARY_STREAM_CHANNEL
            )
        primaryChannel?.removeVideoChannelStateChangeListener(primaryChannelStateListener)
        secondaryChannel?.removeVideoChannelStateChangeListener(secondaryChannelStateListener)
    }

    private fun onCameraSourceUpdated(
        devicePosition: PhysicalDevicePosition,
        lensType: CameraLensType?
    ) {
        LogUtils.i(TAG, "onCameraSourceUpdated", devicePosition, lensType)
        val cameraIndex = CameraUtil.getCameraIndex(devicePosition)
        fpvInteractionWidget!!.updateCameraSource(cameraIndex, lensType!!)
        fpvInteractionWidget!!.updateGimbalIndex(CommonUtils.getGimbalIndex(devicePosition))
        updateViewVisibility(devicePosition, lensType)
        updateInteractionEnabled()
    }

    private fun updateViewVisibility(
        devicePosition: PhysicalDevicePosition,
        lensType: CameraLensType?
    ) {
        //只在fpv下显示
        pfvFlightDisplayWidget!!.visibility =
            if (devicePosition == PhysicalDevicePosition.NOSE) View.VISIBLE else View.INVISIBLE
    }

    /**
     * Swap the video sources of the FPV and secondary FPV widgets.
     */
    private fun swapVideoSource() {
        val primaryVideoChannel = primaryFpvWidget!!.videoChannelType
        val primaryStreamSource = primaryFpvWidget!!.getStreamSource()
        val secondaryVideoChannel = secondaryFPVWidget!!.videoChannelType
        val secondaryStreamSource = secondaryFPVWidget!!.getStreamSource()
        //两个source都存在的情况下才进行切换
        if (secondaryStreamSource != null && primaryStreamSource != null) {
            primaryFpvWidget!!.updateVideoSource(secondaryStreamSource, secondaryVideoChannel)
            secondaryFPVWidget!!.updateVideoSource(primaryStreamSource, primaryVideoChannel)
        }
    }

    private fun updateInteractionEnabled() {
        val newPrimaryStreamSource = primaryFpvWidget!!.getStreamSource()
        fpvInteractionWidget!!.isInteractionEnabled = false
        if (newPrimaryStreamSource != null) {
            fpvInteractionWidget!!.isInteractionEnabled =
                newPrimaryStreamSource.physicalDevicePosition != PhysicalDevicePosition.NOSE
        }
    }

    private class CameraSource(
        var devicePosition: PhysicalDevicePosition,
        var lensType: CameraLensType?
    )

    override fun onBackPressed() {
        if (mDrawerLayout!!.isDrawerOpen(GravityCompat.END)) {
            mDrawerLayout!!.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }

    //endregion

    //region 地图 - 视频 视图切换

    private fun onViewClick(view: View) {
        // 显示视频视图
        if ((view === fpvLayout) && !isMapMini) {
            resizeFPVWidget(

                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                0,
                0
            )
            val mapViewAnimation =
                ResizeAnimation(mapWidget!!, deviceWidth, deviceHeight, width, height, margin)
            mapWidget!!.startAnimation(mapViewAnimation)
            isMapMini = true
            // 缩放按钮显示/隐藏
            mapWidget?.minimizedMap(true)
            fpvViewExpand?.visibility = View.GONE

        }
        // 显示地图视图
        else if (view === mapWidget && isMapMini) {
            resizeFPVWidget(width, height, margin, 1)
            val mapViewAnimation =
                ResizeAnimation(mapWidget!!, width, height, deviceWidth, deviceHeight, 0)
            mapWidget!!.startAnimation(mapViewAnimation)
            isMapMini = false
            // 缩放按钮显示/隐藏
            mapWidget?.minimizedMap(false)
            fpvViewExpand?.visibility = View.VISIBLE

        }
    }

    open fun resizeFPVWidget(width: Int, height: Int, margin: Int, fpvInsertPosition: Int) {
        val fpvParams = fpvLayout?.getLayoutParams() as? ConstraintLayout.LayoutParams
        fpvParams?.height = height
        fpvParams?.width = width

        // 显示地图，缩小视频窗口
        if (isMapMini) {

            fpvParams?.topToTop =  ConstraintLayout.LayoutParams.UNSET
            fpvParams?.endToEnd = ConstraintLayout.LayoutParams.UNSET
            fpvParams?.startToStart = R.id.root_view
            fpvParams?.bottomToBottom = R.id.widget_mainView

        }
        // 显示视频窗口，缩小地图窗口
        else {
            fpvParams?.startToStart =  ConstraintLayout.LayoutParams.UNSET
            fpvParams?.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            fpvParams?.topToTop = R.id.root_view
            fpvParams?.endToEnd = R.id.root_view

        }
        fpvLayout?.setLayoutParams(fpvParams)

        widgetMainView?.removeView(fpvLayout)
//        fpvLayout?.setOnClickListener{v: View? -> onViewClick(fpvLayout!!)}
        widgetMainView?.addView(fpvLayout, fpvInsertPosition)
    }

    //endregion

    // region 设备定位,权限检查

    /**
     * 权限
     *
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)

            // 开启定位
            if (grantResults.isEmpty() ||
                grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                (Manifest.permission.ACCESS_FINE_LOCATION !in permissions) ||
                (Manifest.permission.ACCESS_COARSE_LOCATION !in permissions)) {

                ToastUtils.showToast("定位未开启或没有权限")
                LogUtils.e("定位权限未开启")

            }
        }catch (e:Exception){
            ToastUtils.showToast("权限检查异常，${e.message}")
            LogUtils.e(TAG, "权限检查异常，${e.message},${e.stackTraceToString()}")
        }

    }

    //endregion

    //region 文件选择

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        try {
            super.onActivityResult(requestCode, resultCode, data)

            var context = this
            if(resultCode == RESULT_OK){
                var uri = data?.data
                var path = FileUtil.getFileAbsolutePath(context, uri!!)
                LogUtils.d(TAG,"已选择文件：${path}")
                selectFileListener(path!!, requestCode)

            }
        }catch (e:Exception){
            ToastUtils.showToast("文件选择异常，${e.message}")
            LogUtils.e(TAG, "文件选择异常，${e.message},${e.stackTraceToString()}")
        }

    }

    //endregion
}