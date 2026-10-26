package com.main.suwoninfo.controller;

import com.main.suwoninfo.dto.*;
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
    public ResponseEntity<?> createUser(@RequestBody UserRequest receivedForm) {

        System.out.println(receivedForm.email());
        if(CommonUtils.isEmpty(receivedForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        //유저 정보를 dto화 시켜서 join 실행
        UserResponse createdUser = userService.join(receivedForm);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    // =========================================================================
    // [테스트용] 비밀번호 재설정 — 이메일만 알면 인증 없이 해당 계정 비번을 덮어쓴다.
    //
    // ⚠️ 운영 배포 시에는 이 메서드와 SecurityConfig 의 "/users/reset-password" permitAll,
    //    프론트(views.js)의 재설정 폼을 반드시 주석 처리/삭제할 것.
    //    (누구나 임의 계정을 탈취할 수 있는 엔드포인트임)
    //
    // BCrypt 는 단방향 해시라 기존 비밀번호 복구는 불가능하므로 "찾기"가 아니라 "재설정"이다.
    // =========================================================================
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody UserRequest form) {
        userService.resetPasswordForTest(form.email(), form.password());
        return ResponseEntity.status(HttpStatus.OK).body("비밀번호가 재설정되었습니다. 새 비밀번호로 로그인하세요.");
    }

    //유저 로그인
    @PostMapping("/login")
    public ResponseEntity<?> logIn(@RequestBody UserRequest receivedLoginForm) {

        if(CommonUtils.isEmpty(receivedLoginForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }
        //받은 유저 정보를 토대로 토큰 생성
        TokenResponse tokenResponse = userService.logIn(receivedLoginForm.email(), receivedLoginForm.password());

        return ResponseEntity.status(HttpStatus.OK).body(tokenResponse);
    }

    //유저 업데이트
    @PostMapping("/update")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> update(@RequestBody UserRequest receivedUpdateForm,
                                         @AuthenticationPrincipal UserDetails userDetails) {

        if(CommonUtils.isEmpty(receivedUpdateForm)) {
            String message = "빈 객체 반환";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
        }

        //받은 정보를 토대로 유저정보 업데이트
        UserResponse findUserDto = userService.update(receivedUpdateForm, userDetails);
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
        TokenResponse tokenRefresh = userService.reissue(tokenResponseForm.accessToken(), tokenResponseForm.refreshToken());
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
    public ResponseEntity<?> delete(@AuthenticationPrincipal UserDetails user, @AuthenticationPrincipal UserDetails userDetails) {
        userService.delete(user.getUsername(), userDetails);
        return ResponseEntity.status(HttpStatus.OK).body("회원 삭제 완료");
    }
}