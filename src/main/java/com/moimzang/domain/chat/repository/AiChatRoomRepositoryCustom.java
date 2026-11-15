package com.moimzang.domain.chat.repository;

import com.moimzang.domain.chat.entity.AiChatRoom;
import java.time.LocalDateTime;
import java.util.List;

public interface AiChatRoomRepositoryCustom {

    // 기수의 대화방 목록을 제목 검색어 조건과 cursor로 최근 활동 순으로 조회한다
    List<AiChatRoom> search(Long groupTermId, String keyword, LocalDateTime cursorUpdatedAt, Long cursorId,
                             int limit);

    // 목록 totalCount용 — cursor 조건 없이 "현재 검색어에 맞는 전체 건수"만 센다
    long count(Long groupTermId, String keyword);
}
