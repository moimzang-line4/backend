package com.moimzang.domain.chat.repository;

import static com.moimzang.domain.chat.entity.QAiChatRoom.aiChatRoom;

import com.moimzang.domain.chat.entity.AiChatRoom;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AiChatRoomRepositoryImpl implements AiChatRoomRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 최근 대화한 방이 먼저 보이도록 updatedAt 내림차순으로 조회한다
    @Override
    public List<AiChatRoom> search(Long groupTermId, LocalDateTime cursorUpdatedAt, Long cursorId, int limit) {
        return queryFactory.selectFrom(aiChatRoom)
                .where(aiChatRoom.groupTerm.id.eq(groupTermId))
                .where(cursorCondition(cursorUpdatedAt, cursorId))
                .orderBy(aiChatRoom.updatedAt.desc(), aiChatRoom.id.desc())
                .limit(limit)
                .fetch();
    }

    // 다음 페이지 cursor 조건 — (updatedAt, id) 복합 비교. 내림차순이라 lt를 쓴다.
    // 첫 페이지(cursorId 없음)는 조건 없음
    private BooleanExpression cursorCondition(LocalDateTime cursorUpdatedAt, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return aiChatRoom.updatedAt.lt(cursorUpdatedAt)
                .or(aiChatRoom.updatedAt.eq(cursorUpdatedAt).and(aiChatRoom.id.lt(cursorId)));
    }
}
