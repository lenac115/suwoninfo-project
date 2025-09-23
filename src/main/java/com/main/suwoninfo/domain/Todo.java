package com.main.suwoninfo.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@Entity
@Getter @Setter
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
}
