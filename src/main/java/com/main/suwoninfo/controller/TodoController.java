package com.main.suwoninfo.controller;

import com.google.gson.Gson;
import com.main.suwoninfo.dto.TodoDto;
import com.main.suwoninfo.service.TodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/todo")
public class TodoController {

    private final TodoService todoService;
    private final Gson gson;

    // 시간표 생성
    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> createTodo(@RequestBody String todoDto, @AuthenticationPrincipal UserDetails user) {
        //TodoDto에 맞춰서 객체를 받아오고 생성
        TodoDto getDto = gson.fromJson(todoDto, TodoDto.class);
        todoService.createTodo(getDto, user.getUsername());

        return ResponseEntity.status(HttpStatus.OK).body("저장 완료");
    }

    // 시간표 뷰
    @GetMapping("/view")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> view(@AuthenticationPrincipal UserDetails user) {

        //user의 시간표를 불러옴
        List<TodoDto> todoDtoList = todoService.view(user.getUsername());
        String todoList = gson.toJson(todoDtoList);

        return ResponseEntity.status(HttpStatus.OK).body(todoList);
    }

    // 시간표 수정
    @PostMapping("/update")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> update(@RequestBody String todoDto, @AuthenticationPrincipal UserDetails user) {

        // 수정할 TodoDto 내용을 받아 수정
        TodoDto getDto = gson.fromJson(todoDto, TodoDto.class);
        todoService.update(getDto, user.getUsername());

        return ResponseEntity.status(HttpStatus.OK).body("업데이트 완료");
    }

    // 시간표 삭제
    @DeleteMapping("/delete/{todoId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long todoId, @AuthenticationPrincipal UserDetails user) {

        todoService.todoDelete(todoId, user.getUsername());

        return ResponseEntity.status(HttpStatus.OK).body("삭제 완료");
    }
}
