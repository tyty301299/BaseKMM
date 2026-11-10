package com.example.kmmbase.presentation.studio

import com.example.kmmbase.domain.model.*

data class StudioState(
    val data: StudioData = StudioData(),
    val loading: Boolean = true,
    val busy: String = "",
    val error: String? = null,
    val wardrobeFilter: String = "all",
    val itemDraft: StudioItem? = null,
    val title: String = "Lắp giá đỡ điện thoại",
    val recording: Boolean = false,
    val recorded: Boolean = false,
    val transcript: String = "Đặt chân đế nằm phẳng, gắn thanh đứng rồi siết khóa.",
    val draftSteps: List<String> = emptyList(),
    val savedSkillId: String? = null,
    val activeSkillId: String? = null,
    val stepIndex: Int = 0,
    val completed: Boolean = false,
    val question: String = "",
    val answer: String = "",
    val outfitNotes: String = "",
    val suggestions: List<StudioOutfit> = emptyList(),
    val suggested: Boolean = false,
) {
    val filteredItems: List<StudioItem> get() = data.items.filter { wardrobeFilter == "all" || it.category == wardrobeFilter }
    val activeSkill: StudioSkill? get() = data.skills.find { it.id == activeSkillId }
    val currentStep: String get() = activeSkill?.steps?.getOrNull(stepIndex).orEmpty()
    val canSaveSkill: Boolean get() = busy.isEmpty() && title.isNotBlank() && title.length <= 80 &&
        draftSteps.size in 1..12 && draftSteps.all { it.isNotBlank() && it.length <= 250 }
}
