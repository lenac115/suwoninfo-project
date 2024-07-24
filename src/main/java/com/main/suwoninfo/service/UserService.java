package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Authority;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.domain.UserAuthority;
import com.main.suwoninfo.form.TokenResponse;
import com.main.suwoninfo.dto.UserDto;
import com.main.suwoninfo.form.UserWithAuthorityForm;
import com.main.suwoninfo.exception.*;
import com.main.suwoninfo.jwt.JwtTokenProvider;
import com.main.suwoninfo.repository.UserRepository;
import com.main.suwoninfo.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisUtils redisUtils;

    @Transactional
    public User join(UserDto userDto) {
        User user = dtoToUser(userDto);
        validateDuplicateEmail(user.getEmail());
        validateDuplicateNick(user.getNickname());

        userRepository.save(user);

        Authority authority = Authority.builder()
                .name("ROLE_USER")
                .build();

        UserAuthority userAuthority = new UserAuthority();
        userAuthority.setUser(user);
        userAuthority.setAuthority(authority);
        userRepository.authSave(userAuthority);
        user.getUserAuthorities().add(userAuthority);

        return user;
    }

    //이메일 중복 검증
    private void validateDuplicateEmail(String email) {
        Optional<User> validEmail = userRepository.findByEmail(email);
        if(validEmail.isPresent()) {
            if(validEmail.get().isActivated())
                throw new CustomException(UserErrorCode.VALIDATED_EMAIL_ERROR);
        }
    }

    private void validateDuplicateNick(String nick) {
        Optional<User> validNick = userRepository.findByNick(nick);
        if(validNick.isPresent()) {
            if(validNick.get().isActivated())
                throw new CustomException(UserErrorCode.VALIDATED_NICK_ERROR);
        }
    }

    private User dtoToUser(UserDto userDto) {

        return User.builder()
                .email(userDto.getEmail())
                .name(userDto.getName())
                .nickname(userDto.getNickname())
                .password(passwordEncoder.encode(userDto.getPassword()))
                .studentNumber(userDto.getStudentNumber())
                .userAuthorities(new ArrayList<>())
                .activated(true)
                .build();
    }

    //전체 유저 리스트
    public List<User> findAll() {
        return userRepository.findAll();
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
    public UserDto update(Long id, String password, String nickName, Long studentNum) {
        User findUser = userRepository.findById(id).orElseThrow(() -> new CustomException(UserErrorCode.NOT_AVAILABLE_EMAIL));

        if(password != null) findUser.setPassword(passwordEncoder.encode(password));
        if(nickName != null) findUser.setNickname(nickName);
        if(studentNum != null) findUser.setStudentNumber(studentNum);

        return UserDto.builder()
                .studentNumber(findUser.getStudentNumber())
                .name(findUser.getName())
                .nickname(findUser.getNickname())
                .email(findUser.getEmail())
                .password(findUser.getPassword())
                .build();
    }

    //로그인 메소드
    public TokenResponse logIn(String email, String password) {

        User findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));

        if(!findUser.isActivated())
            throw new CustomException(UserErrorCode.NOT_ACTIVATED_ACCOUNT);

        if(!passwordEncoder.matches(password, findUser.getPassword()))
            throw new CustomException(UserErrorCode.NOT_CORRECT_PASSWORD);

        // 받아온 유저네임과 패스워드를 이용해 UsernamePasswordAuthenticationToken 객체 생성
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(email, password);
        // authenticationToken 객체를 통해 Authentication 객체 생성
        // 이 과정에서 CustomUserDetailsService 에서 우리가 재정의한 loadUserByUsername 메서드 호출
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        // 인증 정보를 기준으로 jwt access 토큰 생성
        TokenResponse tokenResponse = tokenProvider.generateToken(authentication);

        redisUtils.set("RT:"+ email, tokenResponse.getRefreshToken(), 1440);

        return tokenResponse;
    }

    public UserWithAuthorityForm getUserInfo(String email) {

        User user = userRepository.findByEmail(email).orElseThrow(() ->  new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        List<String> userAuthority = userRepository.findAuthById(email);

        return UserWithAuthorityForm.builder()
                .name(user.getName())
                .studentNumber(user.getStudentNumber().toString())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .authority(userAuthority)
                .build();
    }

    public TokenResponse reissue(String requestAccessToken, String requestRefreshToken) {
        if (!tokenProvider.validateToken(requestRefreshToken)) {
            throw new CustomException(UserErrorCode.INVALID_USER_TOKEN);
        }
        Authentication authentication = tokenProvider.getAuthentication(requestAccessToken);

        String refreshToken = (String) redisUtils.get("RT:" + authentication.getName());
        if(!requestRefreshToken.equals(refreshToken)) {
            throw new CustomException(UserErrorCode.INVALID_USER_TOKEN);
        }

        TokenResponse tokenResponse = tokenProvider.generateToken(authentication);

        redisUtils.set("RT:" + authentication.getName(), tokenResponse.getRefreshToken(),
                1440);

        return tokenResponse;
    }

    public void logout(String accessToken, String refreshToken) {
        redisUtils.setBlackList(accessToken, "accessToken", 1800);
        redisUtils.setBlackList(refreshToken, "refreshToken", 60400);
    }

    @Transactional
    public void delete(String email) {
        User findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EQUAL_EMAIL));
        findUser.setName("DELETED_USER");
        findUser.setEmail("DELETED_USER");
        findUser.setNickname("DELETED_USER");
        userRepository.delete(findUser);
    }
}