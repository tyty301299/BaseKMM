package com.example.kmmbase.domain.model

data class TeachVideoTicket(val jobId: String, val token: String, val title: String, val teacher: String, val notes: String, val localUri: String = "")
data class TeachVideoStatus(val phase: String, val error: String = "", val draft: TeachDraft? = null)
