package com.makaohub.android.data

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

/** Reports bytes written, not approval. The caller retains and wipes the private buffer. */
internal class EvidenceUploadBody(
    private val bytes: ByteArray,
    private val mime: MediaType,
    private val progress: (Int) -> Unit
) : RequestBody() {
    override fun contentType() = mime
    override fun contentLength() = bytes.size.toLong()
    override fun writeTo(sink: BufferedSink) {
        require(bytes.isNotEmpty())
        var offset = 0
        var last = -1
        progress(0)
        while (offset < bytes.size) {
            val count = minOf(8192, bytes.size - offset)
            sink.write(bytes, offset, count)
            offset += count
            val percent = (offset * 100L / bytes.size).toInt()
            if (percent != last) { progress(percent); last = percent }
        }
    }
}
