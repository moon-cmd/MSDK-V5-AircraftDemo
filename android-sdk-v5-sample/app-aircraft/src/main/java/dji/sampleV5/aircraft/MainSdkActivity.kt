package dji.sampleV5.aircraft

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.elvishew.xlog.XLog
import dji.sampleV5.logInfo.LogInfoActivity
import dji.sampleV5.map.ArcGISMapWidget
import dji.sampleV5.moduleaircraft.models.WayPointV3VM
import dji.sampleV5.modulecommon.util.DensityUtil
import dji.sampleV5.util.AppInfo
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
import dji.v5.utils.common.ToastUtils
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
 * ???????????????????????????kmz???????????????mmpk??????,????????????????????????????????????????????????
 */
open class MainSdkActivity : InitSdkActivity(){

    //region ??????

    private val TAG = "MainSdkActivity"

    private  var widgetDebug:LinearLayout? = null
    private var btnLogInfo: ImageButton? = null

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

    // ??????/?????? ??????
    private var isMapMini = true
    private var height = 0
    private var width = 0
    private var margin = 0
    private var deviceWidth = 0
    private var deviceHeight = 0

    private val wayPointV3VM: WayPointV3VM by viewModels()

    private var kmzManager:KmzManager? = null

    // ??????kml????????????
    private lateinit var selectFileListener: (String, Int) -> Unit

    //endregion

    //region ????????????

