package dji.sampleV5.util

import androidx.lifecycle.LifecycleOwner
import dji.sdk.wpmz.value.mission.WaylineExecuteWaypoint
import dji.v5.ux.mapkit.core.models.DJILatLng

/**
 * Kmz 文件操作事件
 */
interface KmzManagerEvent {

    /**
     * 绘制线
     */
    fun drawLinesEvent(points: List<WaylineExecuteWaypoint>){}

    /**
     * 绘制点
     */
    fun drawPointEvent(point: DJILatLng, pointIndex: Int){}
}