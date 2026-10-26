package com.school.auth.service;

import com.school.auth.config.AuthProperties;
import com.school.auth.security.AuthCookieService;
import com.school.auth.security.AuthenticatedUser;
import com.school.auth.security.JwtService;
import com.school.auth.security.UserAccountService;
import com.school.common.error.ApiException;
import com.school.common.error.ErrorCode;
import com.school.user.domain.Role;
import com.school.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService cookieService;
    private final UserAccountService userAccountService;
    private final AuthProperties authProperties;

    public AuthService(
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuthCookieService cookieService,
            UserAccountService userAccountService,
            AuthProperties authProperties
    ) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.cookieService = cookieService;
        this.userAccountService = userAccountService;
        this.authProperties = authProperties;
    }

    public AuthenticatedUser startSession(User user, HttpServletResponse response) {
        AuthenticatedUser principal = userAccountService.loadActiveUser(user.getId(), null);
        if (principal == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCOUNT_NOT_REGISTERED,
                    "This account is not active. Contact the school office.");
        }
        writeSessionCookies(principal, response);
        return principal;
    }

    public AuthenticatedUser refreshSession(HttpServletRequest request, HttpServletResponse response) {
        String presentedToken = cookieService.read(request, AuthCookieService.REFRESH_TOKEN_COOKIE)
                .orElseThrow(() -> sessionExpired(response));

        Optional<RefreshTokenService.Rotated> rotated = refreshTokenService.rotate(presentedToken);
        if (rotated.isEmpty()) {
            throw sessionExpired(response);
        }

        AuthenticatedUser principal = userAccountService.loadActiveUser(
                rotated.get().userId(), rotated.get().activeRole());
        if (principal == null) {
            refreshTokenService.revokeAllForUser(rotated.get().userId());
            throw sessionExpired(response);
        }

        writeSessionCookies(principal, response);
        return principal;
    }

    public void endSession(HttpServletRequest request, HttpServletResponse response) {
        cookieService.read(request, AuthCookieService.REFRESH_TOKEN_COOKIE)
                .ifPresent(refreshTokenService::revoke);

        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearAccessToken().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearRefreshToken().toString());
    }

    public AuthenticatedUser switchActiveRole(AuthenticatedUser current, Role requestedRole, HttpServletResponse response) {
        if (!current.roles().contains(requestedRole)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "Your account does not hold the requested role.");
        }

        AuthenticatedUser updated = userAccountService.loadActiveUser(current.id(), requestedRole);
        if (updated == null) {
            throw sessionExpired(response);
        }

        writeSessionCookies(updated, response);
        return updated;
    }

    private void writeSessionCookies(AuthenticatedUser principal, HttpServletResponse response) {
        String accessToken = jwtService.issueAccessToken(principal.id(), principal.activeRole());
        RefreshTokenService.IssuedToken refreshToken =
                refreshTokenService.issue(principal.id(), principal.activeRole());

        response.addHeader(HttpHeaders.SET_COOKIE, cookieService
                .accessToken(accessToken, authProperties.jwt().accessTokenTtl())
                .toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService
                .refreshToken(refreshToken.rawToken(), authProperties.jwt().refreshTokenTtl())
                .toString());
    }

    private ApiException sessionExpired(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearAccessToken().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clearRefreshToken().toString());
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                "Your session has expired. Please sign in again.");
    }
}
