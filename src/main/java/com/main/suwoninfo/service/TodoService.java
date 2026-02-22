package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Todo;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.TodoRequest;
import com.main.suwoninfo.dto.TodoResponse;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    public List<TodoResponse> view(String email) {
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        List<Todo> todoList = todoRepository.findByUser(findUser.getEmail());
        if (todoList.isEmpty())
            throw new CustomException(TodoErrorCode.NOT_EXIST_TODO);

        return todoList.stream().map(ToUtils::toTodoResponse).toList();
    }

    @Transactional
    public void createTodo(TodoRequest todoDto, String email) {
        Todo todo = Todo.builder()
                .dayList(todoDto.dayList())
                .professor(todoDto.professor())
                .classroom(todoDto.classroom())
                .startHour(todoDto.startHour())
                .startMinute(todoDto.startMinute())
                .endHour(todoDto.endHour())
                .endMinute(todoDto.endMinute())
                .build();
        List<Todo> todoList = todoRepository.findByUser(email);
        if(todoList.isEmpty())
            todoRepository.createTodo(todo);
        else {
            for (Todo getedTodo : todoList) {
                int gotStartTime = getedTodo.getStartHour() * 60 + getedTodo.getStartMinute();
                int gotEndTime = getedTodo.getEndHour() * 60 + getedTodo.getEndMinute();
                int newStartTime = todo.getStartHour() * 60 + todo.getStartMinute();
                int newEndTime = todo.getEndHour() * 60 + todo.getEndMinute();
                if ((newStartTime >= gotStartTime && newStartTime < gotEndTime) ||
                        (newStartTime < gotStartTime && newEndTime > gotStartTime)) {
                    if (getedTodo.getDayList() == todo.getDayList())
                        throw new CustomException(TodoErrorCode.DUPLICATED_TODO);
                    else
                        todoRepository.createTodo(todo);
                } else {
                    todoRepository.createTodo(todo);
                }
            }
        }
    }

    @Transactional
    public void update(TodoResponse todoDto, String email) {
        Todo findTodo = todoRepository.findById(todoDto.id()).orElseThrow(()->new CustomException(TodoErrorCode.NOT_EXIST_TODO));
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        if (findTodo.getUser() !=  findUser)
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);

        findTodo.update(findTodo);
    }

    @Transactional
    public void todoDelete(Long todoId, String email) {
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        Todo findTodo = todoRepository.findById(todoId).orElseThrow(() -> new CustomException(TodoErrorCode.NOT_EXIST_TODO));
        if(findTodo.getUser() != findUser)
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);

        todoRepository.delete(findTodo);
    }
}
