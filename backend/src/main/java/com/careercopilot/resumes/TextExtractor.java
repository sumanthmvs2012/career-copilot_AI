package com.careercopilot.resumes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/** Pulls plain text out of a PDF or DOCX. Never logs the text. */
@Component
public class TextExtractor {

    public static final String PDF = "application/pdf";
    public static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public String extract(String contentType, byte[] content) {
        String text;
        try {
            if (PDF.equals(contentType)) {
                try (PDDocument document = Loader.loadPDF(content)) {
                    text = new PDFTextStripper().getText(document);
                }
            } else if (DOCX.equals(contentType)) {
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
                        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    text = extractor.getText();
                }
            } else {
                throw new ResumeUnreadableException();
            }
        } catch (ResumeUnreadableException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ResumeUnreadableException(e);
        }
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            throw new ResumeUnreadableException();
        }
        return trimmed;
    }
}
