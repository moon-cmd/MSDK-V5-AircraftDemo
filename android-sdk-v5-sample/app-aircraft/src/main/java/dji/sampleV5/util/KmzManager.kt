package dji.sampleV5.util

import android.text.TextUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import dji.sampleV5.moduleaircraft.data.FlightControlState
import dji.sampleV5.moduleaircraft.models.WayPointV3VM
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.wpmz.jni.JNIWPMZManager
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
import org.dom4j.XPath
import org.dom4j.io.SAXReader
import java.io.File
/***
 * KMZ 航线文件管理
 */
class KmzManager : KmzManagerEvent, Fragment{

    private val TAG: String = "航线管理"

    private val wayPointV3VM: WayPointV3VM by activityViewModels()

    private val WAYPOINT_SAMPLE_FILE_NAME: String = "waypointsample.kmz"
    private val WAYPOINT_SAMPLE_FILE_DIR: String = "waypoint/"
    private val WAYPOINT_SAMPLE_FILE_CACHE_DIR: String = "waypoint/cache/"
    private val WAYPOINT_FILE_TAG = ".kmz"
    private var unzipChildDir = "temp/"
    private var unzipDir = "wpmz/"

    val rootDir = DiskUtil.getExternalCacheDirPath(ContextUtil.getContext(), WAYPOINT_SAMPLE_FILE_DIR)
    // 当前航线文件路径
    var curMissionPath: String = DiskUtil.getExternalCacheDirPath(
        ContextUtil.getContext(),
        WAYPOINT_SAMPLE_FILE_DIR + WAYPOINT_SAMPLE_FILE_NAME
    )

    // 模板文件
    private var templateFilePath: String = ""
    // 执行文件
    private var waylineFilePath: String = ""

    // 航线转态
    var curMissionExecuteState: WaypointMissionExecuteState? = null

    var selectWaylines: ArrayList<Int> = ArrayList()

    // 飞行高度
    var flightHeight: Double = 100.0
    // 飞行速度
    var flightSpeed: Double = 5.0

    // 飞行状态信息：航高、位置...
    public var flightStateInfo:  FlightControlState? = null


    constructor( ){
        initKmzFile()
    }


    /**
     * 航线文件初始化
     */
    fun initKmzFile(){
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
        val unzipFolder = File(rootDir, unzipChildDir)
        WPMZParserManager.unZipFolder(ContextUtil.getContext(), curMissionPath, unzipFolder.path, false)

    }

    /**
     * 上传航线任务
     */
    fun uploadTask(){

        // 航线任务上传
        val waypointFile = File(curMissionPath)
        if (waypointFile.exists()) {
            wayPointV3VM?.pushKMZFileToAircraft(curMissionPath)
        } else {
            ToastUtils.showToast("未找到航线文件!");
        }

        // 航线标记
        // version参数实际未用到
        var waypoints: ArrayList<WaylineExecuteWaypoint> = ArrayList<WaylineExecuteWaypoint>()
        val parseInfo = JNIWPMZManager.getWaylines("1.0.0", curMissionPath)
        var waylines = parseInfo.waylines
        waylines.forEach() {
            waypoints.addAll(it.waypoints)
            drawLinesEvent(it.waypoints)
        }
        waypoints.forEach() {
            drawPointEvent(DJILatLng(it.location.latitude, it.location.longitude), it.waypointIndex)
        }

    }



    /**
     * 航线飞行事件监听初始化
     */
    fun initFlightListenerEvent(){

        // 状态监听
        wayPointV3VM?.addMissionStateListener() {
            curMissionExecuteState = it
            LogUtils.i(TAG, "执行状况：${it.name}")
        }
        // 添加航线监听
        wayPointV3VM?.addWaylineExecutingInfoListener() {
            LogUtils.i(TAG, "航线信息：")
            LogUtils.i(TAG, "航线ID：${it.waylineID}")
            LogUtils.i(TAG, "航线索引：${it.currentWaypointIndex}")
            LogUtils.i(TAG, "航线名称：${if (curMissionExecuteState == WaypointMissionExecuteState.READY) "" else it.missionFileName}")

        }

        // 航线上传监听
        viewLifecycleOwner?.let {
            wayPointV3VM?.missionUploadState?.observe(it) {
                it?.let {
                    when {
                        it.error != null -> {
                            LogUtils.i(TAG, "航线上传失败，${getErroMsg(it.error!!)}")
                        }
                        it.tips.isNotEmpty() -> {
//                            mission_upload_state_tv?.text = it.tips
                            LogUtils.i(TAG, "上传提示，${it.tips}")
                        }
                        else -> {
                            LogUtils.i(TAG, "上传进度，${it.updateProgress}")
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
                    LogUtils.i(TAG,"航高：${it.height}")
                    LogUtils.i(TAG,"距离：${it.distance}")
                }
                this.flightStateInfo = it
            }
        }

    }


    /**
     * 执行航线任务
     */
    fun startTask(){
        wayPointV3VM?.startMission(
            FileUtils.getFileName(curMissionPath, WAYPOINT_FILE_TAG),
            selectWaylines,
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
//                    ToastUtils.showToast("startMission Success")
                    LogUtils.i(TAG,"航线执行成功")
                }

                override fun onFailure(error: IDJIError) {
//                    ToastUtils.showToast("startMission Failed " + getErroMsg(error))
                    LogUtils.i(TAG,"航线执行失败：${getErroMsg(error)}")
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
                LogUtils.i(TAG, "航线任务中止成功")
            }

            override fun onFailure(error: IDJIError) {
                ToastUtils.showToast("pauseMission Failed " + getErroMsg(error))
                LogUtils.i(TAG, "航线任务中止失败：${getErroMsg(error)}")
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
                LogUtils.i(TAG, "航线任务开始继续执行...")
            }

            override fun onFailure(error: IDJIError) {
//                ToastUtils.showToast("resumeMission Failed " + getErroMsg(error))
                LogUtils.e(TAG, "航线任务继续执行失败，${getErroMsg(error)}")
            }
        })
    }

