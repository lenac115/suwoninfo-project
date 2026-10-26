package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    // 내가 속한 채팅방 목록 (상대방 정보까지 한 번에 로딩)
    @Query("select r from ChatRoom r join fetch r.user1 join fetch r.user2 " +
            "where r.user1.id = :userId or r.user2.id = :userId " +
            "order by r.id desc")
    List<ChatRoom> findAllByMember(@Param("userId") Long userId);

    // 구독/발송 권한 체크용. 엔티티를 로딩하지 않고 존재 여부만 확인
    @Query("select count(r) > 0 from ChatRoom r " +
            "where r.id = :roomId and (r.user1.id = :userId or r.user2.id = :userId)")
    boolean existsMember(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
