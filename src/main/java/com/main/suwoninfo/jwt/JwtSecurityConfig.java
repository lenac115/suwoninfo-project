package com.main.suwoninfo.jwt;

import com.main.suwoninfo.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class JwtSecurityConfig extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {
    private final JwtTokenProvider tokenProvider;
    private final RedisUtils redisUtils;
    private final RestClient restClient;


    @Override
    public void configure(HttpSecurity http) {
        JwtFilter customFilter = new JwtFilter(tokenProvider, redisUtils, restClient);
        http.addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class);
    }
}