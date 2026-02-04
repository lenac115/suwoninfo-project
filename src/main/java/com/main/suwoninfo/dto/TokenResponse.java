package com.main.suwoninfo.dto;

import lombok.*;

@Builder
public record TokenResponse(
        String accessToken,
        String tokenType,
        String refreshToken
) {}
