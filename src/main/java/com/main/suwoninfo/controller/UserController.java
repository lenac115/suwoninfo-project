package com.main.suwoninfo.controller;

import com.google.gson.Gson;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.*;
import com.main.suwoninfo.form.*;
import com.main.suwoninfo.service.UserService;
import com.main.suwoninfo.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 유저 관련 컨트롤러
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final Gson gson;

    //유저 생성
    @PostMapping("/new")
    public ResponseEntity<String> createUser(@RequestBody String receivedForm) {

        if(CommonUtils.isEmpty(receivedForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        //UserForm이란 dto의 형식으로 보낸 jsonString을 객체화
        UserForm userForm = gson.fromJson(receivedForm, UserForm.class);

        UserDto userDto = UserDto.builder()
                .email(userForm.getEmail())
                .name(userForm.getName())
                .nickname(userForm.getNickname())
                .password(userForm.getPassword())
                .studentNumber(userForm.getStudentNumber())
                .build();

        //유저 정보를 dto화 시켜서 join 실행
        User createdUser = userService.join(userDto);

        //가입된 유저정보를 dto화
        UserDto createdUserDto = UserDto.builder()
                .name(createdUser.getName())
                .password(createdUser.getPassword())
                .email(createdUser.getEmail())
                .studentNumber(createdUser.getStudentNumber())
                .nickname(createdUser.getNickname())
                .build();

        String findJson = gson.toJson(createdUserDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(findJson);
    }

    //유저 로그인
    @PostMapping("/login")
    public ResponseEntity<String> logIn(@RequestBody String receivedLoginForm) {

        if(CommonUtils.isEmpty(receivedLoginForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        //UserLoginForm dto의 형식대로 정보를 받아 객체화
        UserLoginForm userLoginForm = gson.fromJson(receivedLoginForm, UserLoginForm.class);
        //받은 유저 정보를 토대로 토큰 생성
        TokenResponse tokenResponse = userService.logIn(userLoginForm.getEmail(), userLoginForm.getPassword());

        String loginTokenJson = gson.toJson(tokenResponse);

        return ResponseEntity.status(HttpStatus.OK).body(loginTokenJson);
    }

    //유저 업데이트
    @PostMapping("/update")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> update(@RequestBody String receivedUpdateForm,
                                         @AuthenticationPrincipal UserDetails userDetails) {

        if(CommonUtils.isEmpty(receivedUpdateForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        //UserUpdateForm의 dto 형식대로 정보 받음
        UserUpdateForm updateForm = gson.fromJson(receivedUpdateForm, UserUpdateForm.class);

        //받은 정보를 토대로 유저정보 업데이트
        UserDto findUserDto = userService.update(userService.findByEmail(userDetails.getUsername()).getId(),
                updateForm.getPassword(), updateForm.getNickname(), updateForm.getStudentNumber());

        String success = gson.toJson(findUserDto);

        return ResponseEntity.status(HttpStatus.OK).body(success);
    }

    //유저 정보
    @GetMapping("/info")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> getInfo(@AuthenticationPrincipal UserDetails user) {

        //유저 정보를 유저가 가진 권한과 함께 받음
        UserWithAuthorityForm userWithAuthorityForm = userService.getUserInfo(user.getUsername());

        String infoJson = gson.toJson(userWithAuthorityForm);

        return ResponseEntity.status(HttpStatus.OK).body(infoJson);
    }

    //토큰 만료시
    @PostMapping("/reissue")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<String> reissue(@RequestBody String tokenResponseForm,
                                          @AuthenticationPrincipal UserDetails user) {
        //TokenResponse 형식대로 json 수신
        TokenResponse tokenResponse = gson.fromJson(tokenResponseForm, TokenResponse.class);

        //토큰 재발급, 다만 토큰 유효성 검사 실패시 로그아웃시키고 홈으로 리다이렉트 시킬것
        TokenResponse tokenRefresh = userService.reissue(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
        String tokenJson = gson.toJson(tokenRefresh);

        return ResponseEntity.status(HttpStatus.OK).body(tokenJson);
    }

    //로그아웃
    @DeleteMapping("/logout")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<String> logout(@RequestBody String tokenResponseForm) {
        //TokenResponse 형식대로 json 수신
        TokenResponse tokenResponse = gson.fromJson(tokenResponseForm, TokenResponse.class);

        userService.logout(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
        return ResponseEntity.status(HttpStatus.OK).body("로그아웃 성공");
    }

    //유저 삭제
    @DeleteMapping("/delete")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<String> delete(@AuthenticationPrincipal UserDetails user) {
        userService.delete(user.getUsername());
        return ResponseEntity.status(HttpStatus.OK).body("회원 삭제 완료");
    }
}