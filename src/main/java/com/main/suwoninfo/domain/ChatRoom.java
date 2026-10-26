package com.main.suwoninfo.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1:1 채팅방
 * 같은 두 유저 사이에 방이 중복 생성되지 않도록
 * 항상 id가 작은 유저를 user1, 큰 유저를 user2로 저장하고 유니크 제약을 건다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "chat_room",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_room_users", columnNames = {"user1_id", "user2_id"}),
        indexes = @Index(name = "idx_chat_room_user2", columnList = "user2_id")
)
public class ChatRoom extends Time {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user1_id")
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user2_id")
    private User user2;

    private ChatRoom(User user1, User user2) {
        this.user1 = user1;
        this.user2 = user2;
    }

    // 두 유저의 순서와 상관없이 같은 방이 되도록 id 기준으로 정렬해서 생성
    public static ChatRoom of(User a, User b) {
        return a.getId() < b.getId() ? new ChatRoom(a, b) : new ChatRoom(b, a);
    }

    public boolean isMember(Long userId) {
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }

    public User getOpponent(Long userId) {
        return user1.getId().equals(userId) ? user2 : user1;
    }
}
