package com.school.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.common.error.ApiError;
import com.school.common.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Per-client-address rate limiting for the external sign-in entry point
 * ({@code /oauth2/authorization/**}). Phone OTP request/verify are limited inside
 * {@code OtpService} (per phone and per address); this closes the remaining login surface.
 *
 * <p>The filter runs before Spring MVC, so it renders the {@code 429} error body itself rather than
 * relying on the controller advice.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final int oauthStartsPerIp;
    private final Duration window;

    public AuthRateLimitFilter(
            RateLimiter rateLimiter,
            ObjectMapper objectMapper,
            @Value("${app.auth.rate-limit.oauth-starts-per-ip:30}") int oauthStartsPerIp,
            @Value("${app.auth.rate-limit.window:PT15M}") Duration window
    ) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.oauthStartsPerIp = oauthStartsPerIp;
        this.window = window;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/oauth2/authorization/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            rateLimiter.check("oauth:start:ip:" + clientAddress(request), oauthStartsPerIp, window);
        } catch (ApiException exception) {
            writeError(request, response, exception);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, ApiException exception)
            throws IOException {
        ApiError body = ApiError.of(exception.getStatus(), exception.getCode(), exception.getMessage(), request, List.of());
        response.setStatus(exception.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static String clientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
