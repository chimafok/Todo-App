package com.chimafok.todo.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** App-private, bounded staging lets rotation/process recreation resume without putting JSON in a Bundle. */
class BackupFiles(private val context:Context) {
    private val folder get()=File(context.cacheDir,"backup-sessions").also { check(it.isDirectory || it.mkdirs()) }
    private fun file(token:String):File {
        require(runCatching { UUID.fromString(token).toString()==token }.getOrDefault(false)){"備份暫存識別碼無效"}
        return File(folder,"$token.json")
    }
    fun stage(text:String):String {
        val bytes=text.toByteArray(Charsets.UTF_8);require(bytes.size<=BACKUP_MAX_BYTES){"備份檔案不可超過 64 MiB"}
        val expiry=System.currentTimeMillis()-24*60*60*1000L
        folder.listFiles()?.filter { it.isFile && it.lastModified()<expiry && it.name.endsWith(".json") && runCatching { UUID.fromString(it.nameWithoutExtension).toString()==it.nameWithoutExtension }.getOrDefault(false) }?.forEach { it.delete() }
        val token=UUID.randomUUID().toString();file(token).outputStream().use { it.write(bytes) };return token
    }
    fun document(token:String):BackupDocument {
        val source=file(token);require(source.isFile){"備份暫存已到期，請重新選擇檔案"}
        return source.inputStream().use(BackupFormat::read)
    }
    fun import(uri:Uri):String {
        require(uri.scheme=="content"){"請透過系統檔案選擇器選取備份"}
        val doc=requireNotNull(context.contentResolver.openInputStream(uri)){"無法讀取所選檔案"}.use(BackupFormat::read)
        return stage(BackupFormat.encode(doc.data,doc.exportedAt,doc.appVersion))
    }
    fun export(token:String,uri:Uri) {
        require(uri.scheme=="content"){"請透過系統檔案選擇器儲存備份"}
        val source=file(token);require(source.isFile && source.length()<=BACKUP_MAX_BYTES){"備份暫存已到期，請重新匯出"}
        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")){"無法寫入所選位置"}.use { output -> source.inputStream().use { it.copyTo(output) };output.flush() }
    }
}
