package com.careercopilot.common.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Writes the ErrorResponse shape from servlet filters, which run before Spring MVC and so cannot
 * use the global exception handler. Messages are fixed strings, so no JSON escaping is needed.
 */
public final class JsonErrorWriter {

    private JsonErrorWriter() {}

    public static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}");
    }
}
