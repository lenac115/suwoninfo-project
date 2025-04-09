package com.main.suwoninfo.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TokenValidationResult {

    private Boolean valid;
    private TokenErrorReason tokenErrorReason;

    public enum TokenErrorReason {
        VALID,
        IN_BLACKLIST,
        INVALID,
        EXPIRED,
        UNSUPPORTED,
        JWT_EMPTY
    }
}
