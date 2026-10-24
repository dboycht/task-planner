package com.dboycht.taskplanner.ui.preset

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dboycht.taskplanner.TaskPlannerApp
import com.dboycht.taskplanner.model.ReminderPreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PresetListViewModel(app: Application) : AndroidViewModel(app) {

    data class PresetGroups(
        val builtIn: List<ReminderPreset> = emptyList(),
        val custom: List<ReminderPreset> = emptyList(),
    )

    val groups: StateFlow<PresetGroups> =
        (app as TaskPlannerApp).presetRepository.observePresets()
            .map { PresetGroups(builtIn = it.filter { p -> p.isBuiltIn }, custom = it.filter { p -> !p.isBuiltIn }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresetGroups())
}
