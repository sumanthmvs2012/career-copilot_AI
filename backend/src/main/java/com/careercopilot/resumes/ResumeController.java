package com.careercopilot.resumes;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The user id always comes from the token, so a user can only reach their own resumes. */
@RestController
@RequestMapping("/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeTailorService tailorService;
    private final ResumeJobLabels labels;

    public ResumeController(ResumeService resumeService, ResumeTailorService tailorService, ResumeJobLabels labels) {
        this.resumeService = resumeService;
        this.tailorService = tailorService;
        this.labels = labels;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeResponse upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        return ResumeResponse.from(resumeService.upload(userId(jwt), file));
    }

    @GetMapping
    public List<ResumeResponse> list(@AuthenticationPrincipal Jwt jwt) {
        List<Resume> found = resumeService.list(userId(jwt));
        Map<Long, ResumeJobLabel> byResume = labels.forResumes(found);
        return found.stream().map(resume -> ResumeResponse.from(resume, byResume.get(resume.getId()))).toList();
    }

    @GetMapping("/{id}")
    public ResumeDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Resume resume = resumeService.get(userId(jwt), id);
        return ResumeDetailResponse.from(resume, labels.forResume(resume));
    }

    @PostMapping("/{id}/tailor")
    @ResponseStatus(HttpStatus.CREATED)
    public TailoredResumeResponse tailor(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ResumeTailorRequest request) {
        return tailorService.tailor(userId(jwt), id, request.jobId());
    }

    @PostMapping("/{id}/suggestions")
    public SuggestionsResponse suggestions(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestParam Long jobId) {
        return tailorService.suggest(userId(jwt), id, jobId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        resumeService.delete(userId(jwt), id);
    }

    private static Long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
