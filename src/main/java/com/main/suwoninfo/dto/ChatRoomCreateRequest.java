package com.main.suwoninfo.dto;

import jakarta.validation.constraints.NotNull;

public record ChatRoomCreateRequest(
        @NotNull Long targetUserId
) {
}
