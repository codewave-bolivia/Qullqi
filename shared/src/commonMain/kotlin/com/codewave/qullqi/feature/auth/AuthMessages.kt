package com.codewave.qullqi.feature.auth

/** Texts shared by the auth flows for backend failures (spec 004, FR-003). */
object AuthMessages {
    /** No connection, server error or unknown error: nothing was consumed and data is kept. */
    const val UNAVAILABLE = "No pudimos conectarnos. Intenta de nuevo."
    const val SESSION_EXPIRED = "Tu sesión expiró. Ingresa tu PIN."
    const val PIN_REQUIRED = "Ingresa tu PIN para continuar."
}
