package dji.sampleV5.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import dji.sampleV5.aircraft.R
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError

object DialogUtil {

    fun showFlightBasicInfo(
        context: Activity,
        title: String?,
        kmzManager: KmzManager?,
        callback: CommonCallbacks.CompletionCallbackWithParam<KmzManager>
    ){
        val dialogView: View = context.layoutInflater.inflate(R.layout.flight_basic_info, null)
//        dialogView.setBackgroundColor(context.resources.getColor(R.color.gray))
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
        dialog!!.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)

        // 标题
        val tvTitle = dialogView.findViewById<TextView>(R.id.title)
        tvTitle.text = if (title ==null || title.isEmpty()) tvTitle.text else title

        // 飞行高度
        var  inputFlightHeight = dialogView.findViewById<TextView>(R.id.input_flight_height)
        inputFlightHeight.text = "${kmzManager?.flightHeight}"

        // 飞行速度
        var  inputFlightSpeed = dialogView.findViewById<TextView>(R.id.input_flight_speed)
        inputFlightSpeed.text = "${kmzManager?.flightSpeed}"

        // 仿地飞行
        var checkBoxTerrainFollowingMode = dialogView.findViewById<CheckBox>(R.id.cb_terrain_follow)
        checkBoxTerrainFollowingMode.isChecked = kmzManager?.terrainFollowingMode == true

        dialogView.findViewById<View>(R.id.confirm).setOnClickListener {
            kmzManager?.flightHeight = inputFlightHeight.text.toString().toDouble()
            kmzManager?.flightSpeed = inputFlightSpeed.text.toString().toDouble()

            kmzManager?.terrainFollowingMode = checkBoxTerrainFollowingMode.isChecked

            callback.onSuccess(kmzManager)
            if (dialog != null) {
                dialog.dismiss()
            }
        }
        dialogView.findViewById<View>(R.id.cancel).setOnClickListener {
            callback?.onSuccess(kmzManager)
            if (dialog != null) {
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun showTipDialog(
        context: Context,
        title: String?,
        msg: String,
        callbacks: CommonCallbacks.CompletionCallback
    ){
        val builder: androidx.appcompat.app.AlertDialog.Builder = androidx.appcompat.app.AlertDialog.Builder(context)
        builder.setTitle(title ?: "提示")
        builder.setMessage(msg)
            .setPositiveButton("确定", DialogInterface.OnClickListener { dialog, id ->
                callbacks.onSuccess()
            })
            .setNegativeButton("取消", DialogInterface.OnClickListener { dialog, id ->
                // 取消

            })
        var dialog = builder.create()

        dialog.show()

        val btnPositive: Button = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        val btnNegative: Button = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)

        val layoutParams = btnPositive.layoutParams as LinearLayout.LayoutParams
        layoutParams.weight = 10f
        layoutParams.gravity = Gravity.CENTER;
        btnPositive.layoutParams = layoutParams
        btnNegative.layoutParams = layoutParams
    }
}