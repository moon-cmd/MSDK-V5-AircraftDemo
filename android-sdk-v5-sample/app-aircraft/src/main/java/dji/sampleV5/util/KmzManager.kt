package dji.sampleV5.util

import android.text.TextUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import com.amap.api.maps.model.Poi
import com.elvishew.xlog.XLog
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReference
import com.esri.arcgisruntime.geometry.SpatialReferences
import dji.sampleV5.moduleaircraft.data.FlightControlState
import dji.sampleV5.moduleaircraft.models.WayPointV3VM
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.wpmz.value.mission.WaylineExecuteWaypoint
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.KeyManager
import dji.v5.manager.aircraft.waypoint3.WPMZParserManager
import dji.v5.manager.aircraft.waypoint3.model.WaypointMissionExecuteState
import dji.v5.utils.common.*
import dji.v5.ux.mapkit.core.models.DJILatLng
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.dom4j.Document
import org.dom4j.Element
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import java.io.File
import java.io.FileOutputStream

/***
 * KMZ 航线文件管理
 */

class KmzManager{

    private val TAG: String = "航线管理"

    private var wayPointV3VM: WayPointV3VM = WayPointV3VM()
    private var viewLifecycleOwner: LifecycleOwner? =  null

    private val WAYPOINT_SAMPLE_FILE_NAME: String = "waypointsample.kmz"
    private val WAYPOINT_SAMPLE_FILE_CACHE_DIR: String = "waypoint/cache/"
    private val WAYPOINT_FILE_TAG = ".kmz"
    private var unzipChildDir = "temp/"
    private var unzipDir = "wpmz/"

    private val rootDir = AppInfo.WAYPOINT_ROOT_DIR

    // 当前航线文件路径
    var curMissionPath: String =  AppInfo.WAYPOINT_ROOT_DIR + File.separator + WAYPOINT_SAMPLE_FILE_NAME

    // 模板文件
    private var templateFilePath: String = ""
    // 执行文件
    private var waylineFilePath: String = ""

    // 航线转态
    private var curMissionExecuteState: WaypointMissionExecuteState? = null

    private var selectWaylines: ArrayList<Int> =  arrayListOf<Int>()

    // 飞行高度
    var flightHeight: Double = 100.0
    // 飞行速度
    var flightSpeed: Double = 5.0

    var terrainFollowingMode: Boolean = true

    // 飞行状态信息：航高、位置...
    var flightStateInfo:  FlightControlState? = null

    // 历史飞行航点
    var historyFlightPoint: ArrayList<Point> = arrayListOf<Point>()

    var homeLocation: Point? = null

    private lateinit var drawLineEvent: (ArrayList<Point>) -> Unit

    constructor( wayPointV3VM: WayPointV3VM,viewLifecycleOwner: LifecycleOwner){
        this.wayPointV3VM = wayPointV3VM
        this.viewLifecycleOwner = viewLifecycleOwner
        // 航线文件初始化
        initKmzFile()
        // 飞行监听
        initFlightListenerEvent()
        // 清除
        clearFlightPointInfo()
    }

