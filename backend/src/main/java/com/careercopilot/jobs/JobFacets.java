package com.careercopilot.jobs;

import java.util.List;

public record JobFacets(
        List<FacetValue> categories,
        List<FacetValue> countries,
        List<FacetValue> states,
        List<FacetValue> cities) {
}
