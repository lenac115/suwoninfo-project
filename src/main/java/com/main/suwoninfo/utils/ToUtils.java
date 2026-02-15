package com.main.suwoninfo.utils;

import com.main.suwoninfo.domain.*;
import com.main.suwoninfo.dto.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ToUtils {

    public static PostResponse toPostResponse(Post p) {
        return PostResponse.builder()
                .postId(p.getId())
                .content(p.getContent())
                .postType(p.getPostType())
                .price(p.getPrice())
                .title(p.getTitle())
                .tradeStatus(p.getTradeStatus())
                .photos(p.getPhoto().stream()
                        .map(ToUtils::toPhotoResponse)
                        .toList())
                .build();
    }

    public static UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .auth(user.getAuth())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .studentNumber(user.getStudentNumber())
                .password(user.getPassword())
                .name(user.getName())
                .build();
    }

    public static PhotoResponse toPhotoResponse(Photo p) {
        return PhotoResponse.builder()
                .filePath(p.getFilePath())
                .origFileName(p.getOrigFileName())
                .photoId(p.getId())
                .fileSize(p.getFileSize())
                .build();
    }

    public static CommentResponse toCommentResponse(Comment c) {
        return CommentResponse.builder()
                .id(c.getId())
                .user(toUserResponse(c.getUser()))
                .content(c.getContent())
                .post(toPostResponse(c.getPost()))
                .build();
    }

    public static TodoResponse toTodoResponse(Todo t) {
        return TodoResponse.builder()
                .id(t.getId())
                .className(t.getClassName())
                .professor(t.getProfessor())
                .classroom(t.getClassroom())
                .dayList(t.getDayList())
                .startMinute(t.getStartMinute())
                .startHour(t.getStartHour())
                .endHour(t.getEndHour())
                .userResponse(toUserResponse(t.getUser()))
                .endMinute(t.getEndMinute())
                .build();
    }
}