package com.main.suwoninfo.jwt;

import com.main.suwoninfo.form.TokenResponse;
import com.main.suwoninfo.utils.RedisUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpServletRequestWrapper requestWrapper = new CachingBodyRequestWrapper(request);
        String token = resolveToken(requestWrapper);

        try {
            if (token != null) {
                if (tokenProvider.validateToken(token).getValid()) {
                    // 유효한 토큰 처리
                    Authentication authentication = tokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    System.out.println("Authentication set: " + authentication.getName());
                } else if (tokenProvider.isTokenExpired(token)) {
                    // Access Token 만료 시 처리
                    HttpServletRequest reissued = handleExpiredToken(requestWrapper, response, token);
                    if (reissued != null) filterChain.doFilter(reissued, response);
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

    private HttpServletRequest handleExpiredToken(HttpServletRequestWrapper request, HttpServletResponse response, String expiredToken)
            throws IOException {
        Authentication authentication = tokenProvider.getAuthentication(expiredToken);

        if (authentication == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
            return null;
        }

        String email = authentication.getName();
        String refreshToken = (String) redisUtils.get("RT:" + email);

        if (refreshToken != null) {
            refreshToken = refreshToken.replace("\"", "");
            if (!tokenProvider.validateToken(refreshToken).getValid()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
                return null;
            }
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
            return null;
        }

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(expiredToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();

        try {
            ResponseEntity<TokenResponse> tokenEntity = restClient
                    .post()
                    .uri("http://localhost:8081/users/reissue")
                    .header("Authorization", "Bearer " + expiredToken)
                    .body(tokenResponse)
                    .retrieve()
                    .toEntity(TokenResponse.class);
            Map <String, String> mutatedRequestHeader = Map.of("Authorization", "Bearer " + tokenEntity.getBody().getAccessToken());
            return new CachingBodyRequestWrapper(request, mutatedRequestHeader);

        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "token generation failed");
            System.out.println("에러" + e.getMessage());
            return null;
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
