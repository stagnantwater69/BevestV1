package com.jtexpress.bevest.data.firebase

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jtexpress.bevest.utils.AppError

private const val TAG = "BeVestFirestore"

fun Throwable.toAppError(): AppError = when {
    this is FirebaseNetworkException -> AppError.Network(this)
    this is FirebaseAuthInvalidUserException -> AppError.NotAuthorized("This account is disabled or no longer exists.")
    this is FirebaseAuthInvalidCredentialsException -> AppError.Validation("Incorrect email or password.")
    message?.contains("CONFIGURATION_NOT_FOUND") == true ->
        AppError.Validation("Email/Password sign-in is not enabled for this Firebase project. Enable it in Firebase Console → Authentication → Sign-in method.")
    this is FirebaseAuthException -> AppError.Validation(localizedMessage ?: "Sign-in failed.")
    this is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.NotAuthorized()
        FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound()
        FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.Network(this)
        FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
            // Firestore puts a one-click "create it here" console URL in the message.
            // Surface it in logcat so the fix is one tap away during development.
            Log.e(TAG, "Firestore query needs an index: " + message.orEmpty(), this)
            AppError.Configuration(
                "This list needs a database index that hasn't been created yet. " +
                    "Deploy the Firestore indexes (firebase deploy --only firestore:indexes).",
                this,
            )
        }
        else -> AppError.Unknown(this)
    }
    else -> AppError.Unknown(this)
}
