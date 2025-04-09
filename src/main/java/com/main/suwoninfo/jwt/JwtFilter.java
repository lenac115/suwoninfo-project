package com.main.suwoninfo.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        String token = resolveToken(request);

        try {
            if (token != null) {
                if (tokenProvider.validateToken(token).getValid()) {
                    // 유효한 토큰 처리
                    Authentication authentication = tokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    System.out.println("Authentication set: " + authentication.getName());
                } else if (tokenProvider.isTokenExpired(token)) {
                    // Access Token 만료 시 처리
                    Boolean reissued = handleExpiredToken(request, response, token);
                    if (!reissued) return;
                } else {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
                    return;
                }
            }

            // 필터 체인을 계속 진행
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // 예외 발생 시 처리
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Token processing failed");
        }
    }

    private Boolean handleExpiredToken(HttpServletRequest request, HttpServletResponse response, String expiredToken)
            throws IOException {
        Authentication authentication = tokenProvider.getAuthentication(expiredToken);

        if (authentication == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
            return false;
        }

        String email = authentication.getName();
        String refreshToken = (String) redisUtils.get("RT:" + email);

        if (refreshToken != null) {
            refreshToken = refreshToken.replace("\"", "");
            if (!tokenProvider.validateToken(refreshToken).getValid()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
                return false;
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
            return false;
        }

        try {
            // 새로운 Access Token 발급
            TokenResponse newTokens = userService.reissue(expiredToken, refreshToken);

            response.setHeader("Authorization", "Bearer " + newTokens.getAccessToken());
            redisUtils.set("RT:" + email, newTokens.getRefreshToken(), 4320); // Redis에 Refresh Token 갱신

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            String json = new ObjectMapper().writeValueAsString(newTokens);
            response.getWriter().write(json);

            // SecurityContextHolder에 새로운 인증 설정
            Authentication newAuthentication = tokenProvider.getAuthentication(newTokens.getAccessToken());
            SecurityContextHolder.getContext().setAuthentication(newAuthentication);
            return true;

        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "token generation failed");
            System.out.println("에러" + e.getMessage());
            return false;
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
