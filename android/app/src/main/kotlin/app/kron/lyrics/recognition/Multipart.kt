package app.kron.lyrics.recognition

import java.io.ByteArrayOutputStream

/** A multipart/form-data body: the plain [fields], then one file part, then the closing boundary. */
fun multipartBody(
    boundary: String,
    fields: Map<String, String>,
    fileField: String,
    fileName: String,
    fileContentType: String,
    file: ByteArray,
): ByteArray {
    val out = ByteArrayOutputStream(file.size + 512)
    fun text(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
    for ((name, value) in fields) {
        text("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
    }
    text("--$boundary\r\nContent-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n")
    text("Content-Type: $fileContentType\r\n\r\n")
    out.write(file)
    text("\r\n--$boundary--\r\n")
    return out.toByteArray()
}
