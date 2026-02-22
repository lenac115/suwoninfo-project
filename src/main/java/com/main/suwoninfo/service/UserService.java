package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.TokenResponse;
import com.main.suwoninfo.dto.UserRequest;
import com.main.suwoninfo.dto.UserResponse;
import com.main.suwoninfo.exception.*;
import com.main.suwoninfo.jwt.CustomAuthenticationProvider;
import com.main.suwoninfo.jwt.JwtTokenProvider;
import com.main.suwoninfo.repository.UserRepository;
import com.main.suwoninfo.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import static com.main.suwoninfo.utils.ToUtils.toUserResponse;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final CustomAuthenticationProvider authenticationProvider;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtils redisUtils;

    @Transactional
    public UserResponse join(UserRequest form) {
        System.out.println("조인");
        User user = User.builder()
                .email(form.email())
                .studentNumber(form.studentNumber())
                .name(form.name())
                .nickname(form.nickname())
                .password(passwordEncoder.encode(form.password()))
                .auth(User.Auth.USER)
                .activated(true)
                .build();

        validateDuplicateEmail(user.getEmail());
        validateDuplicateNick(user.getNickname());

        userRepository.save(user);
        System.out.println("가입완료");
        return toUserResponse(user);
    }

    //이메일 중복 검증
    private void validateDuplicateEmail(String email) {
        Optional<User> validEmail = userRepository.findByEmail(email);
        if (validEmail.isPresent()) {
            if (validEmail.get().isActivated())
                throw new CustomException(UserErrorCode.VALIDATED_EMAIL_ERROR);
        }
    }

    private void validateDuplicateNick(String nick) {
        Optional<User> validNick = userRepository.findByNick(nick);
        if (validNick.isPresent()) {
            if (validNick.get().isActivated())
                throw new CustomException(UserErrorCode.VALIDATED_NICK_ERROR);
        }
    }

    //primary key 값으로 유저 검색
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));
    }

    //email 기반으로 유저 검색
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
    }

    //dirty checking을 이용한 update 메소드
    @Transactional
    public UserResponse update(UserRequest form, UserDetails userDetails) {
        User findUser = userRepository.findByEmail(form.email()).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));

        if(!Objects.equals(findUser.getEmail(), userDetails.getUsername())){
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);
        }
        findUser.update(form);

        return toUserResponse(findUser.update(form));
    }

    //로그인 메소드
    @Transactional
    public TokenResponse logIn(String email, String password) {

        // 받아온 유저네임과 패스워드를 이용해 UsernamePasswordAuthenticationToken 객체 생성
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(email, password);
        // authenticationToken 객체를 통해 Authentication 객체 생성
        // 이 과정에서 CustomUserDetailsService 에서 재정의한 loadUserByUsername 메서드 호출
        Authentication authentication = authenticationProvider.authenticate(authenticationToken);
        // 인증 정보를 기준으로 jwt access 토큰 생성
        TokenResponse tokenResponse = tokenProvider.generateToken(authentication);

        redisUtils.set("RT:" + email, tokenResponse.refreshToken(), Duration.ofMinutes(1440));

        return tokenResponse;
    }

    public UserResponse getUserInfo(String email) {

        User user = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        return toUserResponse(user);
    }

    @Transactional
    public TokenResponse reissue(String requestAccessToken, String requestRefreshToken) {
        if (!tokenProvider.validateToken(requestRefreshToken).getValid()) {
            throw new CustomException(UserErrorCode.INVALID_USER_TOKEN);
        }

        Authentication authentication = tokenProvider.getAuthentication(requestAccessToken);

        Optional<String> refreshTokenOptional =
                Optional.ofNullable((String) redisUtils.get("RT:" + authentication.getName()));
        String refreshToken = refreshTokenOptional.orElseThrow(() -> new CustomException(UserErrorCode.REFRESH_TOKEN_NOT_FOUND_IN_REDIS));

        if (!tokenProvider.validateToken(refreshToken).getValid()) {
            redisUtils.delete("RT:" + authentication.getName());
            throw new CustomException(UserErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        if (!requestRefreshToken.equals(refreshToken)) {
            throw new CustomException(UserErrorCode.TOKEN_MISMATCH_BETWEEN_CLIENT_AND_SERVER);
        }

        TokenResponse tokenResponse = tokenProvider.generateToken(authentication);
        redisUtils.delete(tokenResponse.refreshToken());
        redisUtils.set("RT:" + authentication.getName(), tokenResponse.refreshToken(), Duration.ofMinutes(1440));

        return tokenResponse;
    }

    public void logout(String username, String accessToken) {

        redisUtils.setBlackList(accessToken.substring(7), "accessToken", Duration.ofMinutes(120));
        redisUtils.delete("RT:" + username);
    }

    @Transactional
    public void delete(String email, UserDetails userDetails) {
        User findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));

        if(!Objects.equals(findUser.getEmail(), userDetails.getUsername())){
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);
        }

        findUser.update(UserRequest.builder()
                .name("DELETED_USER")
                .email("DELETED_USER")
                .nickname("DELETED_USER")
                .build());

        findUser.setActivated(false);
    }
}