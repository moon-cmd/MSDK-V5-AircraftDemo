package dji.sampleV5.util

import android.widget.Button
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import dji.sampleV5.aircraft.R
import dji.v5.manager.account.LoginInfo
import java.io.File

object AppInfo {

  private var userInfo: LoginInfo? = null

  fun setUserInfo( userInfo: LoginInfo?){
    this.userInfo = userInfo

    enabledDebug = "2744005838@qq.com" ==  userInfo?.account
  }

  fun getUserInfo(): LoginInfo?{ return userInfo}

  var enabledDebug: Boolean = false

  // 日志文件夹
  val LOG_FILE_PATH = FileUtil.getAppRootPath() + File.separator + "系统日志/"

  // 航线文件夹
  val WAYPOINT_ROOT_DIR = FileUtil.getAppRootPath() + File.separator + "waypoint/"

}