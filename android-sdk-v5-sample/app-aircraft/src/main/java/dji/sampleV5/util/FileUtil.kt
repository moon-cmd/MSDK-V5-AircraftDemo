package dji.sampleV5.util

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import dji.v5.utils.common.FileUtils.*
import dji.v5.utils.common.LogUtils
import java.io.File
import java.io.IOException


object FileUtil {

    const val KmlSelectRequestCode: Int = 0

    const val MmpkSelectRequestCode: Int = 1

    /**
     * 选择文件
     */
    fun getExtSdCardPaths(context: Context, exceptionEvent: (IOException) -> Unit): ArrayList<String?> {
        var sExtSdCardPaths = ArrayList<String?>()
        for (file in context.getExternalFilesDirs("external")) {
            if (file != null && file != context.getExternalFilesDir("external")) {
                val index = file.absolutePath.lastIndexOf("/Android/data")
                if (index >= 0) {
                    var path: String? = file.absolutePath.substring(0, index)
                    try {
                        path = File(path).canonicalPath
                    } catch (e: IOException) {
                        LogUtils.e("文件选择异常", e.message)
                    }
                    sExtSdCardPaths.add(path)
                }
            }
        }
        if (sExtSdCardPaths.isEmpty()) {
            sExtSdCardPaths.add("/storage/sdcard1")
        }
        return sExtSdCardPaths
    }

    fun getChooseFileResultPath(context: Context, uri: Uri): String? {
        var chooseFilePath: String? = null
        if ("file".equals(uri.scheme, ignoreCase = true)) { //使用第三方应用打开
            chooseFilePath = uri.path
            Toast.makeText(context, chooseFilePath, Toast.LENGTH_SHORT).show()
            return chooseFilePath
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) { //4.4以后
            chooseFilePath = getPath(context, uri)
        } else { //4.4以下下系统调用方法
            chooseFilePath = getRealPathFromURI(context, uri)
        }
        return chooseFilePath
    }

    /**
     * 专为Android4.4设计的从Uri获取文件绝对路径，以前的方法已不好使
     */
    @SuppressLint("NewApi")
    private fun getPath(context: Context, uri: Uri): String? {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    java.lang.Long.valueOf(id)
                )
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            uri.path
        }
        return null
    }

    private fun getRealPathFromURI(context: Context, contentUri: Uri): String? {
        var res: String? = null
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = context.getContentResolver().query(contentUri, proj, null, null, null)
        if (null != cursor && cursor.moveToFirst()) {
            val column_index: Int = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            res = cursor.getString(column_index)
            cursor.close()
        }
        return res
    }
}