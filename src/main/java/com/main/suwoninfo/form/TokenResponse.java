package com.main.suwoninfo.form;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponse {

    private String accessToken;
    private String tokenType;
    private String refreshToken;
}
