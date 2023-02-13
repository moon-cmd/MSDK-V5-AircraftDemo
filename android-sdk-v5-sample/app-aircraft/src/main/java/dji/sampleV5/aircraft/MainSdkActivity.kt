package dji.sampleV5.aircraft

import android.graphics.Point
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import dji.sampleV5.modulecommon.util.DensityUtil
import dji.sampleV5.util.animation.ResizeAnimation
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
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit


/**
 * 主界面：定点飞行，kmz航线飞行，mmpk加载
 */
open class MainSdkActivity : InitSdkActivity() {

    //region 属性

    private val TAG = LogUtils.getTag(this)

    private var primaryFpvWidget: FPVWidget? = null
    private var fpvInteractionWidget: FPVInteractionWidget? = null
    private var secondaryFPVWidget: FPVWidget? = null
    private var systemStatusListPanelWidget: SystemStatusListPanelWidget? = null
    private var simulatorControlWidget: SimulatorControlWidget? = null
    private var lensControlWidget: LensControlWidget? = null
    private var autoExposureLockWidget: AutoExposureLockWidget? = null
    private var focusModeWidget: FocusModeWidget? = null
    private var focusExposureSwitchWidget: FocusExposureSwitchWidget? = null
    private var cameraControlsWidget: CameraControlsWidget? = null
    private var horizontalSituationIndicatorWidget: HorizontalSituationIndicatorWidget? = null
    private var exposureSettingsPanel: ExposureSettingsPanel? = null
    private var pfvFlightDisplayWidget: PrimaryFlightDisplayWidget? = null
    private var ndviCameraPanel: CameraNDVIPanelWidget? = null
    private var visualCameraPanel: CameraVisiblePanelWidget? = null
    private var focalZoomWidget: FocalZoomWidget? = null
    private var settingWidget: SettingWidget? = null
    private var mapWidget: dji.v5.ux.arcgis.maps.MapWidget? = null
    private var mSettingPanelWidget: SettingPanelWidget? = null
    private var mDrawerLayout: DrawerLayout? = null

    private var fpvLayout: ConstraintLayout? = null


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

    //endregion

    //region 声明周期

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //region 变量初始化
        height = DensityUtil.dip2px(this, 100F)
        width = DensityUtil.dip2px(this, 150F)
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
//        lensControlWidget = findViewById(R.id.widget_lens_control)
//        ndviCameraPanel = findViewById(R.id.panel_ndvi_camera)
//        visualCameraPanel = findViewById(R.id.panel_visual_camera)
//        autoExposureLockWidget = findViewById(R.id.widget_auto_exposure_lock)
//        focusModeWidget = findViewById(R.id.widget_focus_mode)
//        focusExposureSwitchWidget = findViewById(R.id.widget_focus_exposure_switch)
//        exposureSettingsPanel = findViewById(R.id.panel_camera_controls_exposure_settings)
        pfvFlightDisplayWidget = findViewById(R.id.widget_fpv_flight_display_widget)
//        focalZoomWidget = findViewById(R.id.widget_focal_zoom)
//        cameraControlsWidget = findViewById(R.id.widget_camera_controls)
//        horizontalSituationIndicatorWidget =
//            findViewById(R.id.widget_horizontal_situation_indicator)
        mapWidget = findViewById(R.id.widget_map)
        fpvLayout = findViewById(R.id.fpv_holder)
        mapWidget = findViewById(dji.v5.ux.R.id.widget_map)

//        cameraControlsWidget?.exposureSettingsIndicatorWidget
//            ?.setStateChangeResourceId(R.id.panel_camera_controls_exposure_settings)
        initClickListener()
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

        // 地图初始化


//        mapWidget.initAMap(OnMapReadyListener { map: DJIMap ->
//            // map.setOnMapClickListener(latLng -> onViewClick(mapWidget));
//            val uiSetting = map.uiSettings
//            // 设置地图缩放控件
//            uiSetting?.setZoomControlsEnabled(false)
//            // 设置罗盘控件
//            uiSetting?.setCompassEnabled(true)
//
//            uiSetting?.setMyLocationButtonEnabled(true)
//
//            //uiSetting?.setTiltGesturesEnabled(true)
//        })

