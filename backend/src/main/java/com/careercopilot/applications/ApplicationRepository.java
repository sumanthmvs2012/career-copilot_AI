package com.careercopilot.applications;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByProfileIdOrderByCreatedAtDescIdDesc(Long profileId);

    Optional<Application> findByIdAndProfileId(Long id, Long profileId);

    boolean existsByProfileIdAndJobId(Long profileId, Long jobId);
}
