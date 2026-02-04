package com.main.suwoninfo.jwt;

import com.main.suwoninfo.dto.TokenResponse;
import com.main.suwoninfo.utils.RedisUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final RedisUtils redisUtils;
    private final RestClient restClient;

    private boolean isReissue(HttpServletRequest req) {
        return "POST".equals(req.getMethod()) && req.getRequestURI().endsWith("/users/reissue");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // request는 한번 읽은 뒤엔 읽는 것이 불가능하므로 래퍼 클래스로 옮겨서 읽음
        HttpServletRequestWrapper requestWrapper = new CachingBodyRequestWrapper(request);
        String token = resolveToken(requestWrapper);

        if (isReissue(request)) {
            filterChain.doFilter(requestWrapper, response);
            return;
        }

        try {
            if (token != null) {
                if (tokenProvider.validateToken(token).getValid()) {
                    // 유효한 토큰 처리
                    Authentication authentication = tokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    System.out.println("Authentication set: " + authentication.getName());
                } else if (tokenProvider.isTokenExpired(token)) {
                    // Access Token 만료 시 처리
                    handleExpiredToken(requestWrapper, response, token, filterChain);
                    return;
                } else {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
                    return;
                }
            }
            // 필터 체인을 계속 진행
            filterChain.doFilter(requestWrapper, response);

        } catch (Exception e) {
            // 예외 발생 시 처리
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Token processing failed");
        }
    }

    private void handleExpiredToken(HttpServletRequestWrapper request, HttpServletResponse response, String expiredToken, FilterChain filterChain)
            throws IOException {
        Authentication authentication = tokenProvider.getAuthentication(expiredToken);

        if (authentication == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
            return;
        }

        String email = authentication.getName();
        String refreshToken = (String) redisUtils.get("RT:" + email);

        if (refreshToken != null) {
            refreshToken = refreshToken.replace("\"", "");
            if (!tokenProvider.validateToken(refreshToken).getValid()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
                return;
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
            return;
        }
        TokenResponse tokenResponse = TokenResponse.builder()
                .tokenType("Bearer")
                .refreshToken(refreshToken)
                .accessToken(expiredToken)
                .build();


        // http 탈취 후 new-access-token이란 이름으로 헤더 주입
        try {
            TokenResponse tokenEntity = restClient
                    .post()
                    .uri("http://localhost:8081/users/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(tokenResponse)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TokenResponse.class);
            Map<String, String> mutatedRequestHeader = Map.of("Authorization", "Bearer " + tokenEntity.accessToken());
            response.setHeader("New-Access-Token", tokenEntity.accessToken());
            filterChain.doFilter(new CachingBodyRequestWrapper(request, mutatedRequestHeader), response);

        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "token generation failed");
            System.out.println("에러 : " + e.getMessage());
        }
    }

    // Request Header 에서 토큰 정보 추출
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
