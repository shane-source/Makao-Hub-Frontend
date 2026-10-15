package com.makaohub.android

import com.makaohub.android.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class VerificationPdfTest {
    @Test fun fileSelectionKeepsPdfBytesUnchangedWithoutClaimingApproval() {
        val bytes = "%PDF-1.7\nselection-only-test\n%%EOF".toByteArray()
        assertArrayEquals(bytes, VerificationDocuments.readPdf(ByteArrayInputStream(bytes)))
        assertEquals("application/pdf", OwnerEvidence.OWNERSHIP_PROOF.uploadContentType)
        assertFalse(verificationReady(listOf(OwnerVerificationDocument("pdf", "OWNERSHIP_PROOF", "UPLOADING", "application/pdf"))))
    }
    @Test fun emptyFakeOrOversizeFilesAreRejectedBeforeUpload() {
        for (bytes in listOf(byteArrayOf(), "not a PDF".toByteArray(), ByteArray(10_485_761))) {
            assertThrows(VerificationPdfRejected::class.java) { VerificationDocuments.readPdf(ByteArrayInputStream(bytes)) }
        }
    }
    @Test fun oldOwnershipImageOrMissingMimeCannotSatisfyPdfRequirement() {
        assertFalse(evidenceReady(OwnerEvidence.OWNERSHIP_PROOF, listOf(OwnerVerificationDocument("old", "OWNERSHIP_PROOF", "READY", "image/jpeg"))))
        assertFalse(evidenceReady(OwnerEvidence.OWNERSHIP_PROOF, listOf(OwnerVerificationDocument("old", "OWNERSHIP_PROOF", "READY"))))
        assertTrue(evidenceReady(OwnerEvidence.OWNERSHIP_PROOF, listOf(OwnerVerificationDocument("new", "OWNERSHIP_PROOF", "READY", "application/pdf", securityChecked = true))))
    }
    @Test fun onlyTheEvidenceConfirmationRouteGetsTheLongerHttpBudget() {
        assertTrue(isEvidenceConfirmation("/api/v1/landlord/verification/documents/6cc1d42d-9f36-4a0d-bfdb-d348438a6658/confirm"))
        assertFalse(isEvidenceConfirmation("/api/v1/landlord/verification/documents/upload-url"))
        assertFalse(isEvidenceConfirmation("/api/v1/landlord/verification/documents/id/confirm/extra"))
        assertFalse(isEvidenceConfirmation("/api/v1/payments/mock/completions"))
    }
}
