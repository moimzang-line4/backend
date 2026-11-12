package com.moimzang.domain.chat.repository;

import com.moimzang.domain.chat.entity.AiChatRoom;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiChatRoomRepository extends JpaRepository<AiChatRoom, Long>, AiChatRoomRepositoryCustom {

    // 메시지를 추가해도 대화방 엔티티 자체는 변하지 않아 @LastModifiedDate가 동작하지 않는다.
    // 목록 정렬이 "최근 대화한 방 먼저"라서 활동 시각을 직접 갱신해야 한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AiChatRoom r set r.updatedAt = :now where r.id = :id")
    void touchUpdatedAt(@Param("id") Long id, @Param("now") LocalDateTime now);
}
