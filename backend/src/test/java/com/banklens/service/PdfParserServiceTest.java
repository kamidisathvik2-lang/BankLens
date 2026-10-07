package com.banklens.service;

import com.banklens.exception.BankLensExceptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PdfParserService")
class PdfParserServiceTest {

    private PdfParserService pdfParserService;

    @BeforeEach
    void setUp() {
        pdfParserService = new PdfParserService();
    }

    @Test
    @DisplayName("rejects null file")
    void extractText_nullFile() {
        assertThatThrownBy(() -> pdfParserService.extractText(null))
                .isInstanceOf(BankLensExceptions.InvalidPdfException.class)
                .hasMessageContaining("No file provided");
    }

    @Test
    @DisplayName("rejects empty file")
    void extractText_emptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> pdfParserService.extractText(file))
                .isInstanceOf(BankLensExceptions.InvalidPdfException.class);
    }

    @Test
    @DisplayName("rejects non-PDF content type")
    void extractText_notPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.txt", "text/plain", "some text".getBytes());

        assertThatThrownBy(() -> pdfParserService.extractText(file))
                .isInstanceOf(BankLensExceptions.InvalidPdfException.class)
                .hasMessageContaining("Only PDF files are supported");
    }

    @Test
    @DisplayName("rejects file exceeding 10MB")
    void extractText_fileTooLarge() {
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", largeContent);

        assertThatThrownBy(() -> pdfParserService.extractText(file))
                .isInstanceOf(BankLensExceptions.InvalidPdfException.class)
                .hasMessageContaining("too large");
    }

    @Test
    @DisplayName("rejects invalid PDF bytes")
    void extractText_invalidPdfBytes() {
        // Valid content-type but not real PDF bytes
        byte[] notPdf = "This is not a PDF file content at all".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", notPdf);

        assertThatThrownBy(() -> pdfParserService.extractText(file))
                .isInstanceOf(BankLensExceptions.InvalidPdfException.class);
    }

    @Test
    @DisplayName("accepts .pdf extension even with generic content type")
    void extractText_pdfExtensionNoContentType() {
        // Some browsers send octet-stream for PDF files
        byte[] notPdf = "content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/octet-stream", notPdf);

        // Should pass the validation check (content-type check) but fail on actual PDF parsing
        assertThatThrownBy(() -> pdfParserService.extractText(file))
                .isInstanceOf(BankLensExceptions.InvalidPdfException.class)
                .hasMessageNotContaining("Only PDF files are supported"); // passes validation, fails parsing
    }
}
