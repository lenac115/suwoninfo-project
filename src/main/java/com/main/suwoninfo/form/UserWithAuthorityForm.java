package com.main.suwoninfo.form;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWithAuthorityForm {
    private String email;
    private String name;
    private String nickname;
    private String studentNumber;
    private List<String> authority;
}
