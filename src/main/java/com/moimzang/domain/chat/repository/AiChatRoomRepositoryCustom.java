package com.moimzang.domain.chat.repository;

import com.moimzang.domain.chat.entity.AiChatRoom;
import java.time.LocalDateTime;
import java.util.List;

public interface AiChatRoomRepositoryCustom {

    // 기수의 대화방 목록을 최근 활동 순으로 cursor 조회한다
    List<AiChatRoom> search(Long groupTermId, LocalDateTime cursorUpdatedAt, Long cursorId, int limit);
}
