package com.makaohub.android.data

import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceUploadBodyTest {
    @Test fun writesTheExactPrivateBytesAndReportsRealProgress() {
        val source = ByteArray(20_000) { (it % 251).toByte() }
        val observed = mutableListOf<Int>()
        val body = EvidenceUploadBody(source, "image/jpeg".toMediaType(), observed::add)
        val sink = Buffer()

        body.writeTo(sink)

        assertArrayEquals(source, sink.readByteArray())
        assertEquals(source.size.toLong(), body.contentLength())
        assertEquals("image/jpeg", body.contentType().toString())
        assertEquals(0, observed.first())
        assertEquals(100, observed.last())
        assertTrue(observed.zipWithNext().all { (before, after) -> after >= before })
    }

    @Test fun anEmptyEvidenceBufferIsRejectedBeforeItCanLookUploaded() {
        val body = EvidenceUploadBody(byteArrayOf(), "image/jpeg".toMediaType()) { }
        val error = runCatching { body.writeTo(Buffer()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
