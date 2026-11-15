package com.careercopilot.jobs.scraper;

/** One structured location parsed from a board's free-text location. Any field may be null. */
public record ParsedLocation(String city, String state, String country) {}
