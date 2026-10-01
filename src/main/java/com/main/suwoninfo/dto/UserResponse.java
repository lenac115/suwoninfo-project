package com.main.suwoninfo.dto;

import com.main.suwoninfo.domain.User;
import lombok.Builder;

@Builder
public record UserResponse(
        Long id,
        String email,
        String nickname,
        String name,
        Long studentNumber,
        User.Auth auth,
        Boolean activated
) {}