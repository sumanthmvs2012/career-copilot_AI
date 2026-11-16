package com.careercopilot.interviews;

import java.util.List;

public record InterviewQuestionsResponse(Long jobId, String jobTitle, String company,
        List<QuestionDto> questions) {

    public record QuestionDto(Long id, String question, String category) {

        static QuestionDto from(InterviewQuestion row) {
            return new QuestionDto(row.getId(), row.getQuestion(), row.getCategory());
        }
    }
}
