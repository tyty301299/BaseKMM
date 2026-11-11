package com.example.kmmbase.domain.model

/**
 * The team's deployed server. Baked in so nobody has to type a URL on stage; the Connect screen
 * still accepts another origin.
 */
const val DEFAULT_SHADOW_ORIGIN = "https://fourward.thangqt.com"

/**
 * Access code for the public demo deployment, shared openly in the event link. It only gates a
 * throwaway hackathon server, and having it here means nobody types a code on stage. Leave blank
 * for any deployment holding real user data.
 */
const val DEFAULT_SHADOW_ACCESS_CODE = "fourward-codex-1411"
