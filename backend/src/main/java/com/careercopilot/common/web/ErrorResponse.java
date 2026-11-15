package com.careercopilot.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** The one error shape every endpoint returns (CODE_STYLE: a status code and a clear message). */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(int status, String message, Map<String, String> fieldErrors) {

    public ErrorResponse(int status, String message) {
        this(status, message, Map.of());
    }
}
