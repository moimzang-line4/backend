package com.moimzang.domain.chat.repository;

import com.moimzang.domain.chat.entity.AiMessage;
import java.time.LocalDateTime;
import java.util.List;

public interface AiMessageRepositoryCustom {

    // 대화방의 메시지를 대화 순서(createdAt 오름차순)로 cursor 조회한다
    List<AiMessage> search(Long chatRoomId, LocalDateTime cursorCreatedAt, Long cursorId, int limit);

    // 프롬프트에 실을 최근 대화 히스토리. 최신순으로 limit건만 가져온다
    List<AiMessage> findRecentHistory(Long chatRoomId, int limit);
}
