package com.main.suwoninfo.config;

import com.main.suwoninfo.jwt.JwtAccessDeniedHandler;
import com.main.suwoninfo.jwt.JwtAuthenticationEntryPoint;
import com.main.suwoninfo.jwt.JwtSecurityConfig;
import com.main.suwoninfo.jwt.JwtTokenProvider;
import com.main.suwoninfo.service.UserService;
import com.main.suwoninfo.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더와 권한 설정에 대한 Config
 */
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final JwtTokenProvider tokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final RedisUtils redisUtils;
    private final UserService userService;

    //권한 설정
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()

                .exceptionHandling() // 권한 관련 Exception 핸들링
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)

                .and() // 무연결 설정
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                .and() // 권한에 대한 설정
                .authorizeRequests()
                .antMatchers("/users/info").authenticated()
                .antMatchers("/users/update").authenticated()
                .antMatchers("/post/delete/**").authenticated()
                .antMatchers("/post/update/**").authenticated()
                .antMatchers("/post/free/new").authenticated()
                .antMatchers("/post/trade/new").authenticated()
                .antMatchers("/post/upload").authenticated()
                .antMatchers("/comment/notreply").authenticated()
                .antMatchers("/comment/reply").authenticated()
                .antMatchers("/comment/update/**").authenticated()
                .antMatchers("/comment/delete/**").authenticated()
                .antMatchers("/photo/delete/**").authenticated()
                .antMatchers("/photo/upload/**").authenticated()
                .antMatchers("/todo/view").authenticated()
                .antMatchers("/todo/new").authenticated()
                .antMatchers("/todo/view").authenticated()
                .antMatchers("/todo/update").authenticated()
                .antMatchers("/todo/delete/**").authenticated()

                // 위 url 외 모든 url 권한 허용
                .anyRequest().permitAll()

                .and()
                .apply(new JwtSecurityConfig(tokenProvider, redisUtils, userService));
    }
}
