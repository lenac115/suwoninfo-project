package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.Todo;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.TodoDto;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.TodoErrorCode;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.idempotent.Idempotent;
import com.main.suwoninfo.repository.TodoRepository;
import com.main.suwoninfo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    public List<TodoDto> view(String email) {
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        List<Todo> todoList = todoRepository.findByUser(findUser.getEmail());
        if (todoList.isEmpty())
            throw new CustomException(TodoErrorCode.NOT_EXIST_TODO);

        return toDto(todoList);
    }

    @Transactional
    @Idempotent(user = "#principal.id", key = "#idemKey")
    public void createTodo(TodoDto todoDto, String email, String idemKey) {
        Todo todo = toTodo(todoDto, email);
        List<Todo> todoList = todoRepository.findByUser(email);
        if(todoList.isEmpty())
            todoRepository.createTodo(todo);
        else {
            for (Todo getedTodo : todoList) {
                int getedStartTime = getedTodo.getStartHour() * 60 + getedTodo.getStartMinute();
                int getedEndTime = getedTodo.getEndHour() * 60 + getedTodo.getEndMinute();
                int newStartTime = todo.getStartHour() * 60 + todo.getStartMinute();
                int newEndTime = todo.getEndHour() * 60 + todo.getEndMinute();
                if ((newStartTime >= getedStartTime && newStartTime < getedEndTime) ||
                        (newStartTime < getedStartTime && newEndTime > getedStartTime)) {
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

    private Todo toTodo(TodoDto todoDto, String email) {
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        Todo todo = new Todo();
        todo.setUser(findUser);
        todo.setDayList(todoDto.getDayList());
        todo.setClassroom(todoDto.getClassroom());
        todo.setProfessor(todoDto.getProfessor());
        todo.setClassName(todoDto.getClassName());
        todo.setStartHour(todoDto.getStartHour());
        todo.setStartMinute(todoDto.getStartMinute());
        todo.setEndHour(todoDto.getEndHour());
        todo.setEndMinute(todoDto.getEndMinute());

        return todo;
    }

    private List<TodoDto> toDto(List<Todo> byUser) {
        List<TodoDto> todoDtoList = new ArrayList<>();

        for (Todo todo : byUser) {
            todoDtoList.add(TodoDto.builder()
                    .id(todo.getId())
                    .className(todo.getClassName())
                    .professor(todo.getProfessor())
                    .classroom(todo.getClassroom())
                    .dayList(todo.getDayList())
                    .startMinute(todo.getStartMinute())
                    .startHour(todo.getStartHour())
                    .endHour(todo.getEndHour())
                    .endMinute(todo.getEndMinute())
                    .build());
        }

        return todoDtoList;
    }

    @Transactional
    public void update(TodoDto todoDto, String email) {
        Todo findTodo = todoRepository.findById(todoDto.getId()).orElseThrow(()->new CustomException(TodoErrorCode.NOT_EXIST_TODO));
        User findUser = userRepository.findByEmail(email).orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
        if (findTodo.getUser() !=  findUser)
            throw new CustomException(UserErrorCode.NOT_EQUAL_EMAIL);

        findTodo.setClassName(todoDto.getClassName());
        findTodo.setClassroom(todoDto.getClassroom());
        findTodo.setStartHour(todoDto.getStartHour());
        findTodo.setStartMinute(todoDto.getStartMinute());
        findTodo.setEndHour(todoDto.getEndHour());
        findTodo.setEndMinute(todoDto.getEndMinute());
        findTodo.setDayList(todoDto.getDayList());
        findTodo.setProfessor(todoDto.getProfessor());
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
