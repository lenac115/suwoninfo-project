package com.main.suwoninfo.form;

import lombok.Data;


@Data
public class UserForm {

    private String email;
    private String password;
    private String name;
    private String nickname;
    private Long studentNumber;
}
