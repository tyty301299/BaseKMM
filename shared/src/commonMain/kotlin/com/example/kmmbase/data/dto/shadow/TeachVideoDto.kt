package com.example.kmmbase.data.dto.shadow

internal data class TeachVideoTicketDto(val jobId: String, val token: String, val title: String, val teacher: String, val notes: String)
internal data class TeachVideoStatusDto(val phase: String, val error: String, val draft: TeachDraftDto?)
