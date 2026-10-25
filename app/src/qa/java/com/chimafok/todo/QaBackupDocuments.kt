package com.chimafok.todo

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** QA-only document transport. This provider is never packaged in the user's debug/release app. */
class QaBackupDocuments:ContentProvider() {
    override fun onCreate()=true
    override fun getType(uri:Uri)="application/json"
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        val name=requireNotNull(uri.lastPathSegment);require(name in setOf("input.json","output.json"))
        return ParcelFileDescriptor.open(File(requireNotNull(context).cacheDir,"qa-$name"),ParcelFileDescriptor.parseMode(mode))
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor =
        MatrixCursor(arrayOf("_display_name","_size")).apply { addRow(arrayOf<Any?>(uri.lastPathSegment,0)) }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
}
