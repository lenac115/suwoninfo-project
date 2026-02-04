package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> createUser(email, user))
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
    }

    private org.springframework.security.core.userdetails.User createUser(String email, User user) {
        if (!user.isActivated()) {
            throw new CustomException(UserErrorCode.NOT_ACTIVATED_ACCOUNT);
        }

        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();

        if(user.getAuth() == User.Auth.ADMIN) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else if(user.getAuth() == User.Auth.USER) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return new org.springframework.security.core.userdetails.User(user.getEmail(),
                user.getPassword(),
                grantedAuthorities);
    }
}
