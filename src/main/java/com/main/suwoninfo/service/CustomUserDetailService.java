package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.jwt.CustomUserDetails;
import com.main.suwoninfo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public CustomUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(this::createUser)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
    }

    private CustomUserDetails createUser(User user) {
        if (!user.isActivated()) {
            throw new CustomException(UserErrorCode.NOT_ACTIVATED_ACCOUNT);
        }
        return new CustomUserDetails(user);
    }
}
