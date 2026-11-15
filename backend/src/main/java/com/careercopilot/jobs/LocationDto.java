package com.careercopilot.jobs;

/** One structured location of a job; any field may be null. */
public record LocationDto(String city, String state, String country) {

    public static LocationDto from(JobLocation location) {
        return new LocationDto(location.getCity(), location.getState(), location.getCountry());
    }
}
