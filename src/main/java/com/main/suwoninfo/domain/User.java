package com.main.suwoninfo.domain;

import com.main.suwoninfo.dto.UserRequest;
import com.main.suwoninfo.dto.UserResponse;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user", indexes = {
        @Index(name = "idx_user_email", columnList = "email, activated")
})
@Builder
public class User extends Time {

    // primary key 값
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "user_id")
    private Long id;

    // 로그인 할 경우 사용 될 email
    @NotNull
    private String email;

    // password로 사용될 문자열
    @NotNull
    private String password;

    //이름
    @NotNull
    private String name;

    //닉네임
    @NotNull
    private String nickname;

    //학번
    private Long studentNumber;

    @Enumerated(EnumType.STRING)
    private Auth auth;

    public void setActivated(boolean b) {
        this.activated = b;
    }

    public User update(UserRequest form) {
        this.password = form.password();
        this.name = form.name();
        this.nickname = form.nickname();
        return this;
    }

    public enum Auth {
        USER,
        ADMIN
    }

    @Column(columnDefinition = "boolean default true")
    private boolean activated;

    @OneToMany(mappedBy = "user")
    private List<Post> postList = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Comment> commentList = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Todo> todoList = new ArrayList<>();
}
