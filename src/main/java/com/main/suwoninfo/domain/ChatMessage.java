package com.main.suwoninfo.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지
 * Redis Pub/Sub은 메시지를 보관하지 않으므로 내역은 반드시 DB에 저장한다.
 * (chat_room_id, chat_message_id) 복합 인덱스로 방별 커서 페이지네이션을 처리한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "chat_message",
        indexes = @Index(name = "idx_chat_message_room_id", columnList = "chat_room_id, chat_message_id")
)
public class ChatMessage extends Time {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, length = 1000)
    private String content;

    public ChatMessage(ChatRoom chatRoom, User sender, String content) {
        this.chatRoom = chatRoom;
        this.sender = sender;
        this.content = content;
    }
}
