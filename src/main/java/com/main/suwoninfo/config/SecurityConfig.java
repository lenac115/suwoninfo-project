package com.main.suwoninfo.config;

import com.main.suwoninfo.jwt.JwtAuthenticationEntryPoint;
import com.main.suwoninfo.jwt.JwtSecurityConfig;
import com.main.suwoninfo.jwt.JwtTokenProvider;
import com.main.suwoninfo.service.CustomUserDetailService;
import com.main.suwoninfo.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

/**
 * 비밀번호 인코더와 권한 설정에 대한 Config
 */
@EnableWebSecurity
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtils redisUtils;
    private final RestClient restClient;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers(HttpMethod.POST, "/users/new").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/reissue").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/post/view/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/post/free/list").permitAll()
                        .requestMatchers(HttpMethod.GET, "/post/trade/list").permitAll()
                        .requestMatchers(HttpMethod.GET, "/post/trade/list/origin").permitAll()
                        .requestMatchers(HttpMethod.GET, "/post/trade/search?**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/post/free/search?**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/post/trade/new").permitAll()
                        .requestMatchers(HttpMethod.POST, "/post/free/new").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico", "/error").permitAll() //정적파일
                        .anyRequest().authenticated() // 인증 되지 않는 사용자일 경우 모든 요청을 Spring Security 에서 가로챔(설정한 url을 제외한 url은 이 설정을 적용할 예정)
                )
                .formLogin(
                        AbstractHttpConfigurer::disable
                )
                .logout(
                        AbstractHttpConfigurer::disable
                )
                .exceptionHandling((exceptionHandling) -> exceptionHandling
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedPage("/users/accessDenied")) // 권한에 따른 접근 불가 페이지 설정
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .with(new JwtSecurityConfig(tokenProvider, redisUtils, restClient), Customizer.withDefaults());
                /*.cors((cors) -> cors
                        .configurationSource(corsConfigurationSource()));*/

        return httpSecurity.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }
}