package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.pdf.PdfDocumentProcessor
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfExtractorTest {

  @Test
  fun `extracts uncompressed and FlateDecode PDF text streams cleanly`() {
    val contentStream = "BT /F1 12 Tf 72 720 Td (Patient blood pressure 120/80 mmHg.) Tj ET"
    val compressed = deflate(contentStream.toByteArray(StandardCharsets.ISO_8859_1))

    val pdfHeader = "%PDF-1.4\n1 0 obj\n<< /Filter /FlateDecode /Length ${compressed.size} >>\nstream\n"
    val pdfFooter = "\nendstream\nendobj\n%%EOF"

    val baos = ByteArrayOutputStream()
    baos.write(pdfHeader.toByteArray(StandardCharsets.ISO_8859_1))
    baos.write(compressed)
    baos.write(pdfFooter.toByteArray(StandardCharsets.ISO_8859_1))

    val processor = PdfDocumentProcessor(ApplicationProvider.getApplicationContext())
    val extracted = processor.extractEmbeddedPdfText(baos.toByteArray())
    assertTrue(
      "Expected extracted PDF text to contain blood pressure, got: '$extracted'",
      extracted.contains("Patient blood pressure 120/80 mmHg."),
    )
  }

  private fun deflate(input: ByteArray): ByteArray {
    val deflater = Deflater()
    deflater.setInput(input)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buf = ByteArray(256)
    while (!deflater.finished()) {
      val n = deflater.deflate(buf)
      out.write(buf, 0, n)
    }
    deflater.end()
    return out.toByteArray()
  }
}
