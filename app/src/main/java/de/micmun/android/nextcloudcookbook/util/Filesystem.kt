package de.micmun.android.nextcloudcookbook.util

import android.content.Context
import android.util.Log
import de.micmun.android.nextcloudcookbook.nextcloudapi.Sync
import java.io.*
import java.lang.Exception

class Filesystem(var mContext: Context) {

    companion object {
        private val TAG = this.javaClass::class.toString()
    }

    /**
     * Delete a given folder or file.
     * If a folder is given, it will also delete all its content.
     *
     */
    fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            val results = fileOrDirectory.listFiles()
            if(results != null){
                for (child in results) {
                    deleteRecursive(child)
                }
            }
        }
        fileOrDirectory.delete()
    }

    private fun createInternalFoldersRecursive(path: String) {
        var file = File(mContext.filesDir.path)
        var folderlist = path.split('/')
        for(elem in folderlist){
            var tmpfile = File(file, elem)
            if(!file.exists()){
                file.mkdir()
            }
            file = tmpfile
        }
    }

    fun writeDataToInternal(folder: String, filename: String, content: ByteArray){
        createInternalFoldersRecursive(folder)
        var internalStorage = File(mContext.filesDir, folder)
        val file = File(internalStorage, filename)
        file.createNewFile()
        val stream = FileOutputStream(file)
        try {
            stream.write(content)
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
            e.printStackTrace()
        } finally {
            stream.close()
        }
    }

    fun readInternalFile(file: File): String {
        var content = ""
        try {
            val stream = FileInputStream(file)
            val inputStream = InputStreamReader(stream)
            val bufferedReader = BufferedReader(inputStream)

            var readString: String? = bufferedReader.readLine()
            while (readString != null) {
                content += readString
                readString = bufferedReader.readLine()
            }
        } catch (e: FileNotFoundException){
            //Log.e(TAG, "readInternalFile: File not found! ${file.absoluteFile}")
        }
        return content
    }
}