package com.dboycht.taskplanner.data

import com.dboycht.taskplanner.model.BUILT_IN_PRESETS
import com.dboycht.taskplanner.model.ReminderPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 提醒预设仓库。内置预设不可删除；seed 幂等（表非空即跳过）。 */
class PresetRepository(
    private val dao: ReminderPresetDao,
    private val scope: CoroutineScope,
) {

    fun observePresets(): Flow<List<ReminderPreset>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getPreset(id: Long): ReminderPreset? = dao.getById(id)?.toDomain()

    suspend fun save(preset: ReminderPreset): Long {
        val entity = preset.toEntity()
        return if (preset.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            preset.id
        }
    }

    /** 内置预设拒绝删除。 */
    suspend fun delete(id: Long) {
        val existing = dao.getById(id) ?: return
        if (existing.isBuiltIn) return
        dao.deleteById(id)
    }

    /** 首次启动灌入内置预设（count()==0 才执行，用户删自建后不会重灌）。 */
    fun ensureSeeded() {
        scope.launch {
            if (dao.count() == 0) {
                dao.insertAll(BUILT_IN_PRESETS.map { it.toEntity() })
            }
        }
    }
}
