package com.main.suwoninfo.dto;

import com.main.suwoninfo.domain.User;
import lombok.Builder;

@Builder
public record UserRequest(
        String email,
        String password,
        String nickname,
        String name,
        Long studentNumber,
        User.Auth auth
) {}