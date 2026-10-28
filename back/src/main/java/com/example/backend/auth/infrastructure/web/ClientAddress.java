package com.example.backend.auth.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The visitor's address. The API is only reachable through the frontend's server (it is not published), which
 * sets X-Real-IP from Cloudflare's CF-Connecting-IP, so that header can be trusted here.
 */
final class ClientAddress {

    private ClientAddress() {
    }

    static String of(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp.trim();
    }
}
