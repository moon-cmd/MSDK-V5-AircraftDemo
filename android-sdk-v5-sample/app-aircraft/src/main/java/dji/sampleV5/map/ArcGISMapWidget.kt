package dji.sampleV5.map

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.Nullable
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.geometry.*
import com.esri.arcgisruntime.layers.KmlLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.*
import com.esri.arcgisruntime.mapping.view.*
import com.esri.arcgisruntime.mapping.view.LocationDisplay.DataSourceStatusChangedListener
import com.esri.arcgisruntime.ogc.kml.*
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import dji.sampleV5.aircraft.BuildConfig
import dji.sampleV5.aircraft.R
import dji.sampleV5.util.DialogUtil
import dji.sampleV5.util.FileUtil
import dji.sampleV5.util.KmzManager
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.utils.common.LogUtils
import dji.v5.utils.common.ToastUtils
import dji.v5.ux.core.base.SchedulerProvider.ui
import dji.v5.ux.core.base.widget.ConstraintLayoutWidget
import dji.v5.ux.map.MapWidget.MapCenterLock
import dji.v5.ux.map.MapWidgetModel
import dji.v5.ux.mapkit.core.models.DJILatLng
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.android.synthetic.main.arcgis_map_widget.view.*
import java.util.concurrent.ExecutionException
import kotlin.math.abs
import kotlin.math.roundToInt


/**
 * ArcGIS 地图
 *
 */
class ArcGISMapWidget: ConstraintLayoutWidget<Object>{

    //region 属性

    private val TAG = "地图加载"

    var mapView: MapView? = null

    var btnExpandMap: ImageButton? = null

    private var widgetLoadMap: LinearLayout? = null
    private var btnLoadMmpkMap: Button? = null

    private var widgetFlightMainMenu: LinearLayout? = null

    private var btnAddFlightPoint: Button? = null

    private var btnCancelFlightPoint: Button? = null

    private var btnFlightPoint: Button? = null

    private var btnDeleteFlightPoint: Button? = null

    private var btnImportKml: Button? = null

    // kml 飞行控制
    private var widgetKmzFlightMenu: LinearLayout? = null
    private var btnKmzStartFlight: Button? = null
    private var btnKmzContinueFlight:Button? = null
    private var btnKmzPauseFlight:Button? = null
    private var btnKmzCancelFlight:Button? = null


    private var locationDisplay: LocationDisplay? = null

    private var widgetModel: MapWidgetModel? = null

    // 地图绘制
    private var graphicsOverlay: GraphicsOverlay? = null

    // 绘制点
    private var mouseMode = MouseMode.Default

    // 已选择图形
    private var selectGraphics: List<Graphic> = emptyList()

    // 已选择kml图形
    private var selectKmlElement: ArrayList<KmlPlacemark> = ArrayList()

    // 航线管理
     var kmzManager: KmzManager? = null

    // 是否允许多选
    private var allowMulSelect: Boolean = false

    // 最小化视图
    private var miniView: Boolean = false

    // 最大化事件监听
    private lateinit var expandMapEvent: (View?) -> Unit

    private var activityContext: Activity? = null

    // 文件选择
    private lateinit var selectFileEvent: () -> String

    private lateinit var mapPackage: MobileMapPackage

    //region Aircraft Marker Fields
    private var aircraftMarkerHeading = 0f
    private val aircraftMarker: DJIMarker? = null
    private val aircraftIcon: Drawable? = null
    private val aircraftMarkerEnabled = false
    private val aircraftIconAnchorX = 0.5f
    private val aircraftIconAnchorY = 0.5f
    private val ROTATION_ANIM_DURATION = 100
    private val FLIGHT_ANIM_DURATION = 130
    private val COUNTER_REFRESH_THRESHOLD = 200
    private val DO_NOT_UPDATE_ZOOM = -1
    private val centerRefreshCounter = 201
    private val isTouching = false
    private val mapCenterLockMode = MapCenterLock.AIRCRAFT
    private val gimbalYawMarker: DJIMarker? = null

    //endregion

