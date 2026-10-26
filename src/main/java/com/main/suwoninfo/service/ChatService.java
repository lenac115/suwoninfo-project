package com.main.suwoninfo.service;

import com.main.suwoninfo.domain.ChatMessage;
import com.main.suwoninfo.domain.ChatRoom;
import com.main.suwoninfo.domain.User;
import com.main.suwoninfo.dto.ChatMessageResponse;
import com.main.suwoninfo.dto.ChatRoomResponse;
import com.main.suwoninfo.exception.ChatErrorCode;
import com.main.suwoninfo.exception.CustomException;
import com.main.suwoninfo.exception.UserErrorCode;
import com.main.suwoninfo.repository.ChatMessageRepository;
import com.main.suwoninfo.repository.ChatRoomRepository;
import com.main.suwoninfo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 50;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    /**
     * 1:1 채팅방 생성. 이미 있으면 기존 방을 돌려준다.
     */
    @Transactional
    public ChatRoomResponse getOrCreateRoom(String email, Long targetUserId) {
        User me = findUserByEmail(email);
        if (me.getId().equals(targetUserId)) {
            throw new CustomException(ChatErrorCode.CANNOT_CHAT_WITH_SELF);
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));

        ChatRoom candidate = ChatRoom.of(me, target);
        ChatRoom room = chatRoomRepository
                .findByUser1IdAndUser2Id(candidate.getUser1().getId(), candidate.getUser2().getId())
                .orElseGet(() -> saveRoom(candidate));

        return toRoomResponse(room, me.getId());
    }

    // 두 유저가 거의 동시에 방을 만들면 둘 중 하나는 유니크 제약(uk_chat_room_users)에 걸려 실패한다.
    // 같은 트랜잭션 안에서 catch 후 재조회하면 트랜잭션이 rollback-only로 표시돼 커밋 시점에 터지므로
    // 여기서는 잡지 않고 실패시킨다. 클라이언트가 다시 요청하면 기존 방을 찾아 돌려준다.
    private ChatRoom saveRoom(ChatRoom candidate) {
        return chatRoomRepository.saveAndFlush(candidate);
    }

    public List<ChatRoomResponse> findMyRooms(String email) {
        User me = findUserByEmail(email);
        return chatRoomRepository.findAllByMember(me.getId()).stream()
                .map(room -> toRoomResponse(room, me.getId()))
                .toList();
    }

    /**
     * 메시지 내역 커서 페이지네이션 (최신순)
     * cursor가 null이면 가장 최근 메시지부터, 있으면 그 id보다 오래된 메시지를 가져온다.
     */
    public List<ChatMessageResponse> findMessages(String email, Long roomId, Long cursor, int size) {
        User me = findUserByEmail(email);
        validateMember(roomId, me.getId());

        PageRequest page = PageRequest.of(0, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        List<ChatMessage> messages = (cursor == null)
                ? chatMessageRepository.findLatest(roomId, page)
                : chatMessageRepository.findBefore(roomId, cursor, page);

        return messages.stream().map(ChatMessageResponse::from).toList();
    }

    /**
     * STOMP로 들어온 메시지를 검증 후 저장. 저장이 끝난 메시지만 Redis로 발행된다.
     */
    @Transactional
    public ChatMessageResponse saveMessage(String email, Long roomId, String content) {
        if (!StringUtils.hasText(content)) {
            throw new CustomException(ChatErrorCode.EMPTY_MESSAGE);
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new CustomException(ChatErrorCode.TOO_LONG_MESSAGE);
        }

        User sender = findUserByEmail(email);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ChatErrorCode.NOT_EXIST_CHAT_ROOM));
        if (!room.isMember(sender.getId())) {
            throw new CustomException(ChatErrorCode.NOT_CHAT_ROOM_MEMBER);
        }

        ChatMessage saved = chatMessageRepository.saveAndFlush(new ChatMessage(room, sender, content.strip()));
        return ChatMessageResponse.from(saved);
    }

    /**
     * SUBSCRIBE 시점 권한 체크 (StompAuthChannelInterceptor에서 호출)
     */
    public boolean isMember(String email, Long roomId) {
        return userRepository.findByEmail(email)
                .map(user -> chatRoomRepository.existsMember(roomId, user.getId()))
                .orElse(false);
    }

    private void validateMember(Long roomId, Long userId) {
        if (!chatRoomRepository.existsById(roomId)) {
            throw new CustomException(ChatErrorCode.NOT_EXIST_CHAT_ROOM);
        }
        if (!chatRoomRepository.existsMember(roomId, userId)) {
            throw new CustomException(ChatErrorCode.NOT_CHAT_ROOM_MEMBER);
        }
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(UserErrorCode.NOT_EXIST_EMAIL));
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room, Long myId) {
        User opponent = room.getOpponent(myId);
        return ChatRoomResponse.builder()
                .roomId(room.getId())
                .opponentId(opponent.getId())
                .opponentNickname(opponent.getNickname())
                .build();
    }
}
