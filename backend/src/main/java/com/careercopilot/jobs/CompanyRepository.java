package com.careercopilot.jobs;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findFirstByNameIgnoreCase(String name);

    Optional<Company> findFirstByAtsTypeAndBoardToken(String atsType, String boardToken);
}
