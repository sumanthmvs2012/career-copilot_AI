package com.careercopilot.jobs;

import java.time.Instant;
import java.util.List;

public record JobResponse(
        Long id,
        String title,
        String companyName,
        String location,
        String remoteType,
        String source,
        String sourceUrl,
        String description,
        Instant postedAt,
        Instant createdAt,
        String category,
        List<LocationDto> locations) {

    public static JobResponse from(Job job, String companyName, List<LocationDto> locations) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                companyName,
                job.getLocation(),
                job.getRemoteType(),
                job.getSource(),
                job.getSourceUrl(),
                job.getDescription(),
                job.getPostedAt(),
                job.getCreatedAt(),
                job.getCategory(),
                locations);
    }
}
