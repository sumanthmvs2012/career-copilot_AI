package com.careercopilot.interviews;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores a generated list in its own transaction, so a lost race on the unique position can be caught by the caller. */
@Component
public class InterviewQuestionWriter {

    private final InterviewQuestionRepository questions;

    public InterviewQuestionWriter(InterviewQuestionRepository questions) {
        this.questions = questions;
    }

    @Transactional
    public List<InterviewQuestion> saveAll(List<InterviewQuestion> rows) {
        List<InterviewQuestion> saved = questions.saveAll(rows);
        questions.flush();
        return saved;
    }
}
