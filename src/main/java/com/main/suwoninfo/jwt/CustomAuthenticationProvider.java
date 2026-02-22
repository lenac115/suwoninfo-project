package com.main.suwoninfo.jwt;

import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.service.CustomUserDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final CustomUserDetailService customUserDetailService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = authentication.getCredentials().toString();

        CustomUserDetails userDetails = customUserDetailService.loadUserByUsername(email);
        User user = userDetails.getUser();
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            throw new CustomException(UserErrorCode.NOT_CORRECT_PASSWORD);
        }


        return new UsernamePasswordAuthenticationToken(user, null, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
