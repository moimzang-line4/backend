package com.moimzang.domain.chat.repository;

import static com.moimzang.domain.chat.entity.QAiMessage.aiMessage;

import com.moimzang.domain.chat.entity.AiMessage;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AiMessageRepositoryImpl implements AiMessageRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 대화는 읽는 순서가 곧 시간순이라 오름차순으로 정렬한다
    @Override
    public List<AiMessage> search(Long chatRoomId, LocalDateTime cursorCreatedAt, Long cursorId, int limit) {
        return queryFactory.selectFrom(aiMessage)
                .where(aiMessage.aiChatRoom.id.eq(chatRoomId))
                .where(cursorCondition(cursorCreatedAt, cursorId))
                .orderBy(aiMessage.createdAt.asc(), aiMessage.id.asc())
                .limit(limit)
                .fetch();
    }

    // 히스토리는 "가장 최근 limit건"이 필요해서 내림차순으로 뽑는다.
    // 프롬프트에 넣기 전에 호출하는 쪽에서 순서를 뒤집는다
    @Override
    public List<AiMessage> findRecentHistory(Long chatRoomId, int limit) {
        return queryFactory.selectFrom(aiMessage)
                .where(aiMessage.aiChatRoom.id.eq(chatRoomId))
                .orderBy(aiMessage.createdAt.desc(), aiMessage.id.desc())
                .limit(limit)
                .fetch();
    }

    // 다음 페이지 cursor 조건 — (createdAt, id) 복합 비교. 오름차순이라 gt를 쓴다
    private BooleanExpression cursorCondition(LocalDateTime cursorCreatedAt, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return aiMessage.createdAt.gt(cursorCreatedAt)
                .or(aiMessage.createdAt.eq(cursorCreatedAt).and(aiMessage.id.gt(cursorId)));
    }
}