    override fun onCreate(savedInstanceState: Bundle?) {

        try {
            super.onCreate(savedInstanceState)
            ToastUtils.init(this)
            setContentView(R.layout.activity_main)

            //region ???????????????
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

            widgetDebug = findViewById(R.id.widget_debug_info)
            btnLogInfo = findViewById(R.id.btn_log_info)

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
                    XLog.i(TAG, devicePosition, lensType)
                    cameraSourceProcessor.onNext(CameraSource(devicePosition, lensType))
                }
            })
            //???surfaceView???????????????????????????????????????
            secondaryFPVWidget?.setSurfaceViewZOrderOnTop(true)
            secondaryFPVWidget?.setSurfaceViewZOrderMediaOverlay(true)
            mapWidget?.setWidgetModel(getProductInstance())
            mapWidget?.setActiveContext(this)

        }catch (e:Exception){
            XLog.e(TAG, "????????????????????????${e.message}???${e.stackTraceToString()}")
            ToastUtils.showToast("????????????????????????${e.message}")

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
            XLog.e(TAG, "onResume?????????${e.message},${e.stackTraceToString()}")
            ToastUtils.showToast("onResume?????????${e.message}")

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
            XLog.e(TAG, "onPause?????????${e.message},${e.stackTraceToString()}")
            ToastUtils.showToast("onPause?????????${e.message}")
        }

    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            mapWidget?.onDestroy()

            MediaDataCenter.getInstance().videoStreamManager.clearAllStreamSourcesListeners()
            removeChannelStateListener()
        }catch (e:Exception){

            XLog.e(TAG, "onDestroy?????????${e.message},${e.stackTraceToString()}")
            ToastUtils.showToast("onDestroy?????????${e.message}")
        }
    }

    //endregion

    //region ???????????????
    private fun initClickListener() {

        btnLogInfo?.setOnClickListener {
            val intent = Intent(this, LogInfoActivity::class.java)
            startActivity(intent)
        }

        secondaryFPVWidget?.setOnClickListener { v: View? ->
            try{
                swapVideoSource()
            }catch (e:Exception){
                XLog.e(TAG, "secondaryFPVClick?????????${e.message}, ${e.stackTraceToString()}")
            }
        }

        initChannelStateListener()

        settingWidget?.setOnClickListener { v: View? ->
            try{
                toggleRightDrawer()
            }catch (e:Exception){
                XLog.e(TAG, "?????????????????????${e.message}, ${e.stackTraceToString()}")
                ToastUtils.showToast("?????????????????????${e.message}")

            }
        }

        mapWidget?.setExpandMapClickListener{v: View? ->
            try{
                onViewClick(mapWidget!!)
            }catch (e: Exception){
                XLog.e(TAG, "?????????????????????${e.message}, ${e.stackTraceToString()}")
                ToastUtils.showToast("?????????????????????${e.message}")

            }
        }

        fpvViewExpand?.setOnClickListener{v: View? ->
            try{
                onViewClick(fpvLayout!!)
            }catch (e: Exception){
                XLog.e(TAG, "???????????????????????????${e.message}, ${e.stackTraceToString()}")
                ToastUtils.showToast("???????????????????????????${e.message}")

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
        XLog.i(TAG, JsonUtil.toJson(streamSources))
        if (streamSources == null) {
            return
        }

        //????????????
        if (streamSources.isEmpty()) {
            secondaryFPVWidget!!.visibility = View.GONE
            return
        }

        //???????????????
        if (streamSources.size == 1) {
            //???????????????Widget??????????????????source???channel???????????????widget???
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
            XLog.e(TAG, "????????????????????????${e.message}, ${e.stackTraceToString()}")
            ToastUtils.showToast("????????????????????????${e.message}")

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
        XLog.i(TAG, "onCameraSourceUpdated", devicePosition, lensType)
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
        //??????fpv?????????
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
        //??????source????????????????????????????????????
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

    //region ?????? - ?????? ????????????

    private fun onViewClick(view: View) {
        // ??????????????????
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
            // ??????????????????/??????
            mapWidget?.minimizedMap(true)
            fpvViewExpand?.visibility = View.GONE

        }
        // ??????????????????
        else if (view === mapWidget && isMapMini) {
            resizeFPVWidget(width, height, margin, 1)
            val mapViewAnimation =
                ResizeAnimation(mapWidget!!, width, height, deviceWidth, deviceHeight, 0)
            mapWidget!!.startAnimation(mapViewAnimation)
            isMapMini = false
            // ??????????????????/??????
            mapWidget?.minimizedMap(false)
            fpvViewExpand?.visibility = View.VISIBLE

        }
    }

    open fun resizeFPVWidget(width: Int, height: Int, margin: Int, fpvInsertPosition: Int) {
        val fpvParams = fpvLayout?.getLayoutParams() as? ConstraintLayout.LayoutParams
        fpvParams?.height = height
        fpvParams?.width = width

        // ?????????????????????????????????
        if (isMapMini) {

            fpvParams?.topToTop =  ConstraintLayout.LayoutParams.UNSET
            fpvParams?.endToEnd = ConstraintLayout.LayoutParams.UNSET
            fpvParams?.startToStart = R.id.root_view
            fpvParams?.bottomToBottom = R.id.widget_mainView

        }
        // ???????????????????????????????????????
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

    // region ????????????,????????????

    /**
     * ??????
     *
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)

            // ????????????
            if (grantResults.isEmpty() ||
                grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                (Manifest.permission.ACCESS_FINE_LOCATION !in permissions) ||
                (Manifest.permission.ACCESS_COARSE_LOCATION !in permissions)) {

                ToastUtils.showToast("??????????????????????????????")
                XLog.w("?????????????????????")

            }
        }catch (e:Exception){
            XLog.e(TAG, "?????????????????????${e.message},${e.stackTraceToString()}")
            ToastUtils.showToast("?????????????????????${e.message}")

        }

    }

    //endregion

    //region ????????????

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        try {
            super.onActivityResult(requestCode, resultCode, data)

            var context = this
            if(resultCode == RESULT_OK){
                var uri = data?.data
                var path = FileUtil.getFileAbsolutePath(context, uri!!)
                XLog.d(TAG,"??????????????????${path}")
                selectFileListener(path!!, requestCode)

            }
        }catch (e:Exception){
            XLog.e(TAG, "?????????????????????${e.message},${e.stackTraceToString()}")
            ToastUtils.showToast("?????????????????????${e.message}")

        }

    }

    //endregion

    override fun userInfoUpdate(userInfo: LoginInfo?) {
        AppInfo.setUserInfo(userInfo)
        widgetDebug?.visibility =  if(AppInfo.enabledDebug) ConstraintLayout.VISIBLE else ConstraintLayout.GONE
    }
}