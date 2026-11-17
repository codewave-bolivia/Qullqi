package com.codewave.qullqi

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Target of the provider's redirect `qullqi://kyc/result` (spec 005, research R2). No UI: it only
 * brings the existing AuthActivity back (clearing the Custom Tab above it); the KYC flow checks the
 * result when AuthActivity resumes. It carries no data and trusts none.
 *
 * Security (spec 003 FR-002a): no start extra is passed, so if no AuthActivity exists (cold start, or
 * another app firing the link) the new one starts at the splash → login, never directly in KYC.
 */
class KycReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, AuthActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
        noTransition(closing = true)
    }
}