        mapWidget?.initMap()
        mapWidget?.onCreate(savedInstanceState)
    }

    override fun onResume() {
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
    }

    override fun onPause() {
        if (compositeDisposable != null) {
            compositeDisposable!!.dispose()
            compositeDisposable = null
        }
        mapWidget?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapWidget?.onDestroy()

        MediaDataCenter.getInstance().videoStreamManager.clearAllStreamSourcesListeners()
        removeChannelStateListener()
    }

    //endregion

    //region 视图控件初始化
    private fun initClickListener() {
        secondaryFPVWidget?.setOnClickListener { v: View? -> swapVideoSource() }
        initChannelStateListener()

        settingWidget?.setOnClickListener { v: View? -> toggleRightDrawer() }

        mapWidget?.rootView?.setOnClickListener{v: View? -> onViewClick(mapWidget!!)}

        fpvLayout?.setOnClickListener{v: View? -> onViewClick(fpvLayout!!)}

//        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
//        val display: Display = windowManager.defaultDisplay
//        val outPoint = Point()
//        display.getRealSize(outPoint)
//        var deviceHeight = outPoint.y
//        var deviceWidth = outPoint.x
//
//        mapWidget?.setOnClickListener{v: View? ->
//
//            var rs = getResources()
//            var dm = rs.getDisplayMetrics()
//            if(Math.abs(mapWidget?.height?.minus(dm.heightPixels) ?: 0) > 1){
//                var layoutParams = mapWidget?.getLayoutParams();
//                layoutParams?.width = dm.widthPixels
//                layoutParams?.height = dm.heightPixels
//                mapWidget?.onResume()
//            }
//        }


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
//        lensControlWidget!!.updateCameraSource(cameraIndex, lensType)
//        ndviCameraPanel!!.updateCameraSource(cameraIndex, lensType)
//        visualCameraPanel!!.updateCameraSource(cameraIndex, lensType)
//        autoExposureLockWidget!!.updateCameraSource(cameraIndex, lensType)
//        focusModeWidget!!.updateCameraSource(cameraIndex, lensType)
//        focusExposureSwitchWidget!!.updateCameraSource(cameraIndex, lensType)
//        cameraControlsWidget!!.updateCameraSource(cameraIndex, lensType)
//        exposureSettingsPanel!!.updateCameraSource(cameraIndex, lensType)
//        focalZoomWidget!!.updateCameraSource(cameraIndex, lensType)
//        horizontalSituationIndicatorWidget!!.updateCameraSource(cameraIndex, lensType)
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

        //fpv下不显示
//        lensControlWidget!!.visibility =
//            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
//        ndviCameraPanel!!.visibility =
//            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
//        visualCameraPanel!!.visibility =
//            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
//        autoExposureLockWidget!!.visibility =
//            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
//        focusModeWidget!!.visibility =
//            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
//        focusExposureSwitchWidget!!.visibility =
//            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
//        cameraControlsWidget!!.visibility =
//            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
//        focalZoomWidget!!.visibility =
//            if (devicePosition == PhysicalDevicePosition.NOSE) View.INVISIBLE else View.VISIBLE
//        horizontalSituationIndicatorWidget!!.setSimpleModeEnable(devicePosition != PhysicalDevicePosition.NOSE)

        //有其他的显示逻辑，这里确保fpv下不显示
        if (devicePosition == PhysicalDevicePosition.NOSE) {
//            exposureSettingsPanel!!.visibility = View.GONE
        }

        //只在部分len下显示
//        ndviCameraPanel!!.visibility =
//            if (CameraUtil.isSupportForNDVI(lensType)) View.VISIBLE else View.INVISIBLE
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

    //region 其它方法

    private fun onViewClick(view: View) {
        if ((view === fpvLayout) && !isMapMini) {
            resizeFPVWidget(

                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT,
                0,
                0
            )
//            reorderCameraCapturePanel()
            val mapViewAnimation =
                ResizeAnimation(mapWidget!!, deviceWidth, deviceHeight, width, height, margin)
            mapWidget!!.startAnimation(mapViewAnimation)
            isMapMini = true
        } else if (view === mapWidget && isMapMini) {
//            hidePanels()
            resizeFPVWidget(width, height, margin, 12)
//            reorderCameraCapturePanel()
            val mapViewAnimation =
                ResizeAnimation(mapWidget!!, width, height, deviceWidth, deviceHeight, 0)
            mapWidget!!.startAnimation(mapViewAnimation)
            isMapMini = false
        }
    }

    open fun resizeFPVWidget(width: Int, height: Int, margin: Int, fpvInsertPosition: Int) {
        val fpvParams = fpvLayout?.getLayoutParams() as? ConstraintLayout.LayoutParams
        fpvParams?.height = height
        fpvParams?.width = width
        fpvParams?.rightMargin = margin
        fpvParams?.bottomMargin = margin
        if (isMapMini) {
            fpvParams?.bottomToBottom = R.id.root_view
            fpvParams?.startToStart = R.id.root_view

//            fpvParams.addRule(RelativeLayout.CENTER_IN_PARENT, 0)
//            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE)
//            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        } else {
//            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0)
//            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0)
//            fpvParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
            fpvParams?.bottomToBottom = R.id.root_view
            fpvParams?.endToEnd = R.id.root_view
            fpvParams?.topToTop = R.id.root_view

        }
        fpvLayout?.setLayoutParams(fpvParams)
        root_view.removeView(fpvLayout)
        root_view.addView(fpvLayout, fpvInsertPosition)
    }


    //endregion
}