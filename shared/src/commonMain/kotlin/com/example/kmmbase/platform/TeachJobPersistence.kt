package com.example.kmmbase.platform

import com.example.kmmbase.domain.model.TeachVideoTicket

/** Native private storage, scoped to server origin. Never log or display the ticket token. */
interface TeachJobPersistence {
    fun load(): TeachVideoTicket?
    fun save(ticket: TeachVideoTicket?)
}
