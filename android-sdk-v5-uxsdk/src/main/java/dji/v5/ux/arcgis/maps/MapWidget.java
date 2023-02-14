package dji.v5.ux.arcgis.maps;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.sdk.keyvalue.value.common.LocationCoordinate3D;
import dji.v5.utils.common.LogUtils;
import dji.v5.ux.BuildConfig;
import dji.v5.ux.R;
import dji.v5.ux.core.base.SchedulerProvider;
import dji.v5.ux.core.base.widget.ConstraintLayoutWidget;
import dji.v5.ux.core.util.RxUtil;
import dji.v5.ux.map.MapWidgetModel;
import dji.v5.ux.mapkit.core.camera.DJICameraUpdate;
import dji.v5.ux.mapkit.core.camera.DJICameraUpdateFactory;
import dji.v5.ux.mapkit.core.models.DJICameraPosition;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.BasemapStyle;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;

public class MapWidget extends ConstraintLayoutWidget<Object> {

    private String TAG = "地图加载";

    public MapView mapView;

    public View rootView;

    public ImageButton imageButton;

    private MapWidgetListener mapWidgetListener;

    private LocationDisplay locationDisplay;

    public MapWidgetModel widgetModel;


    //region Aircraft Marker Fields
    private float aircraftMarkerHeading;
    private DJIMarker aircraftMarker;
    private Drawable aircraftIcon;
    private boolean aircraftMarkerEnabled;
    private float aircraftIconAnchorX = 0.5f;
    private float aircraftIconAnchorY = 0.5f;
    private static final int ROTATION_ANIM_DURATION = 100;
    private static final int FLIGHT_ANIM_DURATION = 130;
    private static final int COUNTER_REFRESH_THRESHOLD = 200;
    private static final int DO_NOT_UPDATE_ZOOM = -1;
    private int centerRefreshCounter = 201;
    private boolean isTouching = false;
    private dji.v5.ux.map.MapWidget.MapCenterLock mapCenterLockMode = dji.v5.ux.map.MapWidget.MapCenterLock.AIRCRAFT;
    private DJIMarker gimbalYawMarker;
    //endregion

    //region 构造函数
    
    public MapWidget(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MapWidget(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MapWidget(@NonNull Context context) {
        super(context);
    }

    //endregion

    public void setMapWidgetListener(MapWidgetListener mapWidgetListener){
        this.mapWidgetListener = mapWidgetListener;
    }



    @Override
    protected void initView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {

        inflate(context, R.layout.uxsdk_arcgis_map, this);
        rootView = ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content);

        imageButton = findViewById(R.id.btn_mapExpand);
        imageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mapWidgetListener != null){
                    mapWidgetListener.onClickExpandBtn(view);
                }
            }
        });

        Button btnPointFlightMode = findViewById(R.id.btn_pointFlightMode);
        btnPointFlightMode.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                addMarker(-118.8065, 34.0005,null);
