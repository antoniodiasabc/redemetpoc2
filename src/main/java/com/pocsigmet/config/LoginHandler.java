package com.pocsigmet.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    public LoginHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                        Authentication auth) throws IOException {
        loginAttemptService.loginSucceeded(getIp(req));
        res.sendRedirect("/");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest req, HttpServletResponse res,
                                        AuthenticationException ex) throws IOException {
        loginAttemptService.loginFailed(getIp(req));
        res.sendRedirect("/login?error");
    }

    private String getIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip.trim();
        ip = req.getHeader("X-Forwarded-For");
        return (ip != null && !ip.isBlank()) ? ip.split(",")[0].trim() : req.getRemoteAddr();
    }
}
