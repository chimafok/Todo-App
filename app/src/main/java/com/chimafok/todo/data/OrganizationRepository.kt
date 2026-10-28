package com.chimafok.todo.data

import androidx.room.withTransaction
import com.chimafok.todo.domain.*
import java.time.Clock
import java.util.Locale

data class OrganizationDeletePreview(val tags: Boolean, val revisions: Map<String, Long>, val names: List<String>,
    val tasks: List<TaskEntity>, val series: Map<String, Long>, val targets: List<CategoryEntity>, val canceled: Set<String> = emptySet())

class OrganizationRepository(private val db: TodoDatabase, private val clock: Clock = DeviceClock) {
    private fun cleanName(input: String): String = input.trim().also {
        require(it.isNotEmpty() && it.length <= 50) { "名稱須為 1–50 個字元" }
    }
    private fun key(name: String) = name.trim().lowercase(Locale.ROOT)

    suspend fun saveCategory(id: String, input: String, expectedRevision: Long?) = db.withTransaction {
        val name = cleanName(input)
        val all = db.dao().allCategories()
        val old = all.find { it.id == id }
        require(old?.revision == expectedRevision) { "分類已更新，請重新開啟編輯" }
        require(old?.systemKind != SystemCategory.UNCATEGORIZED) { "未分類不可改名" }
        require(all.none { it.id != id && key(it.name) == key(name) }) { "分類名稱已存在" }
        val now = clock.millis()
        if (old == null) db.dao().insertCategory(CategoryEntity(id, name, sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAt = now, updatedAt = now))
        else {
            if (old.name != name) db.dao().categoryTasks(id).forEach { RecurringRepository(db, clock).snapshot(it.id) }
            db.dao().updateCategory(old.copy(name = name, updatedAt = now, revision = old.revision + 1))
        }
    }

    suspend fun saveTag(id: String, input: String, expectedRevision: Long?) = db.withTransaction {
        val name = cleanName(input)
        val all = db.dao().allTags()
        val old = all.find { it.id == id }
        require(old?.revision == expectedRevision) { "標籤已更新，請重新開啟編輯" }
        require(all.none { it.id != id && it.nameKey == key(name) }) { "標籤名稱已存在" }
        val now = clock.millis()
        if (old == null) db.dao().insertTag(TagEntity(id, name, key(name), (all.maxOfOrNull { it.sortOrder } ?: -1) + 1, now, now))
        else {
            if (old.name != name) db.dao().taggedTasks(id).forEach { RecurringRepository(db, clock).snapshot(it.id) }
            db.dao().updateTag(old.copy(name = name, nameKey = key(name), updatedAt = now, revision = old.revision + 1))
        }
    }

    suspend fun move(id: String, tags: Boolean, delta: Int, expectedOrder: List<String>) = db.withTransaction {
        require(delta == -1 || delta == 1)
        val now = clock.millis()
        if (tags) {
            val items = db.dao().allTags().toMutableList()
            require(items.map { it.id } == expectedOrder) { "排序已更新，請重試" }
            val index = items.indexOfFirst { it.id == id }
            require(index >= 0 && index + delta in items.indices)
            java.util.Collections.swap(items, index, index + delta)
            items.forEachIndexed { position, tag -> if (tag.sortOrder != position) db.dao().updateTag(tag.copy(sortOrder = position, updatedAt = now, revision = tag.revision + 1)) }
        } else {
            val items = db.dao().allCategories().toMutableList()
            require(items.map { it.id } == expectedOrder) { "排序已更新，請重試" }
            val index = items.indexOfFirst { it.id == id }
            require(index >= 0 && index + delta in items.indices)
            java.util.Collections.swap(items, index, index + delta)
            items.forEachIndexed { position, category -> if (category.sortOrder != position) db.dao().updateCategory(category.copy(sortOrder = position, updatedAt = now, revision = category.revision + 1)) }
        }
    }

    suspend fun reorder(tags: Boolean, expectedOrder: List<String>, order: List<String>, revisions: Map<String, Long>) = db.withTransaction {
        require(order.size == expectedOrder.size && order.toSet() == expectedOrder.toSet()) { "排序內容已變更，請重試" }
        val now = clock.millis()
        if (tags) {
            val rows = db.dao().allTags()
            require(rows.map { it.id } == expectedOrder && rows.associate { it.id to it.revision } == revisions) { "標籤已更新，請重新排序" }
            val byId = rows.associateBy { it.id }
            order.forEachIndexed { index, id -> val row = byId.getValue(id)
                if (row.sortOrder != index) db.dao().updateTag(row.copy(sortOrder = index, updatedAt = now, revision = row.revision + 1))
            }
        } else {
            val rows = db.dao().allCategories()
            require(rows.map { it.id } == expectedOrder && rows.associate { it.id to it.revision } == revisions) { "分類已更新，請重新排序" }
            val byId = rows.associateBy { it.id }
            order.forEachIndexed { index, id -> val row = byId.getValue(id)
                if (row.sortOrder != index) db.dao().updateCategory(row.copy(sortOrder = index, updatedAt = now, revision = row.revision + 1))
            }
        }
    }

