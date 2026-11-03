package com.chimafok.todo.data

import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LanguageBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun languageRoundTripConflictsAndLegacyPreservation() = runBlocking {
        val aName="language-a-${UUID.randomUUID()}.db";val bName="language-b-${UUID.randomUUID()}.db"
        val a=TodoDatabase.create(context,aName);val b=TodoDatabase.create(context,bName)
        try {
            a.history().policy(RetentionPolicyEntity(days=90,language="ENGLISH",updatedAt=100))
            b.history().policy(RetentionPolicyEntity(days=90,language="TRADITIONAL",updatedAt=200))
            TaskRepository(a).save(TaskDraft("language-task","原文 Title",notes="備註 Notes",dueDate=java.time.LocalDate.of(2030,12,31),dueTime=java.time.LocalTime.of(10,0),zoneId="Asia/Hong_Kong",status=TaskStatus.COMPLETED))
            val json=BackupRepository(a).export("test");val envelope=JSONObject(json)
            assertEquals(6,envelope.getInt("formatVersion"));assertEquals(10,envelope.getInt("schemaVersion"))
            val doc=BackupFormat.decode(json);val restore=BackupRepository(b)
            val conflict=restore.preview(doc,RestoreMode.MERGE)
            val settings=conflict.plan.conflicts.single { it.local.rows("settings").isNotEmpty() }
            val choices=mapOf(settings.id to BackupChoice.FILE)
            restore.apply(doc,restore.preview(doc,RestoreMode.MERGE,choices),choices)
            assertEquals("ENGLISH",b.history().policy()!!.language)
            assertEquals("原文 Title",b.dao().task("language-task")!!.title)
            assertEquals("Asia/Hong_Kong",b.dao().task("language-task")!!.zoneId)
            val anchor=b.history().anchors().single { it.taskId=="language-task" }
            val another=JSONObject(json);another.getJSONObject("data").getJSONArray("settings").getJSONObject(0).put("language","SIMPLIFIED")
            val languageOnly=BackupFormat.decode(another.toString());val change=restore.preview(languageOnly,RestoreMode.MERGE)
            val pick=change.plan.conflicts.associate { it.id to BackupChoice.FILE }
            restore.apply(languageOnly,restore.preview(languageOnly,RestoreMode.MERGE,pick),pick)
            assertEquals(anchor,b.history().anchors().single { it.taskId=="language-task" })
            val old=JSONObject(json).put("formatVersion",5).put("schemaVersion",9)
            old.getJSONObject("data").getJSONArray("settings").getJSONObject(0).remove("language")
            b.history().policy(b.history().policy()!!.copy(language="SIMPLIFIED"))
            val legacy=BackupFormat.decode(old.toString())
            restore.apply(legacy,restore.preview(legacy,RestoreMode.REPLACE),emptyMap())
            assertEquals("SIMPLIFIED",b.history().policy()!!.language)
            val invalid=JSONObject(json);invalid.getJSONObject("data").getJSONArray("settings").getJSONObject(0).put("language","UNKNOWN")
            try { BackupFormat.decode(invalid.toString());fail("Invalid language accepted") } catch(_:IllegalArgumentException) {}
            assertEquals("SIMPLIFIED",b.history().policy()!!.language)
        } finally { a.close();b.close();context.deleteDatabase(aName);context.deleteDatabase(bName) }
    }
    @Test fun schemaNineMigratesWithoutChangingExistingData() = runBlocking {
        val name="language-migration-${UUID.randomUUID()}.db";val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.chimafok.todo.data.TodoDatabase/9.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file,null).use { sql ->
            val entities=schema.getJSONArray("entities");for(i in 0 until entities.length()) { val item=entities.getJSONObject(i);sql.execSQL(item.getString("createSql").replace("${'$'}{TABLE_NAME}",item.getString("tableName")))
                val indices=item.optJSONArray("indices") ?: org.json.JSONArray();for(n in 0 until indices.length())sql.execSQL(indices.getJSONObject(n).getString("createSql").replace("${'$'}{TABLE_NAME}",item.getString("tableName"))) }
            val queries=schema.getJSONArray("setupQueries");for(i in 0 until queries.length())sql.execSQL(queries.getString(i))
            sql.execSQL("INSERT INTO retention_policy (id,days,revision,updatedAt) VALUES (1,90,7,100)");sql.version=9
        }
        val db=TodoDatabase.create(context,name)
        try { val policy=db.history().policy()!!;assertEquals("SYSTEM",policy.language);assertEquals(90,policy.days);assertEquals(7L,policy.revision);assertEquals(10,db.openHelper.readableDatabase.version) }
        finally { db.close();context.deleteDatabase(name) }
    }
}
