package dji.sampleV5.logInfo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import dji.sampleV5.aircraft.R
import dji.sampleV5.util.AppInfo
import java.io.File

class LogInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_info)

        // 日志加载
        initLogInfo()

        var btnReturn = findViewById<Button>(R.id.btn_return)
        btnReturn.setOnClickListener {
            finish()
        }
    }

    /**
     * 日志加载
     */
    private fun initLogInfo(){

        // 获取文件
        val file: File = File(AppInfo.LOG_FILE_PATH)
        val files = file.listFiles() ?: return
        val s: MutableList<String> = ArrayList()

        var txtWidget = findViewById<TextView>(R.id.txt_log_info)
        txtWidget.movementMethod = ScrollingMovementMethod.getInstance()
        for (i in files.indices) {
            s.add(files[i].name)
            txtWidget.append(files[i].readText())
        }
    }
}