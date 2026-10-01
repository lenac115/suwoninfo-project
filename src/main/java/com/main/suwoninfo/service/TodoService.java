package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Todo;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.TodoRequest;
import com.main.suwoninfo.dto.TodoResponse;
import com.main.suwoninfo.exception.CommonErrorCode;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.TodoErrorCode;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.repository.TodoRepository;
import com.main.suwoninfo.repository.UserRepository;
import com.main.suwoninfo.utils.ToUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    public List<TodoResponse> view(String email) {
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        // 시간표가 없으면 빈 목록 반환
        return todoRepository.findByUser(findUser.getEmail()).stream()
                .map(ToUtils::toTodoResponse)
                .toList();
    }

    @Transactional
    public void createTodo(TodoRequest todoDto, String email) {
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        int start = todoDto.startHour() * 60 + todoDto.startMinute();
        int end = todoDto.endHour() * 60 + todoDto.endMinute();
        validateTime(findUser, todoDto.dayList(), start, end, null);

        Todo todo = Todo.builder()
                .className(todoDto.className())
                .dayList(todoDto.dayList())
                .professor(todoDto.professor())
                .classroom(todoDto.classroom())
                .startHour(todoDto.startHour())
                .startMinute(todoDto.startMinute())
                .endHour(todoDto.endHour())
                .endMinute(todoDto.endMinute())
                .build();
        todo.setUser(findUser);
        todoRepository.save(todo);
    }

    @Transactional
    public void update(TodoResponse todoDto, String email) {
        Todo findTodo = todoRepository.findById(todoDto.id()).orElseThrow(()->new CustomException(TodoErrorCode.NOT_EXIST_TODO));
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        validateOwner(findTodo, findUser);

        int start = todoDto.startHour() * 60 + todoDto.startMinute();
        int end = todoDto.endHour() * 60 + todoDto.endMinute();
        validateTime(findUser, todoDto.dayList(), start, end, findTodo.getId());

        findTodo.update(todoDto);
    }

    @Transactional
    public void todoDelete(Long todoId, String email) {
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        Todo findTodo = todoRepository.findById(todoId).orElseThrow(() -> new CustomException(TodoErrorCode.NOT_EXIST_TODO));
        validateOwner(findTodo, findUser);

        todoRepository.delete(findTodo);
    }

    // 지연 로딩 프록시와 실제 엔티티가 섞일 수 있으므로 인스턴스(==)가 아닌 id로 비교
    private void validateOwner(Todo todo, User user) {
        if (!Objects.equals(todo.getUsers().getId(), user.getId()))
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);
    }

    // 시간 유효성 및 같은 요일 기존 시간표와의 겹침 검사 (수정 시 자기 자신은 제외)
    private void validateTime(User user, Todo.DayList day, int start, int end, Long excludeId) {
        if (day == null || start >= end)
            throw new CustomException(CommonErrorCode.INVALID_PARAMETER);

        boolean duplicated = todoRepository.findByUser(user.getEmail()).stream()
                .filter(t -> !t.getId().equals(excludeId))
                .anyMatch(t -> t.overlaps(day, start, end));
        if (duplicated)
            throw new CustomException(TodoErrorCode.DUPLICATED_TODO);
    }
}
