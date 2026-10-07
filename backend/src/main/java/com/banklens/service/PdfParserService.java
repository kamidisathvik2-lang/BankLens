package com.banklens.service;

import com.banklens.exception.BankLensExceptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class PdfParserService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_PAGES = 20;
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    /**
     * Extract text from uploaded PDF.
     * Server-side extraction is more reliable than browser PDF.js
     * and keeps raw text off the client entirely.
     */
    public String extractText(MultipartFile file) {
        validateFile(file);

        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            int pages = Math.min(doc.getNumberOfPages(), MAX_PAGES);
            log.info("Extracting text from PDF: {} pages, {} bytes",
                    doc.getNumberOfPages(), file.getSize());

            var stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(pages);
            stripper.setSortByPosition(true);

            String text = stripper.getText(doc);

            if (text == null || text.trim().isEmpty()) {
                throw new BankLensExceptions.InvalidPdfException(
                        "No text content found in PDF. The file may be image-based or encrypted.");
            }

            log.info("Extracted {} characters from PDF", text.length());
            return text.trim();

        } catch (BankLensExceptions.InvalidPdfException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse PDF: {}", e.getMessage());
            throw new BankLensExceptions.InvalidPdfException(
                    "Failed to read PDF file. Please ensure it is a valid, unencrypted PDF.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BankLensExceptions.InvalidPdfException("No file provided");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BankLensExceptions.InvalidPdfException("File too large. Maximum size is 10MB");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean isPdf = PDF_CONTENT_TYPE.equals(contentType)
                || (filename != null && filename.toLowerCase().endsWith(".pdf"));
        if (!isPdf) {
            throw new BankLensExceptions.InvalidPdfException("Only PDF files are supported");
        }
    }
}
