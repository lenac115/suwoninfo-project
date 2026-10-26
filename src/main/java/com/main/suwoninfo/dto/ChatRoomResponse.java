package com.main.suwoninfo.dto;

import lombok.Builder;

@Builder
public record ChatRoomResponse(
        Long roomId,
        Long opponentId,
        String opponentNickname
) {
}
