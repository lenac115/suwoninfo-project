package com.main.suwoninfo.controller;

import com.main.suwoninfo.dto.*;
import com.main.suwoninfo.form.*;
import com.main.suwoninfo.service.UserService;
import com.main.suwoninfo.utils.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    //유저 생성
    @PostMapping("/new")
    public ResponseEntity<?> createUser(@RequestBody UserForm receivedForm) {

        if(CommonUtils.isEmpty(receivedForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        //유저 정보를 dto화 시켜서 join 실행
        UserDto createdUser = userService.join(receivedForm);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    //유저 로그인
    @PostMapping("/login")
    public ResponseEntity<?> logIn(@RequestBody UserLoginForm receivedLoginForm) {

        if(CommonUtils.isEmpty(receivedLoginForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
        //받은 유저 정보를 토대로 토큰 생성
        TokenResponse tokenResponse = userService.logIn(receivedLoginForm.getEmail(), receivedLoginForm.getPassword());

        return ResponseEntity.status(HttpStatus.OK).body(tokenResponse);
    }

    //유저 업데이트
    @PostMapping("/update")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> update(@RequestBody UserUpdateForm receivedUpdateForm,
                                         @AuthenticationPrincipal UserDetails userDetails) {

        if(CommonUtils.isEmpty(receivedUpdateForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        //받은 정보를 토대로 유저정보 업데이트
        UserDto findUserDto = userService.update(userService.findByEmail(userDetails.getUsername()).getId(),
                receivedUpdateForm.getPassword(), receivedUpdateForm.getNickname(), receivedUpdateForm.getStudentNumber());
        return ResponseEntity.status(HttpStatus.OK).body(findUserDto);
    }

    //유저 정보
    @GetMapping("/info")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> getInfo(@AuthenticationPrincipal UserDetails user) {

        //유저 정보를 유저가 가진 권한과 함께 받음
        return ResponseEntity.status(HttpStatus.OK).body(userService.getUserInfo(user.getUsername()));
    }

    //토큰 만료시
    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(@RequestBody TokenResponse tokenResponseForm) {

        //토큰 재발급, 다만 토큰 유효성 검사 실패시 로그아웃시키고 홈으로 리다이렉트 시킬것
        TokenResponse tokenRefresh = userService.reissue(tokenResponseForm.getAccessToken(), tokenResponseForm.getRefreshToken());
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(tokenRefresh);
    }

    //로그아웃
    @DeleteMapping("/logout")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<?> logout(@AuthenticationPrincipal UserDetails user, @RequestHeader(name = "Authorization") String accessToken) {
        userService.logout(user.getUsername(), accessToken);
        return ResponseEntity.status(HttpStatus.OK).body("로그아웃 성공");
    }

    //유저 삭제
    @DeleteMapping("/delete")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<?> delete(@AuthenticationPrincipal UserDetails user) {
        userService.delete(user.getUsername());
        return ResponseEntity.status(HttpStatus.OK).body("회원 삭제 완료");
    }
}