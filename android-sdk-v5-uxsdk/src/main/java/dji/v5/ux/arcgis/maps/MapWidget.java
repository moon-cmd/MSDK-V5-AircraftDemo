package dji.v5.ux.arcgis.maps;

import static android.content.Context.WINDOW_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dji.v5.utils.common.LogUtils;
import dji.v5.ux.BuildConfig;
import dji.v5.ux.R;
import dji.v5.ux.core.base.widget.ConstraintLayoutWidget;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.BasemapStyle;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.MapView;

public class MapWidget extends ConstraintLayoutWidget<Object> implements View.OnTouchListener {

    private MapView mapView;

    public View rootView;

    public MapWidget(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MapWidget(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MapWidget(@NonNull Context context) {
        super(context);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

//        Resources rs = getResources();
//
//        DisplayMetrics dm = rs.getDisplayMetrics();
//
//        if(Math.abs(rootView.getWidth() - dm.widthPixels) > 1){
//            ViewGroup.LayoutParams layoutParams = rootView.getLayoutParams();
//            layoutParams.width = dm.widthPixels;
//            layoutParams.height = dm.heightPixels;
//            mapView.resume();
//        }

        return false;
    }

    @Override
    protected void initView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        inflate(context, R.layout.uxsdk_arcgis_map, this);
        rootView = ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content);
    }

    @Override
    protected void reactToModelChanges() {

    }

    public void initMap(){
        LogUtils.i("开始初始化arcgis地图...");
        ArcGISRuntimeEnvironment.setApiKey(BuildConfig.ARCGIS_API_KEY);
        mapView = findViewById(R.id.mapView);

        // create a map with the a topographic basemap
        ArcGISMap map = new ArcGISMap(BasemapStyle.ARCGIS_TOPOGRAPHIC);
        // set the map to be displayed in this view
        mapView.setMap(map);
        mapView.setViewpoint(new Viewpoint(34.056295, -117.195800, 10000));

//        mapView.setOnTouchListener(this);
    }

    public void onCreate(@Nullable Bundle saveInstanceState) {

    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        super.setOnClickListener(l);
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
}
