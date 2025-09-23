package com.main.suwoninfo.form;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class UserForm {

    private String email;
    private String password;
    private String name;
    private String nickname;
    private Long studentNumber;
}