    /**
     * 航线文件初始化
     */
    private fun initKmzFile(){
        try {
            // 移动模板文件到临时目录
            val dir = File(rootDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val cacheDirName = DiskUtil.getExternalCacheDirPath(
                ContextUtil.getContext(),
                WAYPOINT_SAMPLE_FILE_CACHE_DIR
            )
            val cacheDir = File(cacheDirName)

            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val destPath = rootDir + WAYPOINT_SAMPLE_FILE_NAME
            if (!File(destPath).exists()) {
                FileUtils.copyAssetsFile(
                    ContextUtil.getContext(),
                    WAYPOINT_SAMPLE_FILE_NAME,
                    destPath
                )
            }
            // 解压航线文件
            val unzipFolder = File(rootDir, unzipChildDir)
            WPMZParserManager.unZipFolder(ContextUtil.getContext(), curMissionPath, unzipFolder.path, false)

            templateFilePath = rootDir + unzipChildDir + unzipDir + WPMZParserManager.TEMPLATE_FILE
            waylineFilePath = rootDir + unzipChildDir + unzipDir + WPMZParserManager.WAYLINE_FILE

            // 航线初始化
            for (waylineID in wayPointV3VM.getAvailableWaylineIDs(curMissionPath)){
                selectWaylines.add(waylineID)
            }
        }catch (e:Exception){
            ToastUtils.showToast("航线文件初始化失败，${e.message}")
            XLog.e(TAG, "航线文件初始化异常，${e.message},${e.stackTraceToString()}")
        }


    }

    /**
     * 航线飞行事件监听初始化
     */
    private fun initFlightListenerEvent(){

        // 状态监听
        wayPointV3VM?.addMissionStateListener() {
            curMissionExecuteState = it
            XLog.i(TAG, "执行状况：${it.name}")
        }
        // 添加航线监听
        wayPointV3VM?.addWaylineExecutingInfoListener() {
            XLog.i(TAG, "航线信息：")
            XLog.i(TAG, "航线ID：${it.waylineID}")
            XLog.i(TAG, "航线索引：${it.currentWaypointIndex}")
            XLog.i(TAG, "航线名称：${if (curMissionExecuteState == WaypointMissionExecuteState.READY) "" else it.missionFileName}")

        }

        // 航线上传监听
        viewLifecycleOwner?.let {
            wayPointV3VM?.missionUploadState?.observe(it) {
                it?.let {
                    when {
                        it.error != null -> {
                            XLog.i(TAG, "航线上传失败，${getErrorMsg(it.error!!)}")
                        }
                        it.tips.isNotEmpty() -> {
//                            mission_upload_state_tv?.text = it.tips
                            XLog.i(TAG, "上传提示，${it.tips}")
                        }
                        else -> {
                            XLog.i(TAG, "上传进度，${it.updateProgress}")
                        }
                    }

                }
            }
        }

        // 飞行信息，航高，距离
        wayPointV3VM?.listenFlightControlState()
        viewLifecycleOwner?.let { it ->
            wayPointV3VM?.flightControlState?.observe(it) { it ->
                it?.let {
                    XLog.i(TAG,"航高：${it.height}")
                    XLog.i(TAG,"距离：${it.distance}")
                    // 设置起点
                    homeLocation = (Point(it.homeLocation.longitude, it.homeLocation.getLatitude(), flightHeight))
                }
                this.flightStateInfo = it
            }
        }

    }

    /**
     * 执行航线任务
     */
    fun startTask(drawFlightRoute: Boolean){

        // 更新航线文件
        updateWPML()
        // 绘制航线
        if(drawFlightRoute) drawFlightRoute()
        // 保存到历史文件
        saveTaskToHistory()

        // 设置仿地飞行
        if(terrainFollowingMode) setTerrainFollowMode()

        // 航线任务上传
        val waypointFile = File(curMissionPath)
        if (waypointFile.exists()) {
            wayPointV3VM?.pushKMZFileToAircraft(curMissionPath)
        } else {
            ToastUtils.showToast("未找到航线文件!");
        }

        // 执行航线任务
        wayPointV3VM?.startMission(
            FileUtils.getFileName(curMissionPath, WAYPOINT_FILE_TAG),
            selectWaylines,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
//                    ToastUtils.showToast("startMission Success")
                    XLog.i(TAG,"航线执行成功")
                }

                override fun onFailure(error: IDJIError) {
//                    ToastUtils.showToast("startMission Failed " + getErroMsg(error))
                    XLog.i(TAG,"航线执行失败：${getErrorMsg(error)}")
                }
            })

    }

    /**
     * 中止航线任务
     */
    fun pauseTask(){
        wayPointV3VM?.pauseMission(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
//                ToastUtils.showToast("pauseMission Success")
                XLog.i(TAG, "航线任务中止成功")
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("pauseMission Failed " + getErrorMsg(error))
                XLog.i(TAG, "航线任务中止失败：${getErrorMsg(error)}")
            }
        })
    }

    /**
     * 继续航线任务
     */
    fun resumeTask(){
        wayPointV3VM?.resumeMission(object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
//                ToastUtils.showToast("resumeMission Success")
                XLog.i(TAG, "航线任务开始继续执行...")
            }

            override fun onFailure(error: IDJIError) {
//                ToastUtils.showToast("resumeMission Failed " + getErroMsg(error))
                XLog.e(TAG, "航线任务继续执行失败，${getErrorMsg(error)}")
            }
        })
    }

    /**
     * 停止航线任务
     */
    fun stopTask(){
        if (curMissionExecuteState == WaypointMissionExecuteState.READY) {
            ToastUtils.showToast("未找到航线任务")
            XLog.i(TAG, "未找到航线任务")
            return
        }
        wayPointV3VM?.stopMission(
            FileUtils.getFileName(curMissionPath, WAYPOINT_FILE_TAG),
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("已停止航线任务")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("航线任务停止失败， " + getErrorMsg(error))
                    XLog.e(TAG, "航线任务停止失败，${getErrorMsg(error)}")
                }
            })
    }

    /**
     * 更新航线文件
     */
    private fun updateWPML() {
        val waylineFile = File(waylineFilePath)

        Single.fromCallable {
            // 将修改后的waylines.wpml重新压缩打包成 kmz
            val zipFiles = mutableListOf<String>()
            val cacheFolder = File(rootDir, unzipChildDir + unzipDir)
            var zipFile = File(rootDir + unzipChildDir + "waypoint.kmz")
            if (waylineFile.exists()) {
                zipFiles.add(cacheFolder.path)
                zipFile.createNewFile()
                WPMZParserManager.zipFiles(ContextUtil.getContext(), zipFiles, zipFile.path)
            }
            //将用户选择的kmz用修改的后的覆盖
            FileUtils.copyFileByChannel(zipFile.path, curMissionPath)
        }.subscribeOn(Schedulers.io()).subscribe()

    }

    private fun saveTaskToHistory(){

        getFlightPoints { point: Point ->
            historyFlightPoint.add(point)
        }

    }

    /**
     * 绘制航线
     */
    private fun drawFlightRoute(){
        var points: ArrayList<Point> = arrayListOf<Point>()

        if (historyFlightPoint.isEmpty()){
            homeLocation?.let { points.add(it) }
        }else{
            points.add(historyFlightPoint[historyFlightPoint.size - 1])
        }

        getFlightPoints { point: Point ->
            points.add(point)
        }

        // 绘制航线
        drawLineEvent(points)
    }


    fun getErrorMsg(error: IDJIError): String {
        if (!TextUtils.isEmpty(error.description())) {
            return error.description();
        }
        return error.errorCode()
    }

    /**
     * 设置仿地飞行
     */
    private fun setTerrainFollowMode(){

        KeyManager.getInstance().setValue(KeyTools.createKey(FlightControllerKey.KeyTerrainFollowModeEnabled),
            true,
            object: CommonCallbacks.CompletionCallback{
                override fun onSuccess() {
                    // 设置成功
                    XLog.i("仿地飞行已开启")
                }

                override fun onFailure(error: IDJIError) {
                    // 设置失败
                    XLog.e("仿地飞行开启失败")
                }

            });
    }

    /**
     * 添加航点
     */
    fun addFlightPoint(point: Point, height: Double?){
        var  waylineFile = File(waylineFilePath)
        if (!waylineFile.exists()) {
            ToastUtils.showToast("航线文件不存在")
            return
        }
        // 飞行高度
        var executeHeight = if (height == null || height < 1) flightHeight else height

        val reader = SAXReader()
        val document: Document = reader.read(waylineFile)

        // 查找节点
        var map = HashMap<String, String>()
        map["xmlns"] = document.rootElement.namespaceURI

        // 查找索引
        var placeMarkStr = "//xmlns:Placemark";
        var filter = document.createXPath(placeMarkStr)
        filter.setNamespaceURIs(map)
        var placeMarks = filter.selectNodes(document)
        var index = if(placeMarks == null) 1 else placeMarks.count() + 1


        var folderStr = "//xmlns:Folder"
        filter = document.createXPath(folderStr)
        filter.setNamespaceURIs(map)
        var folderNode = filter.selectSingleNode (document) as Element

        // 插入航点根节点
        var placeMarkElement = folderNode.addElement("Placemark")

        // Point -> coordinates 添加坐标点
        placeMarkElement.addElement("Point").addElement("coordinates").addText("${point.x},${point.y}")
        // wpml:index 添加索引
        placeMarkElement.addElement("wpml:index").addText("$index")
        // wpml:executeHeight
        placeMarkElement.addElement("wpml:executeHeight").addText("$executeHeight")
        // wpml:waypointSpeed
        placeMarkElement.addElement("wpml:waypointSpeed").addText("$flightSpeed")
        // wpml:waypointHeadingParam
        var headingParamElement = placeMarkElement.addElement("wpml:waypointHeadingParam")
        //      wpml:waypointHeadingMode
        // 飞行器偏航角模式,followWayline：沿航线方向。飞行器机头沿着航线方向飞至下一航点;
        //               manually：手动控制。飞行器在飞至下一航点的过程中，用户可以手动控制飞行器机头朝向
        //               fixed：锁定当前偏航角。飞行器机头保持执行完航点动作后的飞行器偏航角飞至下一航点
        //               smoothTransition：自定义。通过“wpml:waypointHeadingAngle”给定某航点的目标偏航角，并在航段飞行过程中均匀过渡至下一航点的目标偏航角。
        headingParamElement.addElement("wpml:waypointHeadingMode").addText("followWayline")
        //      wpml:waypointHeadingAngle
        headingParamElement.addElement("wpml:waypointHeadingAngle").addText("0")
        //      wpml:waypointPoiPoint
        headingParamElement.addElement("wpml:waypointPoiPoint").addText("0.000000,0.000000,0.000000")
        //      wpml:waypointHeadingAngleEnable
        headingParamElement.addElement("wpml:waypointHeadingAngleEnable").addText("0")
        // wpml:waypointTurnParam
        var turnParamElement = placeMarkElement.addElement("wpml:waypointTurnParam")
        //      wpml:waypointTurnMode
        turnParamElement.addElement("wpml:waypointTurnMode").addText("toPointAndStopWithDiscontinuityCurvature")
        //      wpml:waypointTurnDampingDist
        turnParamElement.addElement("wpml:waypointTurnDampingDist").addText("0")
        // wpml:useStraightLine
        placeMarkElement.addElement("wpml:useStraightLine").addText("1")

        // 保存文件
        saveDocument(document)
    }


    /**
     * 清空航点信息
     */
    fun clearFlightPointInfo(){
        val waylineFile = File(waylineFilePath)

        val reader = SAXReader()
        val document: Document = reader.read(waylineFile)

        // 查找节点
        var map = HashMap<String, String>()
        map["xmlns"] = document.rootElement.namespaceURI

        // 查找索引
        var str = "//xmlns:Placemark"
        var filter = document.createXPath(str)
        filter.setNamespaceURIs(map)
        var placeMarks = filter.selectNodes(document)

        // 根节点
        str = "//xmlns:Folder"
        filter = document.createXPath(str)
        filter.setNamespaceURIs(map)
        var folderNode = filter.selectSingleNode (document) as Element

        for (element in placeMarks){
            folderNode.remove(element)
        }

        saveDocument(document)
    }


    /**
     * 清空，模板文件信息
     */
    fun clearTemplateFile(){
        val templateFile = File(templateFilePath)

        val reader = SAXReader()
        val document: Document = reader.read(templateFile)

        // 查找节点
        var map = HashMap<String, String>()
        map["xmlns"] = document.rootElement.namespaceURI

        // 查找索引
        var str = "//xmlns:Document"
        var filter = document.createXPath(str)
        filter.setNamespaceURIs(map)
        var documentElement = filter.selectSingleNode(document)

        // 根节点
        str = "//xmlns:kml"
        filter = document.createXPath(str)
        filter.setNamespaceURIs(map)
        var kmlElement = filter.selectSingleNode (document) as Element

        // document 置为空
        kmlElement.remove(documentElement)
        kmlElement.addElement("Document")

        saveDocument(document)
    }

    /**
     * 保存文档
     */
    private fun saveDocument(document: Document){
        val format = OutputFormat.createPrettyPrint()
        format.encoding = "UTF-8" //应和xml文档的编码格式一致
        var write = XMLWriter(FileOutputStream(waylineFilePath), format)
        write.write(document)
        write.close()
    }


    /**
     * 航线绘制事件绑定
     */
    fun setDrawLineEvent(event: (ArrayList<Point>) -> Unit){
        this.drawLineEvent = event
    }


    private  fun getFlightPoints(event: (Point) -> Unit){
        val waylineFile = File(waylineFilePath)

        val reader = SAXReader()
        val document: Document = reader.read(waylineFile)

        // 查找节点
        var map = HashMap<String, String>()
        map["xmlns"] = document.rootElement.namespaceURI

        // 查找索引
        var str = "//xmlns:coordinates"
        var filter = document.createXPath(str)
        filter.setNamespaceURIs(map)
        var coordinates = filter.selectNodes(document)

        for (node in coordinates){
            var coordinateStr = node.text.split(',')
            var point = Point(coordinateStr[0].toDouble(), coordinateStr[1].toDouble(), flightHeight, SpatialReferences.getWgs84())
            event(point)
        }

    }
}



