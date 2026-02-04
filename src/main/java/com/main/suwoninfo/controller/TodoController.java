package com.main.suwoninfo.controller;

import com.google.gson.Gson;
import com.main.suwoninfo.dto.TodoRequest;
import com.main.suwoninfo.dto.TodoResponse;
import com.main.suwoninfo.service.TodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/todo")
public class TodoController {

    private final TodoService todoService;

    // 시간표 생성
    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> createTodo(@RequestBody TodoRequest todoDto,
                                        @AuthenticationPrincipal UserDetails user) {
        //TodoDto에 맞춰서 객체를 받아오고 생성
        todoService.createTodo(todoDto, user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body("저장 완료");
    }

    // 시간표 뷰
    @GetMapping("/view")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> view(@AuthenticationPrincipal UserDetails user) {

        //user의 시간표를 불러옴
        return ResponseEntity.status(HttpStatus.OK).body(todoService.view(user.getUsername()));
    }

    // 시간표 수정
    @PostMapping("/update")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> update(@RequestBody TodoResponse todoDto, @AuthenticationPrincipal UserDetails user) {

        // 수정할 TodoDto 내용을 받아 수정
        todoService.update(todoDto, user.getUsername());

        return ResponseEntity.status(HttpStatus.OK).body("업데이트 완료");
    }

    // 시간표 삭제
    @DeleteMapping("/delete/{todoId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long todoId, @AuthenticationPrincipal UserDetails user) {

        todoService.todoDelete(todoId, user.getUsername());

        return ResponseEntity.status(HttpStatus.OK).body("삭제 완료");
    }
}