    /**
     * 文件选择监听
     */
    var selectFileListenerEvent: (String, Int) -> Unit = fun (filePath: String, requestCode: Int){

        var fileName = filePath.substringAfterLast("/")
        var ext = fileName.substringAfterLast(".")


        when(requestCode){
            FileUtil.KmlSelectRequestCode -> {
                if(!ext.equals("kml", true) && !ext .equals("kmz",true)){
                    ToastUtils.showToast("所选文件不是kml，请选择kml文件！")
                    return
                }

                // 加载选择的kml文件
                loadKmlFile(filePath)
            }
            FileUtil.MmpkSelectRequestCode -> {
                if(!ext.equals("mmpk", true)  ){
                    ToastUtils.showToast("所选文件不是mmpk，请选择mmpk文件！")
                    return
                }

                // 加载选择的mmpk文件
                loadMobileMapPackage(filePath)
            }
        }


    }

    //region 构造函数

    constructor(context: Context) : this(context, null, 0)

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
//        initView(context, attrs,defStyleAttr)
    }

    //endregion

    //region 重载，视图初始化

    override fun initView(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) {
        inflate(context, R.layout.arcgis_map_widget, this)

        // 初始化一些变量值
        initVariable()
        // 地图初始化
        initMap(context)
        // 控件初始化
        initWidget()
        // 初始化事件
        initListenerEvent(context)

    }

    /**
     * 初始化一些变量值
     */
    private fun initVariable(){
        graphicsOverlay = GraphicsOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initMap(context: Context?) {
        LogUtils.i("开始初始化arcgis地图...")
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.ARCGIS_API_KEY)
        ArcGISRuntimeEnvironment.setLicense("runtimelite,1000,rud4449636536,none,NKMFA0PL4S0DRJE15166")

        mapView = findViewById(R.id.mapView)

        // create a map with the a topographic basemap
        val map = ArcGISMap(BasemapStyle.ARCGIS_TOPOGRAPHIC)
        // set the map to be displayed in this view
        mapView?.map = map
        mapView?.isAttributionTextVisible = false;

        mapView!!.graphicsOverlays.add(graphicsOverlay)
        // 定位
        locationDisplay = mapView?.locationDisplay
        locationDisplay?.addDataSourceStatusChangedListener(DataSourceStatusChangedListener { dataSourceStatusChangedEvent ->
            if (!dataSourceStatusChangedEvent.isStarted && dataSourceStatusChangedEvent.error != null) {
                LogUtils.e("定位权限未开启")
            }
        })
        locationDisplay?.autoPanMode = LocationDisplay.AutoPanMode.COMPASS_NAVIGATION
        locationDisplay?.startAsync()

    }

    /**
     * 控件初始化
     */
    private fun initWidget(){
        btnExpandMap = findViewById(R.id.btn_mapExpand)
        btnLoadMmpkMap = findViewById(R.id.btn_load_mmpk)
        widgetLoadMap = findViewById(R.id.widget_load_map)
        // 主菜单
        widgetFlightMainMenu = findViewById(dji.sampleV5.aircraft.R.id.widget_flight_main_menu)
        btnImportKml = findViewById(R.id.btn_KmlFlightMode)
        btnAddFlightPoint = findViewById(R.id.btn_add_flight_point)
        btnFlightPoint = findViewById(R.id.btn_flight_point)
        btnDeleteFlightPoint = findViewById(R.id.btn_delete_flight_point)
        btnCancelFlightPoint = findViewById(R.id.btn_cancel_flight_point)

        // kmz飞行控制
        widgetKmzFlightMenu = findViewById(R.id.widget_kmz_flight_menu)
        btnKmzStartFlight = findViewById(R.id.btn_kml_start_flight)
        btnKmzContinueFlight = findViewById(R.id.btn_kml_continue_flight)
        btnKmzPauseFlight = findViewById(R.id.btn_kml_pause_flight)
        btnKmzCancelFlight = findViewById(R.id.btn_kml_cancel_flight)
    }

    /**
     * 控件事件初始化
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initListenerEvent(context: Context){

        // 加载mmpk地图
        btnLoadMmpkMap?.setOnClickListener {
            // 选择文件
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            activityContext?.startActivityForResult(
                Intent.createChooser(intent, "请选择MMPK文件"), FileUtil.MmpkSelectRequestCode
            )
        }

        // 地图点击事件监听
        mapView?.onTouchListener = object : DefaultMapViewOnTouchListener(context, mapView) {

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // 获取屏幕点击位置
                val point = android.graphics.Point(e.x.roundToInt(), e.y.roundToInt())
                // 屏幕位置转换为地图位置
                val mapLocation: Point = mapView?.screenToLocation(point) ?: return super.onSingleTapUp(e)

                // 转换为84坐标系
                val wgs84Point:  Point =
                    GeometryEngine.project(mapLocation, SpatialReferences.getWgs84()) as  Point


                // 绘制要素选择
                var identifyFuture = mapView?.identifyGraphicsOverlayAsync(graphicsOverlay, point, 10.0, false, 10)
                identifyFuture!!.addDoneListener {
                    try {
                        //region 绘制图形选择

                        //  获取选中图形
                        selectGraphics = identifyFuture!!.get().graphics

                        // 清空历史选择
                        if(selectGraphics.isNotEmpty()) clearSection()

                        // 选中要素
                        for (graphic in selectGraphics) {
                            // select each graphic
                            graphic.isSelected = graphic.geometry is Point
                            // 不允许多选，则只选中第一个
                            if(!allowMulSelect) break
                        }

                        //endregion

                    } catch (ex: InterruptedException) {
                        LogUtils.e(TAG,"图形选择异常：${ex.message}")
                    } catch (ex: ExecutionException) {
                        LogUtils.e(TAG,"图形选择异常：${ex.message}")
                    }
                }

                // kml 要素选择
                var kmzHighlightStyle = KmlStyle()
                kmzHighlightStyle.lineStyle = KmlLineStyle(Color.rgb(0, 255, 255), 5.0)
                kmzHighlightStyle.polygonStyle = KmlPolygonStyle(Color.rgb(0, 255, 255))

                for (kmlLayer in mapView?.map?.operationalLayers!!){

                    var identify = mMapView
                        .identifyLayerAsync(kmlLayer, point, 5.0, false);

                    identify.addDoneListener {
                        try {
                            val result = identify.get().elements
                            // 清空历史选择
                            if(result.isNotEmpty()) clearSection()

                            for (geoElement in result) {

                                if(geoElement !is KmlPlacemark) continue

                                geoElement.isHighlighted = true
                                geoElement.highlightStyle = kmzHighlightStyle
                                selectKmlElement.add(geoElement)

                                if(!allowMulSelect) break

//                                val placemark = geoElement
//                                // Google Earth only displays the placemarks with description or extended data. To
//                                // match its behavior, add a description placeholder if the data source is empty
//                                if (placemark.description.isEmpty()) {
//                                    placemark.description = "Weather condition"
//                                }
//                                val calloutContent = TextView(activityContext)
//                                calloutContent.text = Html.fromHtml(placemark.balloonContent)
//
//                                // get callout, set content and show
//                                val callout = mMapView.callout
//                                callout.location = mMapView.screenToLocation(point)
//                                callout.content = calloutContent
//                                callout.show()
//                                break
                            }

                        }catch (error: java.lang.Exception){

                        }
                    }


                }


                when (mouseMode) {
                    MouseMode.Default -> {
                        // 图形选择
                    }
                    MouseMode.DrawFlyPoint -> {
                        var draw = true
                        for (graphic in selectGraphics){
                            if(graphic.geometry !is Point) continue
                            var  selectPoint = graphic.geometry as Point
                            if(abs(selectPoint.x - wgs84Point.x) < 0.001 && abs(selectPoint.y - wgs84Point.y) < 0.001){
                                draw = false
                                break
                            }
                        }
                        if(draw){
                            addMarker(wgs84Point.x, wgs84Point.y, null)
                            mouseMode = MouseMode.Default
                            // 显示航点飞行菜单
                            btnFlightPoint?.visibility = VISIBLE
                            btnDeleteFlightPoint?.visibility = VISIBLE
                            widgetFlightMainMenu?.visibility = VISIBLE
                            btnCancelFlightPoint?.visibility = VISIBLE
                            // 隐藏kml加载
                            btnImportKml?.visibility = GONE

//                            mapView?.setViewpoint(com.esri.arcgisruntime.mapping.Viewpoint(wgs84Point.y, wgs84Point.x, 10000.0))
                        }
                    }
                }

                return super.onSingleTapUp(e)
            }
        }

        // 添加航点
        btnAddFlightPoint?.setOnClickListener(OnClickListener { // 开启绘制飞行点
            mouseMode = MouseMode.DrawFlyPoint
            ToastUtils.showToast("点击地图添加航点")
            widgetFlightMainMenu?.visibility = GONE
        })

        // 飞到指定点
        btnFlightPoint?.setOnClickListener{

            if (selectGraphics.isEmpty()){
                DialogUtil.showTipDialog(context, null, "请先选择航点！", object:CommonCallbacks.CompletionCallback{
                    override fun onSuccess() {}
                    override fun onFailure(error: IDJIError) {}
                })

                return@setOnClickListener
            }
            var graphic = selectGraphics[0].geometry as Point
            DialogUtil.showTipDialog(context, "提示", "是否飞行到当前位置？", object: CommonCallbacks.CompletionCallback{
                override fun onSuccess() {

                    // 显示飞行信息
                    activityContext?.let { it1 ->
                        dji.sampleV5.util.DialogUtil.showFlightBasicInfo(it1, null, kmzManager, object: CommonCallbacks.CompletionCallbackWithParam<KmzManager>{
                            override fun onSuccess(t: KmzManager?) {
                                // 保存
                                kmzManager = t
                                // 开始飞行
                                kmzManager?.clearFlightPointInfo()
                                kmzManager?.addFlightPoint(graphic as Point,null)
                                kmzManager?.startTask(true)
                            }

                            override fun onFailure(error: IDJIError) { }
                        })
                    }
                }
                override fun onFailure(error: IDJIError) {}
            })
        }

        // 删除航点
        btnDeleteFlightPoint?.setOnClickListener{
            if(selectGraphics == null || selectGraphics.isEmpty()){

                DialogUtil.showTipDialog(context, "提示", "是否删除所有航点？", object: CommonCallbacks.CompletionCallback{
                    override fun onSuccess() {
                        // 清除所有航点
                        graphicsOverlay?.graphics?.clear()
                        selectGraphics = emptyList()
                    }

                    override fun onFailure(error: IDJIError) {

                    }

                })
                btnFlightPoint?.visibility = GONE
                btnDeleteFlightPoint?.visibility = GONE
                return@setOnClickListener
            }
            // 删除航点
            for (graphic in selectGraphics){
                graphicsOverlay?.graphics?.remove(graphic)
            }
            // 航点为空，隐藏飞行
            if (graphicsOverlay?.graphics?.isEmpty() == true){
                btnFlightPoint?.visibility = GONE
                btnDeleteFlightPoint?.visibility = GONE
            }
        }

        // 取消航点飞行
        btnCancelFlightPoint?.setOnClickListener {

            // 清空绘制航点
            graphicsOverlay?.graphics?.clear()

            btnFlightPoint?.visibility = GONE
            btnDeleteFlightPoint?.visibility = GONE
            btnCancelFlightPoint?.visibility = GONE

            btnImportKml?.visibility = VISIBLE
        }

        // 最大化地图
        btnExpandMap?.setOnClickListener ( OnClickListener {
            expandMapEvent(it)
        })

        // 导入kml
        btnImportKml?.setOnClickListener {
            // 选择文件
//            val intent = Intent(Intent.ACTION_GET_CONTENT)
//            intent.type = "*/*"
//            intent.addCategory(Intent.CATEGORY_OPENABLE)
//            activityContext?.startActivityForResult(
//                Intent.createChooser(intent, "请选择kml文件"), FileUtil.KmlSelectResponseCode
//            )

            // 测试 kml加载
            selectFileListenerEvent("/storage/emulated/0/Download/WeiXin/kmlTest.kmz", FileUtil.KmlSelectRequestCode)

        }

        // kmz 开始飞行
        btnKmzStartFlight?.setOnClickListener{

            if(selectKmlElement.isEmpty()){
                DialogUtil.showTipDialog(context, null, "请选择飞行航线!", object: CommonCallbacks.CompletionCallback{
                    override fun onSuccess() { }
                    override fun onFailure(error: IDJIError) {}
                })

                return@setOnClickListener
            }

            DialogUtil.showTipDialog(context, "提示", "是否沿当前航线飞行？", object: CommonCallbacks.CompletionCallback{

                override fun onSuccess() {
                    // 显示飞行信息
                    activityContext?.let { it1 ->
                        dji.sampleV5.util.DialogUtil.showFlightBasicInfo(it1, null, kmzManager, object: CommonCallbacks.CompletionCallbackWithParam<KmzManager>{
                            override fun onSuccess(t: KmzManager?) {
                                // 保存
                                kmzManager = t

                                // 开始飞行
                                kmzManager?.clearFlightPointInfo()
                                for (element in selectKmlElement){
                                    if(element.geometry is Point){
                                        kmzManager?.addFlightPoint(element.geometry as Point,null)
                                    }else if(element.geometry is Polyline){
                                        var polyline = element.geometry as Polyline
                                        for (point in  polyline.parts.partsAsPoints){
                                            kmzManager?.addFlightPoint(point, null)
                                        }
                                    }
                                }

                                kmzManager?.startTask(false)
                            }

                            override fun onFailure(error: IDJIError) { }
                        })
                    }
                }
                override fun onFailure(error: IDJIError) {}
            })

        }

        // kmz 继续飞行
        btnKmzContinueFlight?.setOnClickListener {
            kmzManager?.resumeTask()
        }

        // kmz 中止飞行
        btnKmzPauseFlight?.setOnClickListener {
            kmzManager?.pauseTask()
        }

        // kmz 取消飞行
        btnKmzCancelFlight?.setOnClickListener {
            widgetKmzFlightMenu?.visibility = GONE
            widgetFlightMainMenu?.visibility = VISIBLE

            kmzManager?.stopTask()

            mapView?.map?.operationalLayers?.clear()
        }
    }

    /**
     * 清空选择
     */
    private fun clearSection(){
        // 清空历史选择
        for (element in selectKmlElement){
            element.isHighlighted = false
        }
        selectKmlElement.clear()

        for (graphic in selectGraphics){
            graphic.isSelected = false
        }

        selectGraphics = emptyList()
    }


    override fun reactToModelChanges() {
        addReaction(
            widgetModel!!.productConnection.observeOn(ui()).subscribe { connected: Boolean ->
                if (connected) {
                    reactToHeadingChanges()?.let { addReaction(it) }
                    addReaction(
                        widgetModel!!.homeLocation
                            .observeOn(ui())
                            .subscribe(this::updateHomeLocation)
                    )
                    addReaction(
                        widgetModel!!.aircraftLocation
                            .observeOn(ui())
                            .subscribe(this::updateAircraftLocation)
                    )
                }
            })
    }

    //endregion

    //region 飞机起飞点更新

    //region 飞机起飞点更新
    /**
     * 更新飞机起飞点
     * @param homeLocation
     */
    private fun updateHomeLocation(homeLocation: LocationCoordinate2D) {
        if (homeLocation.getLatitude() === MapWidgetModel.INVALID_COORDINATE
            || homeLocation.getLongitude() === MapWidgetModel.INVALID_COORDINATE
        ) return
        val homePosition = DJILatLng(homeLocation.getLatitude(), homeLocation.getLongitude())
        if (mapView == null || !homePosition.isAvailable) return

        // 地图上标记起飞点
//        if (homeMarker != null) {
//            homeMarker.setPosition(homePosition);
//            updateCameraPosition();
//        } else {
//            initHomeOnMap(homePosition);
//        }
    }

    //endregion

    //region 飞机朝向更新

    /**
     * 飞机朝向更新
     * @return
     */
    private fun reactToHeadingChanges(): Disposable? {
        return null
    }
    //endregion

    //region 飞机位置更新

    private fun updateAircraftLocation(locationCoordinate3D: LocationCoordinate3D) {
        if (mapView == null) return
    }

    //endregion

    //endregion

    //region 生命周期

    /**
     * Calling this method from the corresponding method in your activity is required for Google Maps.
     */
    fun onPause() {
        if (mapView != null) {
            mapView!!.pause()
        }
    }

    /**
     * Calling this method from the corresponding method in your activity is required for Google Maps.
     */
    fun onResume() {
        if (mapView != null) {
            mapView!!.resume()
        }
    }


    /**
     * Calling this method from the corresponding method in your activity is required for Google Maps.
     */
    fun onDestroy() {
        if (mapView != null) {
            mapView!!.dispose()
        }
    }

    //endregion

    //region 图形绘制

    /**
     * 绘制点
     * @param x
     * @param y
     * @param simpleMarkerSymbol 点样式
     */
    fun addMarker(x: Double, y: Double, @Nullable simpleMarkerSymbol: SimpleMarkerSymbol?) {
        var simpleMarkerSymbol = simpleMarkerSymbol
        if (mapView == null) return

        val point = Point(x, y, SpatialReferences.getWgs84())

        // 创建点样式 -0xa8cd -0xff9c01 CCCCCC
        if (simpleMarkerSymbol == null) {
            simpleMarkerSymbol = SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, -0x0099FF, 15f)
            val blueOutlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, -0xCCCCCC, 2f)
            simpleMarkerSymbol.outline = blueOutlineSymbol
        }
        graphicsOverlay?.graphics?.add(Graphic(point, simpleMarkerSymbol))
    }

    /**
     * 绘制线
     */
    fun addPolyline(points: List<Point>){

        if(points.isEmpty()) return

        val polylinePoints = PointCollection(SpatialReferences.getWgs84()).apply {
            for (point in points){
                add(point.x, point.y)
            }
        }

        val polyline = Polyline(polylinePoints)

        // create a blue line symbol for the polyline
        val polylineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SHORT_DASH, -0X00ccff, 3f)

        // create a polyline graphic with the polyline geometry and symbol
        val polylineGraphic = Graphic(polyline, polylineSymbol)

        // add the polyline graphic to the graphics overlay
        graphicsOverlay?.graphics?.add(polylineGraphic)

    }

    // endregion
    
    //region 视图控制

    /**
     * 视图最大化/最小化
     */
    open fun minimizedMap(mini: Boolean) {
        // 最大化按钮
        btnExpandMap?.visibility = if (mini) View.VISIBLE else View.GONE
        // 航点菜单
        widgetFlightMainMenu?.visibility = if (mini) View.GONE else View.VISIBLE
        // kml菜单
        widgetKmzFlightMenu?.visibility = if(mini) GONE else widgetKmzFlightMenu?.visibility!!
        // 地图加载菜单
        widgetLoadMap?.visibility = if (mini) GONE else VISIBLE

    }

    open fun setWidgetModel(widgetModel: MapWidgetModel?){
        this.widgetModel = widgetModel
    }

    /**
     * 点击地图放大监听
     */
    open fun setExpandMapClickListener(function: (View?) -> Unit) {
        this.expandMapEvent = function
    }

    //endregion

    //region 加载kml

    /**
     * 加载kml文件
     */
    private fun loadKmlFile(filePath: String){
        val kmlDataset = KmlDataset(filePath)

        var kmlLayer = KmlLayer(kmlDataset)

        // 加载图层
        mapView?.map?.operationalLayers?.add(kmlLayer)

        // kml 加载监听
        kmlDataset.addDoneLoadingListener {
            if(kmlDataset.loadStatus != LoadStatus.LOADED){
                ToastUtils.showToast("kml 文件加载失败，${kmlDataset.loadError.cause!!.message}")
                LogUtils.e(TAG, "kml 文件加载失败，${kmlDataset.loadError.cause!!.message}")
            }else{
                widgetFlightMainMenu?.visibility = GONE
                widgetKmzFlightMenu?.visibility = VISIBLE
                // 清除绘制的航点
                graphicsOverlay?.graphics?.clear()


                mapView?.setViewpoint(Viewpoint(kmlLayer.fullExtent))

            }
        }

    }

    //endregion

    fun setActiveContext(context: Activity){
        this.activityContext = context
//        ToastUtils.init(context)
    }


    /**
     * 加载mmpk文件
     */
    private fun loadMobileMapPackage(filePath: String){

        mapPackage = MobileMapPackage(filePath).also {
            it.loadAsync()
        }

        // 加载监听
        mapPackage.addDoneLoadingListener {
            // 加载成功
            if (mapPackage.loadStatus === LoadStatus.LOADED && mapPackage.maps.isNotEmpty()) {
                // add the map from the mobile map package to the MapView
                mapView?.map = mapPackage.maps[0]

                // 视图缩放


                ToastUtils.showToast("地图加载成功！")
            } else {
                ToastUtils.showToast("地图加载失败，mapPackage.loadError.message")
                LogUtils.e(mapPackage.loadError.message)
            }
        }

    }
}