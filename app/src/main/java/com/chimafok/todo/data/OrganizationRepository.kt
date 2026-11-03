package com.chimafok.todo.data

import com.chimafok.todo.R
import com.chimafok.todo.i18n.tr
import androidx.room.withTransaction
import com.chimafok.todo.domain.*
import java.time.Clock
import java.util.Locale

data class OrganizationDeletePreview(val tags: Boolean, val revisions: Map<String, Long>, val names: List<String>,
    val tasks: List<TaskEntity>, val series: Map<String, Long>, val targets: List<CategoryEntity>, val canceled: Set<String> = emptySet())

class OrganizationRepository(private val db: TodoDatabase, private val clock: Clock = DeviceClock) {
    private fun cleanName(input: String): String = input.trim().also {
        require(it.isNotEmpty() && it.length <= 50) { tr(R.string.text_names_must_contain_characters_b89d94) }
    }
    private fun key(name: String) = name.trim().lowercase(Locale.ROOT)

    suspend fun saveCategory(id: String, input: String, expectedRevision: Long?) = db.withTransaction {
        val name = cleanName(input)
        val all = db.dao().allCategories()
        val old = all.find { it.id == id }
        require(old?.revision == expectedRevision) { tr(R.string.text_the_category_changed_please_reopen_the_358087) }
        require(old?.systemKind != SystemCategory.UNCATEGORIZED) { tr(R.string.text_uncategorized_cannot_be_renamed_90fca9) }
        require(all.none { it.id != id && key(it.name) == key(name) }) { tr(R.string.text_a_category_with_this_name_already_766ce7) }
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
        require(old?.revision == expectedRevision) { tr(R.string.text_the_tag_changed_please_reopen_the_e0cf98) }
        require(all.none { it.id != id && it.nameKey == key(name) }) { tr(R.string.text_a_tag_with_this_name_already_678280) }
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
            require(items.map { it.id } == expectedOrder) { tr(R.string.text_sort_order_changed_please_try_again_da8edb) }
            val index = items.indexOfFirst { it.id == id }
            require(index >= 0 && index + delta in items.indices)
            java.util.Collections.swap(items, index, index + delta)
            items.forEachIndexed { position, tag -> if (tag.sortOrder != position) db.dao().updateTag(tag.copy(sortOrder = position, updatedAt = now, revision = tag.revision + 1)) }
        } else {
            val items = db.dao().allCategories().toMutableList()
            require(items.map { it.id } == expectedOrder) { tr(R.string.text_sort_order_changed_please_try_again_da8edb) }
            val index = items.indexOfFirst { it.id == id }
            require(index >= 0 && index + delta in items.indices)
            java.util.Collections.swap(items, index, index + delta)
            items.forEachIndexed { position, category -> if (category.sortOrder != position) db.dao().updateCategory(category.copy(sortOrder = position, updatedAt = now, revision = category.revision + 1)) }
        }
    }

    suspend fun reorder(tags: Boolean, expectedOrder: List<String>, order: List<String>, revisions: Map<String, Long>) = db.withTransaction {
        require(order.size == expectedOrder.size && order.toSet() == expectedOrder.toSet()) { tr(R.string.text_the_items_to_sort_changed_please_ddc898) }
        val now = clock.millis()
        if (tags) {
            val rows = db.dao().allTags()
            require(rows.map { it.id } == expectedOrder && rows.associate { it.id to it.revision } == revisions) { tr(R.string.text_tags_changed_please_sort_again_a3a982) }
            val byId = rows.associateBy { it.id }
            order.forEachIndexed { index, id -> val row = byId.getValue(id)
                if (row.sortOrder != index) db.dao().updateTag(row.copy(sortOrder = index, updatedAt = now, revision = row.revision + 1))
            }
        } else {
            val rows = db.dao().allCategories()
            require(rows.map { it.id } == expectedOrder && rows.associate { it.id to it.revision } == revisions) { tr(R.string.text_categories_changed_please_sort_again_18a4fa) }
            val byId = rows.associateBy { it.id }
            order.forEachIndexed { index, id -> val row = byId.getValue(id)
                if (row.sortOrder != index) db.dao().updateCategory(row.copy(sortOrder = index, updatedAt = now, revision = row.revision + 1))
            }
        }
    }

    suspend fun previewDelete(ids: Set<String>, tags: Boolean): OrganizationDeletePreview = db.withTransaction {
        require(ids.isNotEmpty()) { tr(R.string.text_select_items_to_delete_first_962af4) }
        if (tags) {
            val selected = db.dao().allTags().filter { it.id in ids }
            require(selected.size == ids.size) { tr(R.string.text_tags_changed_please_select_again_5cd8a0) }
            val tasks = selected.flatMap { db.dao().taggedTasks(it.id) }.distinctBy { it.id }
            val series = selected.flatMap { db.dao().taggedSeries(it.id) }.distinct().map { requireNotNull(db.dao().series(it)) }
            OrganizationDeletePreview(true, selected.associate { it.id to it.revision }, selected.map { it.name }, tasks,
                series.associate { it.id to it.revision }, emptyList())
        } else {
            val categories = db.dao().allCategories()
            val selected = categories.filter { it.id in ids }
            require(selected.size == ids.size && selected.all { canDeleteCategory(it.id, it.systemKind) }) { tr(R.string.text_protected_categories_cannot_be_deleted_please_58b63c) }
            val tasks = selected.flatMap { db.dao().categoryTasks(it.id) }
            val series = selected.flatMap { db.dao().categorySeries(it.id) }
            OrganizationDeletePreview(false, selected.associate { it.id to it.revision }, selected.map { it.name }, tasks,
                series.associate { it.id to it.revision }, categories.filter { it.id !in ids },
                tasks.filter { db.dao().occurrence(it.id)?.excludedByRule == true }.map { it.id }.toSet())
        }
    }

    suspend fun deleteBatch(preview: OrganizationDeletePreview, targetId: String, choices: Map<String, TaskStatus>) = db.withTransaction {
        val current = previewDelete(preview.revisions.keys, preview.tags)
        require(current == preview) { tr(R.string.text_content_changed_cancel_and_preview_again_ba665e) }
        if (preview.tags) preview.revisions.forEach { (id, revision) -> deleteTag(id, revision) }
        else {
            val target = requireNotNull(preview.targets.find { it.id == targetId }) { tr(R.string.text_choose_a_destination_category_that_is_63cf11) }
            require(db.dao().category(targetId) == target) { tr(R.string.text_the_destination_category_changed_please_preview_6e24c0) }
            preview.revisions.forEach { (id, revision) ->
                val tasks = preview.tasks.filter { it.categoryId == id }.associate { it.id to it.revision }
                deleteCategory(id, revision, targetId, tasks, choices.filterKeys { it in tasks })
            }
        }
    }

    suspend fun deleteTag(id: String, expectedRevision: Long) = db.withTransaction {
        val tag = requireNotNull(db.dao().tag(id)) { tr(R.string.text_tag_not_found_1438c2) }
        require(tag.revision == expectedRevision) { tr(R.string.text_the_tag_changed_please_reopen_it_d6c6f9) }
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
        val source = requireNotNull(db.dao().category(id)) { tr(R.string.text_category_not_found_a4acb7) }
        require(canDeleteCategory(source.id, source.systemKind)) { tr(R.string.text_protected_categories_cannot_be_deleted_8fbb17) }
        require(source.revision == expectedRevision) { tr(R.string.text_the_category_changed_please_reopen_it_6747e0) }
        val target = requireNotNull(db.dao().category(targetId)) { tr(R.string.text_destination_category_not_found_3246c5) }
        require(id != targetId) { tr(R.string.text_choose_a_different_category_45ddd2) }
        val affected = db.dao().categoryTasks(id)
        require(affected.associate { it.id to it.revision } == taskRevisions) { tr(R.string.text_tasks_in_the_category_changed_check_51d5bd) }
        val assignment = target.systemKind == SystemCategory.ASSIGNMENTS
        if (assignment) require(assignmentChoices.keys == taskRevisions.keys && assignmentChoices.values.none { it == TaskStatus.OPEN }) { tr(R.string.text_choose_an_assignment_status_for_every_92c8a0) }
        if (assignment) require(affected.filter { db.dao().occurrence(it.id)?.excludedByRule == true }
            .all { assignmentChoices[it.id] == TaskStatus.NOT_STARTED }) { tr(R.string.text_rule_canceled_items_moved_to_assignments_6257ff) }
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
