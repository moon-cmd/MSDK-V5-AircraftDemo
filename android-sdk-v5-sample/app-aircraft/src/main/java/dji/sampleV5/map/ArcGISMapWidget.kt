package dji.sampleV5.map

import android.annotation.SuppressLint
import android.content.Context
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
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.view.*
import com.esri.arcgisruntime.mapping.view.LocationDisplay.DataSourceStatusChangedListener
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import dji.sampleV5.aircraft.BuildConfig
import dji.sampleV5.aircraft.R
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.utils.common.LogUtils
import dji.v5.ux.core.base.SchedulerProvider.ui
import dji.v5.ux.core.base.widget.ConstraintLayoutWidget
import dji.v5.ux.map.MapWidget.MapCenterLock
import dji.v5.ux.map.MapWidgetModel
import dji.v5.ux.mapkit.core.models.DJILatLng
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker
import io.reactivex.rxjava3.disposables.Disposable
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

    private var btnPointFlightMode: Button? = null

    private var btnImportKml: Button? = null

    private var widgetFlightMainMenu: LinearLayout? = null

    private var locationDisplay: LocationDisplay? = null

    private var widgetModel: MapWidgetModel? = null

    // 绘制点
    private var mouseMode = MouseMode.Default

    // 最小化视图
    private var miniView: Boolean = false

    // 最大化事件监听
    private lateinit var expandMapEvent: (View?) -> Unit

    // 加载kml
    private lateinit var loadKmlEvent: (View?) -> Unit

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

//    constructor(context: Context, attrs:AttributeSet ,
//                defStyleAttr:Int) : super(context) {
//    }
//
//    constructor(context: Context, attrs:AttributeSet) : super(context) {
//    }
//    constructor(context: Context) : super(context) {
//    }

    constructor(context: Context) : this(context, null, 0)

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
//        initView(context, attrs,defStyleAttr)
    }


    //region 重载，视图初始化

    override fun initView(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) {
        inflate(context, R.layout.arcgis_map_widget, this)
        initMap(context)
        btnExpandMap = findViewById(R.id.btn_mapExpand)
        btnPointFlightMode = findViewById(R.id.btn_pointFlightMode)
        btnImportKml = findViewById(R.id.btn_KmlFlightMode)
        widgetFlightMainMenu = findViewById(R.id.widget_flight_main_menu)


        // 航点飞行
        btnPointFlightMode = findViewById(R.id.btn_pointFlightMode)
        btnPointFlightMode?.setOnClickListener(OnClickListener { // 开启绘制飞行点
            mouseMode = MouseMode.DrawFlyPoint
            // 隐藏飞行功能菜单按钮
            widgetFlightMainMenu?.visibility = GONE
        })

        btnExpandMap?.setOnClickListener ( OnClickListener {
            expandMapEvent(it)
        })

        btnImportKml?.setOnClickListener {
            loadKmlEvent(it)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun initMap(context: Context?) {
        LogUtils.i("开始初始化arcgis地图...")
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.ARCGIS_API_KEY)
        mapView = findViewById(R.id.mapView)


        // create a map with the a topographic basemap
        val map = ArcGISMap(BasemapStyle.ARCGIS_TOPOGRAPHIC)
        // set the map to be displayed in this view
        mapView?.map = map

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
                when (mouseMode) {
                    MouseMode.Default -> {}
                    MouseMode.DrawFlyPoint -> {
                        addMarker(wgs84Point.x, wgs84Point.y, null)
                        mapView?.setViewpoint(com.esri.arcgisruntime.mapping.Viewpoint(wgs84Point.y, wgs84Point.x, 10000.0))
                    }
                }
                return super.onSingleTapUp(e)
            }
        }

        // 定位
        locationDisplay = mapView?.locationDisplay
        locationDisplay?.addDataSourceStatusChangedListener(DataSourceStatusChangedListener { dataSourceStatusChangedEvent ->
            if (!dataSourceStatusChangedEvent.isStarted && dataSourceStatusChangedEvent.error != null) {
//                    Toast.makeText(this, getString(R.string.uxsdk_location_permission_denied), Toast.LENGTH_SHORT).show();
                LogUtils.e("定位权限未开启")
            }
            // 视图缩放到当前位置
//                Point position = (Point) GeometryEngine.project(locationDisplay.getMapLocation(), SpatialReferences.getWgs84());
//                mapView.setViewpoint(new Viewpoint(position.getY(), position.getX(), 10000));
        })
        locationDisplay?.autoPanMode = LocationDisplay.AutoPanMode.COMPASS_NAVIGATION
        locationDisplay?.startAsync()

//        mapView.setViewpoint(new Viewpoint(locationDisplay.loca, -117.195800, 10000));
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
        val graphicsOverlay = GraphicsOverlay()
        mapView!!.graphicsOverlays.add(graphicsOverlay)
        val point = Point(x, y, SpatialReferences.getWgs84())

        // 创建点样式 -0xa8cd -0xff9c01
        if (simpleMarkerSymbol == null) {
            simpleMarkerSymbol = SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, -0xa8cd, 15f)
            val blueOutlineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, -0xff9c01, 2f)
            simpleMarkerSymbol.outline = blueOutlineSymbol
        }
        graphicsOverlay.graphics.add(Graphic(point, simpleMarkerSymbol))
    }

    // endregion
    
    //region 视图控制
    
    open fun minimizedMap(mini: Boolean) {
        btnExpandMap?.visibility = if (mini) View.VISIBLE else View.GONE
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

    open fun  setLoadKmlClickListener(function: (View?) -> Unit){
        this.loadKmlEvent = function
    }

    //endregion

}