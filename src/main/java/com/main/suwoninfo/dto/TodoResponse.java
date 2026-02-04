package com.main.suwoninfo.dto;

import com.main.suwoninfo.domain.Todo;
import lombok.Builder;

@Builder
public record TodoResponse(
        Long id,
        String className,
        String classroom,
        Todo.DayList dayList,
        String professor,
        int startMinute,
        int startHour,
        int endMinute,
        int endHour,
        UserResponse userResponse
) {}