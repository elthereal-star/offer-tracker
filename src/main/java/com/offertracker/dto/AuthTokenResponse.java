package com.offertracker.dto;
public record AuthTokenResponse(String tokenType, String accessToken, long expiresInSeconds) {}
