package top.fumiama.copymanga.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_dlist.*
import kotlinx.android.synthetic.main.widget_titlebar.*
import top.fumiama.copymanga.R
import top.fumiama.copymanga.handler.DlLHandler
import java.io.File
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

class DlListActivity:Activity() {
    var nullZipDirStr = emptyArray<String>()
    var handler: DlLHandler? = null
    var loadingDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dlist)
        ttitle.text = intent.getStringExtra("title")
        loadingDialog = Dialog(this)
        loadingDialog?.setContentView(R.layout.dialog_loading)
        handler = DlLHandler(Looper.myLooper()!!, this)
        handler?.obtainMessage(3, currentDir)?.sendToTarget()       //call scanFile
    }

    fun scanFile(cd: File?){
        cd?.list()?.sortedArrayWith { o1, o2 ->
            if(o1.endsWith(".zip") && o2.endsWith(".zip")) (10000*getFloat(o1) - 10000*getFloat(o2) + 0.5).toInt()
            else o1[0] - o2[0]
        }?.let {
            mylv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, it)
            mylv.setOnItemClickListener { _, _, position, _ ->
                val chosenFile = File(cd, it[position])
                //Toast.makeText(this, "进入$chosenFile", Toast.LENGTH_SHORT).show()
                if (chosenFile.isDirectory) {
                    currentDir = chosenFile
                    startActivity(
                        Intent(
                            this,
                            DlListActivity::class.java
                        ).putExtra("title", it[position])
                    )
                }
                else{
                    Toast.makeText(this, "加载中...", Toast.LENGTH_SHORT).show()
                    ViewMangaActivity.zipFile = chosenFile
                    ViewMangaActivity.titleText = it[position]
                    ViewMangaActivity.zipPosition = position
                    ViewMangaActivity.zipList = it
                    ViewMangaActivity.cd = cd
                    startActivity(Intent(this, ViewMangaActivity::class.java))
                }
            }
            mylv.setOnItemLongClickListener { _, _, position, _ ->
                val chosenFile = File(cd, it[position])
                AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_launcher_foreground).setMessage("是否在此执行删除/查错?")
                    .setTitle("提示").setPositiveButton(android.R.string.ok){ _, _ ->
                        if(chosenFile.exists()) handler?.obtainMessage(2, chosenFile)?.sendToTarget()       //call rmrf
                        handler?.obtainMessage(3, cd)?.sendToTarget()       //call scanFile
                    }.setNegativeButton(android.R.string.cancel){_, _ ->}
                    .setNeutralButton("查错"){_, _ -> handler?.obtainMessage(1, chosenFile)?.sendToTarget()}  //call checkDir
                    .show()
                true
            }
        }
    }

    fun rmrf(f: File) {
        if (f.isDirectory) f.listFiles()?.let {
            for (i in it)
                if (i.isDirectory) rmrf(i)
                else i.delete()
        }
        f.delete()
    }

    fun checkDir(f: File){
        nullZipDirStr = emptyArray()
        findNullWebpZipFileInDir(f)
        if(nullZipDirStr.isNotEmpty()) showErrorZip(nullZipDirStr.joinToString("\n"))
        else Toast.makeText(this, "未发现错误", Toast.LENGTH_SHORT).show()
    }

    fun showLoading() = loadingDialog?.show()

    fun hideLoading() = loadingDialog?.hide()

    private fun findNullWebpZipFileInDir(f: File){
        if (f.isDirectory) f.listFiles()?.let {
            for (i in it)
                if (i.isDirectory) findNullWebpZipFileInDir(i)
                else if(!checkZip(i)) nullZipDirStr += i.path.substringAfterLast(getExternalFilesDir("").toString())
        }
    }

    private fun checkZip(f: File): Boolean{
        return try {
            val exist = f.exists()
            if (!exist) true
            else {
                var re = true
                val zip = ZipInputStream(f.inputStream().buffered())
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory){
                        if(zip.read() == -1 && entry.size == 0L){
                            re = false
                            break
                        }
                    }
                    entry = zip.nextEntry
                }
                zip.closeEntry()
                zip.close()
                re
            }
        } catch (e: Exception) {
            Toast.makeText(this, "读取${f.name}错误!", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun showErrorZip(msg: CharSequence) = AlertDialog.Builder(this)
        .setIcon(R.drawable.ic_launcher_foreground)
        .setTitle("找到以下错误文件,是否删除?")
        .setMessage(msg)
        .setPositiveButton(android.R.string.ok){_, _ -> deleteErrorZip()}
        .setNegativeButton(android.R.string.cancel){_, _ ->}
        .show()

    private fun deleteErrorZip(){
        val exf = getExternalFilesDir("")
        for(i in nullZipDirStr){
            val f = File(exf, i)
            if(f.exists()) f.delete()
        }
    }

    private fun getFloat(oldString: String): Float {
        val newString = StringBuffer()
        var matcher = Pattern.compile("\\d+.+\\d+").matcher(oldString)
        while (matcher.find()) newString.append(matcher.group())
        //Log.d("MyDLL1", newString.toString())
        if(newString.isEmpty()){
            matcher = Pattern.compile("\\d").matcher(oldString)
            while (matcher.find()) newString.append(matcher.group())
        }
        //Log.d("MyDLL2", newString.toString().toFloat().toString())
        return newString.toString().toFloat()
    }

    companion object{
        var currentDir: File? = null
    }
}

