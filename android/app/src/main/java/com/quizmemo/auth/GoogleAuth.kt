package com.quizmemo.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.quizmemo.BuildConfig

/**
 * Wraps Credential Manager + "Sign in with Google" flow.
 *
 * Uses GetSignInWithGoogleOption (the "branded button" flow) instead of GetGoogleIdOption
 * (the personalized chooser). The button flow is more reliable for first-time sign-ins
 * where Google Play Services has no prior authorization record for the app, which is
 * the source of spurious 28444 errors on fresh installs.
 *
 * The serverClientId is the Web OAuth client ID from Google Cloud Console — NOT the
 * Android client ID. Injected at build time via BuildConfig from local.properties.
 * See CLAUDE.md → "Google Sign-In setup".
 */
object GoogleAuth {
    private val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    suspend fun getIdToken(context: Context): String {
        check(webClientId.isNotEmpty()) {
            "GOOGLE_WEB_CLIENT_ID is empty. Set quizmemo.google.webClientId in android/local.properties."
        }

        val credentialManager = CredentialManager.create(context)

        val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential

        if (credential is CustomCredential &&
            credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }

        error("Unexpected credential type: ${credential.type}")
    }
}
