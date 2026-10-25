package com.chimafok.todo.data

import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@com.chimafok.todo.Regression101
@RunWith(AndroidJUnit4::class)
class NewTaskDraftStoreTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var directory:File
    @Before fun setup() {
        assertEquals("com.chimafok.todo.qa",context.packageName)
        directory=File(context.cacheDir,"draft-store-${UUID.randomUUID()}").also { assertTrue(it.mkdir()) }
    }
    @After fun cleanup() {
        if(::directory.isInitialized) {
            require(directory.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator))
            directory.deleteRecursively()
        }
    }
    @Test fun freshStoreRoundTripsAllRawInputsWithoutCreatingDatabaseRecords() {
        val draft=NewTaskDraft(UUID.randomUUID().toString()," 尚未完成 ","第一行\n第二行","1332","2", "Asia/Hong_Kong",123456789L,
            listOf("tag-a","tag-b"),"category-a","IN_PROGRESS","WEEKDAYS","N",listOf(1,7),"COUNT","261332","2026-10-25","invalid",
            "[]","[]","captured template stamp",listOf("inherited-a"))
        NewTaskDraftStore(directory).save(draft)
        assertEquals(draft,NewTaskDraftStore(directory).read())
        NewTaskDraftStore(directory).clear()
        assertNull(NewTaskDraftStore(directory).read())
    }
    @Test fun interruptedReplacementRestoresThePreviousDraft() {
        val draft=NewTaskDraft("fixture","previous valid draft")
        NewTaskDraftStore(directory).save(draft)
        val atomic=AtomicFile(File(directory,"new-task-draft-v1.json"))
        val stream=atomic.startWrite()
        stream.write("partial replacement".toByteArray())
        atomic.failWrite(stream)
        assertEquals(draft,NewTaskDraftStore(directory).read())
    }
    @Test fun oversizedSaveKeepsPreviousAndUnknownVersionIsNeverSilentlyDeleted() {
        val store=NewTaskDraftStore(directory)
        val good=NewTaskDraft("fixture","kept")
        store.save(good)
        try {store.save(good.copy(notes="x".repeat(NewTaskDraftStore.MAX_BYTES)));fail()} catch(_:IllegalArgumentException) {}
        assertEquals(good,store.read())
        val file=File(directory,"new-task-draft-v1.json")
        file.writeText("{\"version\":999}")
        try {store.read();fail()} catch(_:IllegalArgumentException) {}
        assertTrue(file.exists())
        assertEquals("{\"version\":999}",file.readText())
    }
}
