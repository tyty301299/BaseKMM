package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.*

interface TeachRepository {
    suspend fun uploadVideo(source: TeachVideoSource, title: String, teacher: String, notes: String): TeachVideoTicket =
        throw UnsupportedOperationException("Máy chủ chưa hỗ trợ học từ video.")
    suspend fun startVideo(ticket: TeachVideoTicket, retry: Boolean = false) { throw UnsupportedOperationException() }
    suspend fun videoStatus(ticket: TeachVideoTicket): TeachVideoStatus = throw UnsupportedOperationException()
    suspend fun cancelVideo(ticket: TeachVideoTicket) { throw UnsupportedOperationException() }
    suspend fun deleteVideo(ticket: TeachVideoTicket) { throw UnsupportedOperationException() }
    suspend fun compileSkill(request: TeachRequest): TeachDraft
    suspend fun teachProgress(jobId: String): String
    suspend fun verifyStep(draft: TeachDraft, stepIndex: Int, referenceFrame: Int?): TeachDraft =
        throw UnsupportedOperationException("Dịch vụ chưa hỗ trợ kiểm chứng lại bài.")
    suspend fun saveSkill(draft: TeachDraft): String
}
