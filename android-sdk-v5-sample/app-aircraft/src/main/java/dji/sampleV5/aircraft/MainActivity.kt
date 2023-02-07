package dji.sampleV5.aircraft

import android.os.Bundle
import dji.v5.ux.map.MapWidget
import dji.v5.ux.map.MapWidget.OnMapReadyListener
import dji.v5.ux.mapkit.core.maps.DJIMap

/**
 * 主界面
 */
class MainActivity : InitActivity() {

    private lateinit var mapWidget: MapWidget

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)
        super.onCreate(savedInstanceState)
        mapWidget = findViewById(dji.v5.ux.R.id.widget_map)

        mapWidget.initAMap(OnMapReadyListener { map: DJIMap ->
            // map.setOnMapClickListener(latLng -> onViewClick(mapWidget));
            val uiSetting = map.uiSettings
            // 设置地图缩放控件
            uiSetting?.setZoomControlsEnabled(false)
            // 设置罗盘控件
            uiSetting?.setCompassEnabled(true)

            uiSetting?.setMyLocationButtonEnabled(true)

            //uiSetting?.setTiltGesturesEnabled(true)
        })
        mapWidget.onCreate(savedInstanceState)

    }

    override fun onResume() {
        super.onResume()
        mapWidget.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapWidget.onDestroy()
    }

    override fun onPause() {

        mapWidget.onPause()
        super.onPause()
    }
}