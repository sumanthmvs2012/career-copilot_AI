package com.careercopilot.interviews;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    List<InterviewQuestion> findByProfileIdAndJobIdOrderByPositionAscIdAsc(Long profileId, Long jobId);

    void deleteByProfileIdAndJobId(Long profileId, Long jobId);
}
