package com.main.suwoninfo.dto;

import com.main.suwoninfo.domain.DayList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TodoDto {

    private Long id;

    private String className;

    private String classroom;

    private DayList dayList;

    private String professor;

    private int startMinute;

    private int startHour;

    private int endMinute;

    private int endHour;
}
