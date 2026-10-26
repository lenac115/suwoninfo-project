package com.main.suwoninfo.repository;

import com.main.suwoninfo.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 가장 최근 메시지부터 (첫 페이지)
    @Query("select m from ChatMessage m join fetch m.sender " +
            "where m.chatRoom.id = :roomId order by m.id desc")
    List<ChatMessage> findLatest(@Param("roomId") Long roomId, Pageable pageable);

    // cursor(메시지 id)보다 오래된 메시지 (스크롤 올릴 때)
    @Query("select m from ChatMessage m join fetch m.sender " +
            "where m.chatRoom.id = :roomId and m.id < :cursor order by m.id desc")
    List<ChatMessage> findBefore(@Param("roomId") Long roomId, @Param("cursor") Long cursor, Pageable pageable);
}
