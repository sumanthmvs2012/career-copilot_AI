package com.careercopilot.matches;

/** A job skill the resume lacks, paired with a related skill the resume has. */
public record NearMatch(String jobSkill, String resumeSkill) {
}
