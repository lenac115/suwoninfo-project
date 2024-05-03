package com.main.suwoninfo.form;

import lombok.Data;

@Data
public class UserUpdateForm {

    private String password;
    private Long studentNumber;
    private String nickname;
}
