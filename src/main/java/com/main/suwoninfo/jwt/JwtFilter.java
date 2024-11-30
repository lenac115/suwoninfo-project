package com.main.suwoninfo.jwt;

import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.form.TokenResponse;
import com.main.suwoninfo.service.UserService;
import com.main.suwoninfo.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final RedisUtils redisUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Request Header 에서 JWT 토큰 추출
        String token = resolveToken(request);

        // validateToken 으로 토큰 유효성 검사
        if (token != null) {
            if (tokenProvider.validateToken(token)) {
                // Access Token이 유효한 경우
                Authentication authentication = tokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (tokenProvider.isTokenExpired(token)) {
                // Access Token이 만료된 경우
                handleExpiredToken(request, response, token);
                return; // 요청 처리를 중단하고 새 토큰을 반환
            } else {
                // 유효하지 않은 토큰인 경우
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("유효하지 않은 토큰입니다.");
                return;
            }
        }

        filterChain.doFilter(request, response); // 필터 체인 계속 진행
    }

    private void handleExpiredToken(HttpServletRequest request, HttpServletResponse response, String expiredToken)
            throws IOException {
        // Access Token에서 사용자 정보 추출
        Authentication authentication = tokenProvider.getAuthentication(expiredToken);
        if (authentication == null) {
            // 사용자 정보를 가져올 수 없으므로 에러 반환
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("만료된 토큰에서 사용자 정보를 추출할 수 없습니다.");
            return;
        }
        String email = authentication.getName(); // 이메일 또는 사용자 고유 ID 추출

        // Redis에서 Refresh Token 조회
        String refreshToken = (String) redisUtils.get("RT:" + email);
        if (refreshToken == null || !tokenProvider.validateToken(refreshToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Refresh Token이 유효하지 않거나 Redis에서 누락되었습니다.");
            return;
        }

        try {
            // Reissue 메서드 호출로 새로운 토큰 발급
            TokenResponse newTokens = userService.reissue(expiredToken, refreshToken);

            // 새 토큰을 응답 헤더에 추가
            response.setHeader("Authorization", "Bearer " + newTokens.getAccessToken());
            response.setHeader("Refresh-Token", newTokens.getRefreshToken());

            // Redis의 기존 Refresh Token 갱신
            redisUtils.delete("RT:" + email); // 기존 토큰 삭제
            redisUtils.set("RT:" + email, newTokens.getRefreshToken(), 4320); // 새로운 토큰 저장

        } catch (CustomException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("토큰 재발급에 실패했습니다: " + e.getMessage());
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