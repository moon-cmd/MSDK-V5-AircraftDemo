package dji.sampleV5.map

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.geometry.*
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.view.*
import com.esri.arcgisruntime.mapping.view.LocationDisplay.DataSourceStatusChangedListener
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import dji.sampleV5.aircraft.BuildConfig
import dji.sampleV5.aircraft.R
import dji.sampleV5.util.DialogUtil
import dji.sampleV5.util.KmzManager
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.utils.common.LogUtils
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
class ArcGISMapWidget: ConstraintLayoutWidget<Object> {

    //region 属性

    private val TAG = "地图加载"

    var mapView: MapView? = null

    var btnExpandMap: ImageButton? = null


    private var widgetFlightMainMenu: LinearLayout? = null

    private var btnAddFlightPoint: Button? = null

    private var btnFlightPoint: Button? = null

    private var btnDeleteFlightPoint: Button? = null

    private var btnImportKml: Button? = null


    private var locationDisplay: LocationDisplay? = null

    private var widgetModel: MapWidgetModel? = null

    // 地图绘制
    private var graphicsOverlay: GraphicsOverlay? = null

    // 绘制点
    private var mouseMode = MouseMode.Default

    // 已选择图形
    private var selectGraphics: List<Graphic> = emptyList()

    // 航线管理
     var kmzManager: KmzManager? = null

    // 是否允许多选
    private var allowMulSelect: Boolean = false

    // 最小化视图
    private var miniView: Boolean = false

    // 最大化事件监听
    private lateinit var expandMapEvent: (View?) -> Unit

    // 加载kml
    private lateinit var loadKmlEvent: (View?) -> Unit

    var activityContext: Activity? = null

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
        mapView = findViewById(R.id.mapView)

        // create a map with the a topographic basemap
        val map = ArcGISMap(BasemapStyle.ARCGIS_TOPOGRAPHIC)
        // set the map to be displayed in this view
        mapView?.map = map
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
        widgetFlightMainMenu = findViewById(R.id.widget_flight_main_menu)
        // 航点飞行
        btnImportKml = findViewById(R.id.btn_KmlFlightMode)
        btnAddFlightPoint = findViewById(R.id.btn_add_flight_point)
        btnFlightPoint = findViewById(R.id.btn_flight_point)
        btnDeleteFlightPoint = findViewById(R.id.btn_delete_flight_point)
    }

    /**
     * 控件事件初始化
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initListenerEvent(context: Context){
        // 地图点击事件监听
        mapView?.onTouchListener = object : DefaultMapViewOnTouchListener(context, mapView) {

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // 获取屏幕点击位置
                val point = android.graphics.Point(e.x.roundToInt(), e.y.roundToInt())
                // 屏幕位置转换为地图位置
                val mapLocation:  Point? = mapView?.screenToLocation(point)
                // 转换为84坐标系
                val wgs84Point:  Point =
                    GeometryEngine.project(mapLocation, SpatialReferences.getWgs84()) as  Point

                var identifyFuture = mapView?.identifyGraphicsOverlayAsync(graphicsOverlay, point, 10.0, false, 10)
                identifyFuture!!.addDoneListener {
                    try {
                        //  获取选中图形
                        selectGraphics = identifyFuture!!.get().graphics

                        // 清空历史选择
                        if(selectGraphics.isNotEmpty()){
                            graphicsOverlay?.clearSelection()
                        }

                        // 选中要素
                        for (graphic in selectGraphics) {
                            // select each graphic
                            graphic.isSelected = true
                            // 不允许多选，则只选中第一个
                            if(!allowMulSelect) break
                        }
                    } catch (ex: InterruptedException) {
                        LogUtils.e(TAG,"图形选择异常：${ex.message}")
                    } catch (ex: ExecutionException) {
                        LogUtils.e(TAG,"图形选择异常：${ex.message}")
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
//                            mapView?.setViewpoint(com.esri.arcgisruntime.mapping.Viewpoint(wgs84Point.y, wgs84Point.x, 10000.0))
                        }
                    }
                }
                return super.onSingleTapUp(e)
            }
        }

        // 开启航点飞行
        btnAddFlightPoint?.setOnClickListener(OnClickListener { // 开启绘制飞行点
            mouseMode = MouseMode.DrawFlyPoint
            // 显示航点飞行菜单
            btnFlightPoint?.visibility = VISIBLE
            btnDeleteFlightPoint?.visibility = VISIBLE
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
                            }

                            override fun onFailure(error: IDJIError) {
                                // 取消
                            }

                        })
                    }

                    // 开始飞行
                    kmzManager?.addFlightPoint(graphic.x, graphic.y,null)
                    kmzManager?.startTask()

                    // 绘制飞行线
                    var points:List<Point> = emptyList()
                    if(kmzManager?.historyFlightPoint?.count()!! < 1){
                        points.plus(kmzManager?.homeLocation)
                    }else{
//                        var v = kmzManager?.historyFlightPoint?

                    }
                    points.plus(graphic)
                    addPolyline(points)
                }
                override fun onFailure(error: IDJIError) {

                }

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

        // 最大化地图
        btnExpandMap?.setOnClickListener ( OnClickListener {
            expandMapEvent(it)
        })

        // 导入kml
        btnImportKml?.setOnClickListener {
            loadKmlEvent(it)
        }
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

    fun addPolyline(points: List<Point>){

        val polylinePoints = PointCollection(SpatialReferences.getWgs84()).apply {
            for (point in points){
                add(point.x, point.y)
            }
        }

        val polyline = Polyline(polylinePoints)

        // create a blue line symbol for the polyline
        val polylineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SHORT_DASH, -0xff9c01, 3f)

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

    open fun setLoadKmlClickListener(function: (View?) -> Unit){
        this.loadKmlEvent = function
    }

    //endregion

}