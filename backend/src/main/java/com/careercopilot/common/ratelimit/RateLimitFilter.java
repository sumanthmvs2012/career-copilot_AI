package com.careercopilot.common.ratelimit;

import com.careercopilot.common.web.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** SECURITY.md: rate limiting applies to login and sign up. Not a @Component, so it runs only in the security chain. */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Set<String> LIMITED_PATHS = Set.of("/auth/login", "/auth/signup");

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(path(request));
    }

    // Request URI minus context path: getServletPath() depends on how the servlet is mapped.
    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = path(request);
        String key = request.getRemoteAddr() + "|" + path;
        if (!rateLimiter.tryAcquire(key)) {
            log.warn("Rate limit hit path={}", path);
            JsonErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please try again shortly.");
            return;
        }
        chain.doFilter(request, response);
    }
}
