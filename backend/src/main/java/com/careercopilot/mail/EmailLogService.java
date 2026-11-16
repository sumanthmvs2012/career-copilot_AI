package com.careercopilot.mail;

import com.careercopilot.applications.ApplicationNotFoundException;
import com.careercopilot.applications.ApplicationRepository;
import com.careercopilot.profiles.Profile;
import com.careercopilot.profiles.ProfileRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailLogService {

    private final ProfileRepository profiles;
    private final ApplicationRepository applications;
    private final EmailLogRepository emailLogs;

    public EmailLogService(ProfileRepository profiles, ApplicationRepository applications,
            EmailLogRepository emailLogs) {
        this.profiles = profiles;
        this.applications = applications;
        this.emailLogs = emailLogs;
    }

    /** Newest first. Another user's application and a missing one are both a 404. */
    @Transactional(readOnly = true)
    public List<EmailLogResponse> forApplication(Long userId, Long applicationId) {
        Profile profile = profiles.findByUserId(userId).orElseThrow(ApplicationNotFoundException::new);
        applications.findByIdAndProfileId(applicationId, profile.getId())
                .orElseThrow(ApplicationNotFoundException::new);
        return emailLogs.findByApplicationIdOrderByOccurredAtDescIdDesc(applicationId).stream()
                .map(EmailLogResponse::from).toList();
    }
}
