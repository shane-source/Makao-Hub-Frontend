package com.makaohub.android.data

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

class VerificationPdfRejected : Exception("Choose an ownership PDF of at most 10 MB. Images are not accepted for ownership proof.")

/** File bytes stay in memory; server parsing and malware scanning remain authoritative. */
internal object VerificationDocuments {
    fun fromUri(resolver: ContentResolver, uri: Uri): ByteArray {
        if (resolver.getType(uri)?.lowercase() != "application/pdf") throw VerificationPdfRejected()
        return resolver.openInputStream(uri)?.use(::readPdf) ?: throw VerificationPdfRejected()
    }

    fun readPdf(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > 10_485_760) throw VerificationPdfRejected()
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        if (bytes.size < 8 || !bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII)))
            throw VerificationPdfRejected()
        return bytes // A header is not a security/validity verdict; confirmation comes from the backend.
    }
}
