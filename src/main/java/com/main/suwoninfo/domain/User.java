package com.main.suwoninfo.domain;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User extends Time {

    // primary key 값
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id @Column(name = "user_id")
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
    
    private boolean activated;

    @OneToMany(mappedBy = "user")
    private List<Post> postList = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Comment> commentList = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<UserAuthority> userAuthorities = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Todo> todoList = new ArrayList<>();
}