    /**
     * 停止航线任务
     */
    fun stopTask(){
        if (curMissionExecuteState == WaypointMissionExecuteState.READY) {
            ToastUtils.showToast("未找到航线任务")
            LogUtils.i(TAG, "未找到航线任务")
            return
        }
        wayPointV3VM?.stopMission(
            FileUtils.getFileName(curMissionPath, WAYPOINT_FILE_TAG),
            object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    ToastUtils.showToast("已停止航线任务")
                }

                override fun onFailure(error: IDJIError) {
                    ToastUtils.showToast("航线任务停止失败， " + getErroMsg(error))
                    LogUtils.e(TAG, "航线任务停止失败，${getErroMsg(error)}")
                }
            })
    }


    /**
     * 解析航线文件WPML
     */
    public fun parseWPML(){
        val waypointFile = File(curMissionPath)
        if (!waypointFile.exists()) {
            ToastUtils.showToast("未找到航线文件")
            return
        }

        val unzipFolder = File(rootDir, unzipChildDir)
        // 解压后的waylines路径
        val templateFile = File(rootDir + unzipChildDir + unzipDir, WPMZParserManager.TEMPLATE_FILE)
        val waylineFile = File(rootDir + unzipChildDir + unzipDir, WPMZParserManager.WAYLINE_FILE)

//        val reader = SAXReader()
//        val document: Document = reader.read(File("input.xml"))
        val reader = SAXReader()
        val document: Document = reader.read(waylineFile)
        val root: Element = document.rootElement
        // 查找节点
        var map = HashMap<String, String>()
        map.put("xmlns", document.rootElement.namespaceURI)
        var filter = document.createXPath("//xmlns:Point")
        filter.setNamespaceURIs(map)
        var nodes = filter.selectNodes(document)
        var s = ""
//        XmlNamespaceManager m = new XmlNamespaceManager(xmlDoc.NameTable);　　　
//
//        ————添加命名空间　
//        m.AddNamespace("fuck", "http://www.google.com/schemas/sitemap/0.84");      　

//        val memberElm: Element = root.element("member") // "member"是节点名

        root.elementIterator("")
//        String text=memberElm.getText();
//        String text=root.elementText("name");
//        ageElm.setText("29");

//        val format: OutputFormat = OutputFormat.createPrettyPrint()
//        format.setEncoding("GBK") // 指定XML编码
//
//        val writer = XMLWriter(FileWriter("output.xml"), format)
//        writer.write(document)
//        writer.close()
    }


    /**
     * 更新航线文件
     */
    private fun updateWPML(newContent: String) {
        val waylineFile = File(rootDir + unzipChildDir + unzipDir, WPMZParserManager.WAYLINE_FILE)

        Single.fromCallable {
            FileUtils.writeFile(waylineFile.path, newContent, false)
            //将修改后的waylines.wpml重新压缩打包成 kmz
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

    fun getErroMsg(error: IDJIError): String {
        if (!TextUtils.isEmpty(error.description())) {
            return error.description();
        }
        return error.errorCode()
    }

    /**
     * 设置仿地飞行
     */
    fun setTerrainFollowMode(){

        KeyManager.getInstance().setValue(KeyTools.createKey(FlightControllerKey.KeyTerrainFollowModeEnabled),
            true,
            object: CommonCallbacks.CompletionCallback{
                override fun onSuccess() {
                    // 设置成功
                }

                override fun onFailure(error: IDJIError) {
                    // 设置失败
                }

            });
    }

    fun addFlightPoint(lat: Double, lng: Double, height: Double?){
        var  waylineFile = File(waylineFilePath)
        if (!waylineFile.exists()) {
            ToastUtils.showToast("航线文件不存在")
            return
        }
        val reader = SAXReader()
        val document: Document = reader.read(waylineFile)

        // Point
        //      coordinates
        // wpml:index
        // wpml:executeHeight
        // wpml:waypointSpeed
        // wpml:waypointHeadingParam
        //      wpml:waypointHeadingMode
        // 飞行器偏航角模式,followWayline：沿航线方向。飞行器机头沿着航线方向飞至下一航点;
        //               manually：手动控制。飞行器在飞至下一航点的过程中，用户可以手动控制飞行器机头朝向
        //               fixed：锁定当前偏航角。飞行器机头保持执行完航点动作后的飞行器偏航角飞至下一航点
        //               smoothTransition：自定义。通过“wpml:waypointHeadingAngle”给定某航点的目标偏航角，并在航段飞行过程中均匀过渡至下一航点的目标偏航角。
        //      wpml:waypointHeadingAngle
        //      wpml:waypointPoiPoint
        //      wpml:waypointHeadingAngleEnable
        // wpml:waypointTurnParam
        //      wpml:waypointTurnMode
        //      wpml:waypointTurnDampingDist
        //      wpml:useStraightLine
    }
}

