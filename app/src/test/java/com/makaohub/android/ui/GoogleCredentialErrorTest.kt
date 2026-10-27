package com.makaohub.android.ui

import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCredentialErrorTest {
    @Test fun credentialFailuresAreActionableWithoutLeakingProviderDetails() {
        val privateProviderDetail = "private provider account detail"
        val cases = mapOf(
            GetCredentialProviderConfigurationException(privateProviderDetail) to "not configured",
            NoCredentialException(privateProviderDetail) to "No Google account",
            GetCredentialUnsupportedException(privateProviderDetail) to "not supported",
            GetCredentialInterruptedException(privateProviderDetail) to "interrupted",
            IllegalStateException(privateProviderDetail) to "Google rejected"
        )

        cases.forEach { (error, expected) ->
            val message = googleCredentialErrorMessage(error)
            assertTrue(message.contains(expected))
            assertFalse(message.contains(privateProviderDetail))
        }
    }
}
