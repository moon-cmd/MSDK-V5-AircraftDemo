package dji.sampleV5.logInfo

import android.os.Build
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.flattener.Flattener
import com.elvishew.xlog.flattener.Flattener2
import java.text.SimpleDateFormat
import java.util.*

class LogFlattener: Flattener2 {

    override fun flatten(timeMillis: Long, logLevel: Int, tag: String?, message: String?): CharSequence {
        return (getCurrDDate()
                + '|' + LogLevel.getLevelName(logLevel)
                + '|' + tag
                + '|' + message)
    }

    private fun getCurrDDate(): String? {
        return if (Build.VERSION.SDK_INT >= 24) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Date())
        } else {
            val tms = Calendar.getInstance()
            tms[Calendar.YEAR].toString() + "-" + tms[Calendar.MONTH] + "-" + tms[Calendar.DAY_OF_MONTH] + " " + tms[Calendar.HOUR_OF_DAY] + ":" + tms[Calendar.MINUTE] + ":" + tms[Calendar.SECOND] + "." + tms[Calendar.MILLISECOND]
        }
    }
}