    suspend fun previewDelete(ids: Set<String>, tags: Boolean): OrganizationDeletePreview = db.withTransaction {
        require(ids.isNotEmpty()) { "請先選擇要刪除的項目" }
        if (tags) {
            val selected = db.dao().allTags().filter { it.id in ids }
            require(selected.size == ids.size) { "標籤已更新，請重新選擇" }
            val tasks = selected.flatMap { db.dao().taggedTasks(it.id) }.distinctBy { it.id }
            val series = selected.flatMap { db.dao().taggedSeries(it.id) }.distinct().map { requireNotNull(db.dao().series(it)) }
            OrganizationDeletePreview(true, selected.associate { it.id to it.revision }, selected.map { it.name }, tasks,
                series.associate { it.id to it.revision }, emptyList())
        } else {
            val categories = db.dao().allCategories()
            val selected = categories.filter { it.id in ids }
            require(selected.size == ids.size && selected.all { it.systemKind == null }) { "系統分類不能刪除，請重新選擇" }
            val tasks = selected.flatMap { db.dao().categoryTasks(it.id) }
            val series = selected.flatMap { db.dao().categorySeries(it.id) }
            OrganizationDeletePreview(false, selected.associate { it.id to it.revision }, selected.map { it.name }, tasks,
                series.associate { it.id to it.revision }, categories.filter { it.id !in ids },
                tasks.filter { db.dao().occurrence(it.id)?.excludedByRule == true }.map { it.id }.toSet())
        }
    }

    suspend fun deleteBatch(preview: OrganizationDeletePreview, targetId: String, choices: Map<String, TaskStatus>) = db.withTransaction {
        val current = previewDelete(preview.revisions.keys, preview.tags)
        require(current == preview) { "內容已更新，請取消後重新預覽" }
        if (preview.tags) preview.revisions.forEach { (id, revision) -> deleteTag(id, revision) }
        else {
            val target = requireNotNull(preview.targets.find { it.id == targetId }) { "請選擇未被刪除的目標分類" }
            require(db.dao().category(targetId) == target) { "目標分類已更新，請重新預覽" }
            preview.revisions.forEach { (id, revision) ->
                val tasks = preview.tasks.filter { it.categoryId == id }.associate { it.id to it.revision }
                deleteCategory(id, revision, targetId, tasks, choices.filterKeys { it in tasks })
            }
        }
    }

    suspend fun deleteTag(id: String, expectedRevision: Long) = db.withTransaction {
        val tag = requireNotNull(db.dao().tag(id)) { "找不到標籤" }
        require(tag.revision == expectedRevision) { "標籤已更新，請重新開啟" }
        val recurring = RecurringRepository(db, clock)
        db.dao().taggedTasks(id).forEach { recurring.snapshot(it.id) }
        db.dao().taggedSeries(id).forEach { seriesId -> db.dao().series(seriesId)?.let {
            db.dao().updateSeries(it.copy(updatedAt = clock.millis(), revision = it.revision + 1))
        } }
        db.dao().taggedTasks(id).forEach { db.dao().update(it.copy(updatedAt = clock.millis(), revision = it.revision + 1)) }
        db.dao().deleteTag(id) // FK cascade removes links, never tasks.
    }

    suspend fun deleteCategory(id: String, expectedRevision: Long, targetId: String,
        taskRevisions: Map<String, Long>, assignmentChoices: Map<String, TaskStatus>) = db.withTransaction {
        val source = requireNotNull(db.dao().category(id)) { "找不到分類" }
        require(source.systemKind == null) { "系統分類不可刪除" }
        require(source.revision == expectedRevision) { "分類已更新，請重新開啟" }
        val target = requireNotNull(db.dao().category(targetId)) { "找不到目標分類" }
        require(id != targetId) { "請選擇其他分類" }
        val affected = db.dao().categoryTasks(id)
        require(affected.associate { it.id to it.revision } == taskRevisions) { "分類內的任務已更新，請重新檢查後重試" }
        val assignment = target.systemKind == SystemCategory.ASSIGNMENTS
        if (assignment) require(assignmentChoices.keys == taskRevisions.keys && assignmentChoices.values.none { it == TaskStatus.OPEN }) { "請為每項任務選擇作業狀態" }
        if (assignment) require(affected.filter { db.dao().occurrence(it.id)?.excludedByRule == true }
            .all { assignmentChoices[it.id] == TaskStatus.NOT_STARTED }) { "規則取消的項目移入作業時只能對應為未開始，不會啟用或完成" }
        val now = clock.millis()
        val recurring = RecurringRepository(db, clock)
        affected.forEach { recurring.snapshot(it.id) }
        affected.forEach { task ->
            val status = if (assignment) assignmentChoices.getValue(task.id) else convertStatus(task.status, false, study = targetId == STUDY_ID)
            db.dao().update(task.copy(categoryId = targetId, status = status,
                completedAt = if (status == TaskStatus.COMPLETED) task.completedAt ?: now else null,
                updatedAt = now, revision = task.revision + 1))
        }
        db.dao().categorySeries(id).forEach { series -> db.dao().updateSeries(series.copy(
            categoryId = targetId, updatedAt = now, revision = series.revision + 1)) }
        db.dao().deleteCategory(id)
        affected.filter { it.deletedAt == null && it.status != TaskStatus.COMPLETED &&
            db.dao().task(it.id)?.status == TaskStatus.COMPLETED }.forEach {
            recurring.snapshot(it.id, replaceContent = true)
            recurring.advance(it.id)
        }
    }
}
