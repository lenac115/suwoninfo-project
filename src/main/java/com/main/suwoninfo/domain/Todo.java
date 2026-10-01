package com.main.suwoninfo.domain;

import com.main.suwoninfo.dto.TodoResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import jakarta.persistence.*;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Todo extends Time {

    @Id @Column(name = "todo_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String className;

    private String classroom;

    private String professor;

    @Enumerated(EnumType.STRING)
    private DayList dayList;

    @Max(59) @Min(00)
    private int startMinute;

    @Max(24) @Min(00)
    private int startHour;

    @Max(59) @Min(00)
    private int endMinute;

    @Max(24) @Min(00)
    private int endHour;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User users;

    public void setUser(User user) {
        this.users = user;
        user.getTodoList().add(this);
    }

    public void update(TodoResponse todoRequest) {
        this.className = todoRequest.className();
        this.classroom = todoRequest.classroom();
        this.professor = todoRequest.professor();
        this.dayList = todoRequest.dayList();
        this.startMinute = todoRequest.startMinute();
        this.startHour = todoRequest.startHour();
        this.endMinute = todoRequest.endMinute();
        this.endHour = todoRequest.endHour();
    }

    public int startTime() {
        return startHour * 60 + startMinute;
    }

    public int endTime() {
        return endHour * 60 + endMinute;
    }

    // 같은 요일에 시간이 겹치는지 확인
    public boolean overlaps(DayList day, int start, int end) {
        return dayList == day && start < endTime() && end > startTime();
    }

    public enum DayList {
        MON,
        TUE,
        WED,
        THU,
        FRI,
        SAT,
        SUN
    }
}
