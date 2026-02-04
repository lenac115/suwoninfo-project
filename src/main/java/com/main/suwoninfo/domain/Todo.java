package com.main.suwoninfo.domain;

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
    private User user;

    public void setUser(User user) {
        this.user = user;
        user.getTodoList().add(this);
    }

    public void update(Todo todoRequest) {
        this.className = todoRequest.getClassName();
        this.classroom = todoRequest.getClassroom();
        this.professor = todoRequest.getProfessor();
        this.dayList = todoRequest.getDayList();
        this.startMinute = todoRequest.getStartMinute();
        this.startHour = todoRequest.getStartHour();
        this.endMinute = todoRequest.getEndMinute();
        this.endHour = todoRequest.getEndHour();
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
