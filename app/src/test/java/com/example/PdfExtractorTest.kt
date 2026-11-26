package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.pdf.PdfDocumentProcessor
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
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

  @Test
  fun `extracts ordered bilingual chapters from EPUB OPF spine cleanly`() {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zos ->
      zos.putNextEntry(ZipEntry("META-INF/container.xml"))
      zos.write(
        """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
        """
          .trimIndent()
          .toByteArray(StandardCharsets.UTF_8)
      )
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
      zos.write(
        """
        <?xml version="1.0" encoding="utf-8"?>
        <package xmlns="http://www.idpf.org/2007/opf">
          <manifest>
            <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="ch1"/>
            <itemref idref="ch2"/>
          </spine>
        </package>
        """
          .trimIndent()
          .toByteArray(StandardCharsets.UTF_8)
      )
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
      zos.write(
        "<html><body><h1>Chapter One</h1><p>Welcome to bilingual reading.</p></body></html>"
          .toByteArray(StandardCharsets.UTF_8)
      )
      zos.closeEntry()

      zos.putNextEntry(ZipEntry("OEBPS/ch2.xhtml"))
      zos.write(
        "<html><body><p>القراءة المستمرة تصنع الطلاقة.</p></body></html>"
          .toByteArray(StandardCharsets.UTF_8)
      )
      zos.closeEntry()
    }

    val processor = PdfDocumentProcessor(ApplicationProvider.getApplicationContext())
    val result = processor.extractFromEpubBytes("Sample EPUB", baos.toByteArray())
    assertEquals(2, result.pageCount)
    assertTrue(result.text.contains("Chapter One"))
    assertTrue(result.text.contains("Welcome to bilingual reading."))
    assertTrue(result.text.contains("القراءة المستمرة تصنع الطلاقة."))
    assertTrue(result.text.indexOf("Chapter One") < result.text.indexOf("القراءة المستمرة"))
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
