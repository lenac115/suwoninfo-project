package com.main.suwoninfo.controller;

import com.main.suwoninfo.dto.ChatMessageResponse;
import com.main.suwoninfo.dto.ChatRoomCreateRequest;
import com.main.suwoninfo.dto.ChatRoomResponse;
import com.main.suwoninfo.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 채팅방 관리/내역 조회용 REST API
 * 실시간 송수신은 STOMP(ChatStompController), 방 생성·목록·과거 메시지는 HTTP로 분리
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatRoomController {

    private final ChatService chatService;

    // 1:1 채팅방 생성 (이미 있으면 기존 방 반환)
    @PostMapping("/rooms")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ChatRoomResponse> createRoom(@Valid @RequestBody ChatRoomCreateRequest request,
                                                       @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(chatService.getOrCreateRoom(user.getUsername(), request.targetUserId()));
    }

    // 내 채팅방 목록
    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<ChatRoomResponse>> myRooms(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(chatService.findMyRooms(user.getUsername()));
    }

    // 메시지 내역 (커서 기반, 최신순). 다음 페이지는 응답 마지막 messageId를 cursor로 전달
    @GetMapping("/rooms/{roomId}/messages")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<ChatMessageResponse>> messages(@PathVariable Long roomId,
                                                              @RequestParam(required = false) Long cursor,
                                                              @RequestParam(defaultValue = "30") int size,
                                                              @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(chatService.findMessages(user.getUsername(), roomId, cursor, size));
    }
}