//                mapView.setViewpoint（Viewpoint（34.0270， -118.8050， 72000.0))
                mapView.setViewpoint(new Viewpoint(34.0270, -118.8050, 72000.0));
            }
        });

        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
    }


    @Override
    protected void reactToModelChanges() {

        addReaction(widgetModel.getProductConnection().observeOn(SchedulerProvider.ui()).subscribe(connected -> {
            if (connected) {
                addReaction(reactToHeadingChanges());
                addReaction(widgetModel.getHomeLocation()
                        .observeOn(SchedulerProvider.ui())
                        .subscribe(this::updateHomeLocation));
                addReaction(widgetModel.getAircraftLocation()
                        .observeOn(SchedulerProvider.ui())
                        .subscribe(this::updateAircraftLocation));
            }
        }));

    }

    //region 飞机起飞点更新


    /**
     * 更新飞机起飞点
     * @param homeLocation
     */
    private void updateHomeLocation(LocationCoordinate2D homeLocation) {
        if (homeLocation.getLatitude() == MapWidgetModel.INVALID_COORDINATE
                || homeLocation.getLongitude() == MapWidgetModel.INVALID_COORDINATE) return;
        DJILatLng homePosition = new DJILatLng(homeLocation.getLatitude(), homeLocation.getLongitude());
        if (mapView == null || !homePosition.isAvailable()) return;

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
    private Disposable reactToHeadingChanges() {
        return Flowable.combineLatest(widgetModel.getAircraftHeading(),
                        widgetModel.getGimbalHeading(), Pair::create)
                .observeOn(SchedulerProvider.ui())
                .subscribe(values -> {
                    updateAircraftHeading(values.first.floatValue());
                    setGimbalHeading(values.first.floatValue(), values.second.floatValue());
                }, RxUtil.logErrorConsumer(TAG, "react to Heading Update "));
    }

    /**
     * 飞机朝向
     * @param aircraftHeading
     */
    private void updateAircraftHeading(float aircraftHeading) {
        if (((aircraftHeading >= 0 && aircraftMarkerHeading >= 0) ||
                (aircraftHeading <= 0 && aircraftMarkerHeading <= 0)) && mapView != null) {
//            animateAircraftHeading(aircraftMarkerHeading,
//                    aircraftHeading - mapView.getCameraPosition().bearing,
//                    aircraftHeading);

            animateAircraftHeading(aircraftMarkerHeading,
                    (float) (aircraftHeading - mapView.getMapRotation()),
                    aircraftHeading);
        } else {
            setAircraftHeading(aircraftHeading);
        }
    }
    private void setAircraftHeading(float aircraftHeading) {
        if (mapView == null) return;
        if (aircraftMarker != null) {
            rotateAircraftMarker((float) (aircraftHeading -  mapView.getMapRotation()));
        }
        aircraftMarkerHeading = (float) (aircraftHeading -  mapView.getMapRotation());
    }
    private void rotateAircraftMarker(float rotation) {
        if (aircraftMarker != null) {
            aircraftMarker.setRotation(rotation);
        }
    }
    private void animateAircraftHeading(final float fromPosition, final float toPosition, float aircraftHeading) {
        if (mapView == null || aircraftMarker == null) return;

        //rotation animation
        ValueAnimator rotateAnimation =
                ValueAnimator.ofFloat(aircraftMarkerHeading, (float) (aircraftHeading - mapView.getMapRotation()));
        rotateAnimation.setDuration(ROTATION_ANIM_DURATION);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.addUpdateListener(valueAnimator -> {
            float progress = valueAnimator.getAnimatedFraction();
            float rotation = (toPosition - fromPosition) * progress + fromPosition;
            rotateAircraftMarker(rotation);
        });
        rotateAnimation.start();
        aircraftMarkerHeading = (float) (aircraftHeading - mapView.getMapRotation());
    }
    private void setGimbalHeading(float aircraftHeading, float gimbalHeading) {
        if (mapView == null) return;
        if (gimbalYawMarker != null) {
            rotateGimbalMarker((float) (gimbalHeading + aircraftHeading - mapView.getMapRotation()));
        }
    }
    private void rotateGimbalMarker(float rotation) {
        if (gimbalYawMarker != null) {
            gimbalYawMarker.setRotation(rotation);
        }
    }

    //endregion

    //region 飞机位置更新

    private void updateAircraftLocation(LocationCoordinate3D locationCoordinate3D) {
        if (mapView == null) return;

    }

    //endregion

    public void initMap(){
        LogUtils.i("开始初始化arcgis地图...");
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.ARCGIS_API_KEY);
        mapView = findViewById(R.id.mapView);



        // create a map with the a topographic basemap
        ArcGISMap map = new ArcGISMap(BasemapStyle.ARCGIS_TOPOGRAPHIC);
        // set the map to be displayed in this view
        mapView.setMap(map);
        mapView.setViewpoint(new Viewpoint(34.056295, -117.195800, 10000));

        // 定位
        locationDisplay = mapView.getLocationDisplay();
        locationDisplay.addDataSourceStatusChangedListener(new LocationDisplay.DataSourceStatusChangedListener() {
            @Override
            public void onStatusChanged(LocationDisplay.DataSourceStatusChangedEvent dataSourceStatusChangedEvent) {
                if(!dataSourceStatusChangedEvent.isStarted() && dataSourceStatusChangedEvent.getError() != null){
//                    Toast.makeText(this, getString(R.string.uxsdk_location_permission_denied), Toast.LENGTH_SHORT).show();
                    LogUtils.e("定位权限未开启");
                }
            }

        });

        locationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.COMPASS_NAVIGATION);
        locationDisplay.startAsync();
    }

    public void onCreate(@Nullable Bundle saveInstanceState) {

    }


    /**
     * Calling this method from the corresponding method in your activity is required for Google Maps.
     */
    public void onPause() {
        if (mapView != null) {
            mapView.pause();
        }
    }

    /**
     * Calling this method from the corresponding method in your activity is required for Google Maps.
     */
    public void onResume() {
        if (mapView != null) {
            mapView.resume();
        }
    }


    /**
     * Calling this method from the corresponding method in your activity is required for Google Maps.
     */
    public void onDestroy() {
        if (mapView != null) {
            mapView.dispose();
        }
    }


    public void refreshView(){
        this.invalidate();
        mapView.resume();

    }

    /**
     * 开启定位
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    public void startLocation(int requestCode, String[] permissions,
                             int[] grantResults){

        if(locationDisplay == null) return;

        if(grantResults.length > 0 && grantResults[0] ==  PackageManager.PERMISSION_GRANTED){
            locationDisplay.startAsync();
        }
    }


    /**
     * 绘制点
     * @param x
     * @param y
     * @param simpleMarkerSymbol 点样式
     */
    public void addMarker(double x, double y, @Nullable SimpleMarkerSymbol simpleMarkerSymbol){
        if(mapView == null) return;
        GraphicsOverlay graphicsOverlay = new GraphicsOverlay();
        mapView.getGraphicsOverlays().add(graphicsOverlay);

        Point point = new Point(x, y, SpatialReferences.getWgs84());

        // 创建点样式
        if(simpleMarkerSymbol == null){
            simpleMarkerSymbol = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, 0xFFFF0000, 15);
            SimpleLineSymbol blueOutlineSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, -0xff9c01, 2f);
            simpleMarkerSymbol.setOutline(blueOutlineSymbol);
        }
        graphicsOverlay.getGraphics().add(new Graphic(point, simpleMarkerSymbol));

//        Point pierPoint = new Point(-118.4978, 34.0086, SpatialReferences.getWgs84());
    }

}

