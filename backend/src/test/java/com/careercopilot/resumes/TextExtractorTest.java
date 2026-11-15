package com.careercopilot.resumes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void extractsTextFromAPdf() throws IOException {
        String text = extractor.extract(TextExtractor.PDF, TestFiles.pdf("Java developer with Spring experience"));

        assertThat(text).contains("Java developer with Spring experience");
    }

    @Test
    void extractsTextFromADocx() throws IOException {
        String text = extractor.extract(TextExtractor.DOCX, TestFiles.docx("Senior engineer in Berlin"));

        assertThat(text).contains("Senior engineer in Berlin");
    }

    @Test
    void corruptContentIsUnreadable() {
        byte[] junk = "%PDF-not really a pdf".getBytes();

        assertThatThrownBy(() -> extractor.extract(TextExtractor.PDF, junk))
                .isInstanceOf(ResumeUnreadableException.class);
    }

    @Test
    void aDocumentWithNoTextIsUnreadable() throws IOException {
        byte[] empty = TestFiles.docx("   ");

        assertThatThrownBy(() -> extractor.extract(TextExtractor.DOCX, empty))
                .isInstanceOf(ResumeUnreadableException.class);
    }

    @Test
    void anUnknownContentTypeIsUnreadable() {
        assertThatThrownBy(() -> extractor.extract("text/plain", new byte[] {1}))
                .isInstanceOf(ResumeUnreadableException.class);
    }
}
