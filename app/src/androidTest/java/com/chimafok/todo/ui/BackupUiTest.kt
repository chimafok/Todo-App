package com.chimafok.todo.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chimafok.todo.MainActivity
import com.chimafok.todo.data.*
import com.chimafok.todo.domain.*
import com.chimafok.todo.reminders.ReminderRuntime
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val db get()=ReminderRuntime.database
    private lateinit var original:BackupDocument
    private var cancelPicker=false
    private val actions=mutableListOf<String>()
    private val monitor=object:Instrumentation.ActivityMonitor(){override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
        if(intent.action !in listOf(Intent.ACTION_OPEN_DOCUMENT,Intent.ACTION_CREATE_DOCUMENT))return null
        actions+=intent.action!!
        return if(cancelPicker)Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
        else Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(Uri.parse("content://${context.packageName}.backup-test/${if(intent.action==Intent.ACTION_OPEN_DOCUMENT)"input.json" else "output.json"}")))
    }}
    private fun <T> data(block:suspend()->T):T=runBlocking{block()}
    private fun ready(){compose.waitUntil(20_000){compose.onAllNodesWithTag("add_task").fetchSemanticsNodes().isNotEmpty()}}
    private fun navSettings(){compose.onNodeWithTag("open_menu").performClick();compose.onNodeWithTag("nav_SETTINGS").performClick();compose.waitForIdle()}
    private fun await(tag:String){compose.waitUntil(20_000){runCatching{compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}}
    private fun closed(){compose.waitUntil(20_000){compose.onAllNodesWithTag("backup_preview").fetchSemanticsNodes().isEmpty()}}
    private fun seedLocal(id:String="one",title:String="本機")=data{TaskRepository(db).save(TaskDraft(id,title))}
    private fun importFile(title:String="檔案",id:String="incoming",timestamp:Clock?=null):BackupDocument=data {
        val name="backup-ui-${UUID.randomUUID()}.db";val source=TodoDatabase.create(context,name)
        try{TaskRepository(source,timestamp ?: DeviceClock).save(TaskDraft(id,title));val text=BackupRepository(source).export("test");File(context.cacheDir,"qa-input.json").writeText(text,Charsets.UTF_8);BackupFormat.decode(text)}
        finally{source.close();context.deleteDatabase(name)}
    }
    private fun openImport(){compose.onNodeWithTag("import_backup").performScrollTo().performClick();await("backup_ready")}
    @Before fun prepare(){assertTrue(context.packageName.endsWith(".qa"));original=data{BackupFormat.decode(BackupRepository(db).export("test"))};instrumentation.addMonitor(monitor);ready()}
    @After fun restore(){instrumentation.removeMonitor(monitor);if(::original.isInitialized)data{val repo=BackupRepository(db);repo.apply(original,repo.preview(original,RestoreMode.REPLACE),emptyMap())}}
    @Test fun exportUsesCreateDocumentAndWritesPortableJsonThroughARealContentResolver(){
        seedLocal();navSettings();compose.onNodeWithTag("export_backup").performScrollTo().performClick();await("backup_message")
        compose.onNodeWithTag("backup_message").assertTextContains("已匯出",substring=true)
        val value=File(context.cacheDir,"qa-output.json").inputStream().use(BackupFormat::read)
        assertTrue(value.data.rows("tasks").any { it["id"]=="one" });assertTrue(Intent.ACTION_CREATE_DOCUMENT in actions)
        compose.runOnIdle{assertFalse(ViewModelProvider(compose.activity)[TodoViewModel::class.java].externalDocumentFlow)}
    }
    @Test fun cancellingEitherPickerLeavesTheDatabaseAndSettingsPageAlone(){
        seedLocal();val before=data{db.dao().allTasks()};cancelPicker=true;navSettings()
        compose.onNodeWithTag("import_backup").performScrollTo().performClick();compose.waitForIdle();compose.onNodeWithTag("page_title").assertTextEquals("設定")
        compose.onNodeWithTag("backup_preview").assertDoesNotExist();assertEquals(before,data{db.dao().allTasks()})
        compose.onNodeWithTag("export_backup").performScrollTo().performClick();await("backup_message");compose.onNodeWithTag("backup_message").assertTextContains("取消",substring=true)
        assertEquals(before,data{db.dao().allTasks()})
    }
    @Test fun malformedFileShowsAnErrorWithoutOpeningDestructiveConfirmation(){
        seedLocal();val before=data{db.dao().allTasks()};File(context.cacheDir,"qa-input.json").writeText("not a backup",Charsets.UTF_8);navSettings()
        compose.onNodeWithTag("import_backup").performScrollTo().performClick();await("backup_error");compose.onNodeWithTag("backup_preview").assertDoesNotExist();assertEquals(before,data{db.dao().allTasks()})
    }
    @Test fun replaceCanCancelThenExportCurrentDataAndConfirmAfterRotation(){
        seedLocal();importFile();navSettings();openImport();compose.onNodeWithTag("backup_mode_REPLACE").performClick();await("backup_ready")
        compose.onNodeWithTag("cancel_backup_restore").performClick();assertNotNull(data{db.dao().task("one")});assertNull(data{db.dao().task("incoming")})
        openImport();compose.onNodeWithTag("backup_mode_REPLACE").performClick();await("backup_ready");compose.activityRule.scenario.recreate();await("backup_ready")
        compose.onNodeWithTag("backup_export_before_restore").performClick();await("backup_transfer_status")
        val saved=File(context.cacheDir,"qa-output.json").inputStream().use(BackupFormat::read);assertTrue(saved.data.rows("tasks").any{it["id"]=="one"})
        compose.onNodeWithTag("confirm_backup_restore").performClick();closed();assertNull(data{db.dao().task("one")});assertEquals("檔案",data{db.dao().task("incoming")!!.title})
    }
    @Test fun equalTimestampConflictRequiresAChoiceAndKeepsTheChoiceAcrossRotation(){
        val clock=Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"),ZoneOffset.UTC)
        data{TaskRepository(db,clock).save(TaskDraft("one","本機"))};val doc=importFile(id="one",timestamp=clock)
        val key=data{BackupRepository(db).preview(doc,RestoreMode.MERGE).plan.conflicts.single().id};navSettings();openImport()
        compose.onNodeWithTag("confirm_backup_restore").assertIsNotEnabled()
        compose.onNodeWithTag("backup_review_list").performScrollToNode(hasTestTag("backup_conflict_$key"))
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/todo-061-backup-preview.png").use { fd->java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
        compose.onNodeWithTag("backup_choice_${key}_FILE").performClick();await("backup_ready");compose.activityRule.scenario.recreate();await("backup_ready")
        compose.onNodeWithTag("confirm_backup_restore").assertIsEnabled().performClick();closed();assertEquals("檔案",data{db.dao().task("one")!!.title})
    }
    @Test fun aNewerLocalEditInvalidatesThePreviewAndRequiresAnExplicitRefresh(){
        seedLocal();importFile();navSettings();openImport()
        data{val old=db.dao().task("one")!!;TaskRepository(db).save(TaskDraft("one","已更新",expectedRevision=old.revision))}
        compose.onNodeWithTag("confirm_backup_restore").performClick();await("backup_preview_error");assertNull(data{db.dao().task("incoming")})
        compose.onNodeWithTag("backup_refresh").performClick();await("backup_ready");compose.onNodeWithTag("confirm_backup_restore").performClick();closed()
        assertEquals("已更新",data{db.dao().task("one")!!.title});assertNotNull(data{db.dao().task("incoming")})
    }
    @Test fun externalDocumentReturnKeepsSettingsButOrdinaryBackgroundReturnGoesHome(){
        navSettings();compose.runOnIdle{ViewModelProvider(compose.activity)[TodoViewModel::class.java].externalDocumentFlow=true}
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("page_title").assertTextEquals("設定")
        compose.runOnIdle{ViewModelProvider(compose.activity)[TodoViewModel::class.java].externalDocumentFlow=false}
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("page_title").assertTextEquals("日程")
    }
}
