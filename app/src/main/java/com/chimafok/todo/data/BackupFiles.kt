package com.chimafok.todo.data

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** App-private, bounded staging lets rotation/process recreation resume without putting JSON in a Bundle. */
class BackupFiles(private val context:Context) {
    private val folder get()=File(context.cacheDir,"backup-sessions").also { check(it.isDirectory || it.mkdirs()) }
    private fun file(token:String):File {
        require(runCatching { UUID.fromString(token).toString()==token }.getOrDefault(false)){tr(R.string.text_invalid_temporary_backup_id_994b50)}
        return File(folder,"$token.json")
    }
    fun stage(text:String):String {
        val bytes=text.toByteArray(Charsets.UTF_8);require(bytes.size<=BACKUP_MAX_BYTES){tr(R.string.text_backups_cannot_exceed_mib_d3bf96)}
        val expiry=System.currentTimeMillis()-24*60*60*1000L
        folder.listFiles()?.filter { it.isFile && it.lastModified()<expiry && it.name.endsWith(".json") && runCatching { UUID.fromString(it.nameWithoutExtension).toString()==it.nameWithoutExtension }.getOrDefault(false) }?.forEach { it.delete() }
        val token=UUID.randomUUID().toString();file(token).outputStream().use { it.write(bytes) };return token
    }
    fun document(token:String):BackupDocument {
        val source=file(token);require(source.isFile){tr(R.string.text_the_temporary_backup_expired_please_select_6a2e1a)}
        return source.inputStream().use(BackupFormat::read)
    }
    fun import(uri:Uri):String {
        require(uri.scheme=="content"){tr(R.string.text_please_choose_a_backup_using_the_afce19)}
        val doc=requireNotNull(context.contentResolver.openInputStream(uri)){tr(R.string.text_cannot_read_the_selected_file_6b1cb1)}.use(BackupFormat::read)
        return stage(BackupFormat.encode(doc.data,doc.exportedAt,doc.appVersion))
    }
    fun export(token:String,uri:Uri) {
        require(uri.scheme=="content"){tr(R.string.text_please_save_the_backup_using_the_c00b26)}
        val source=file(token);require(source.isFile && source.length()<=BACKUP_MAX_BYTES){tr(R.string.text_the_temporary_backup_expired_please_export_510450)}
        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")){tr(R.string.text_cannot_write_to_the_selected_location_e1d691)}.use { output -> source.inputStream().use { it.copyTo(output) };output.flush() }
    }
}
