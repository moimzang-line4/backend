package com.moimzang.domain.chat.repository;

import static com.moimzang.domain.chat.entity.QAiChatRoom.aiChatRoom;

import com.moimzang.domain.chat.entity.AiChatRoom;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class AiChatRoomRepositoryImpl implements AiChatRoomRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 최근 대화한 방이 먼저 보이도록 updatedAt 내림차순으로 조회한다
    @Override
    public List<AiChatRoom> search(Long groupTermId, String keyword, LocalDateTime cursorUpdatedAt, Long cursorId,
                                    int limit) {
        return queryFactory.selectFrom(aiChatRoom)
                .where(baseConditions(groupTermId, keyword))
                .where(cursorCondition(cursorUpdatedAt, cursorId))
                .orderBy(aiChatRoom.updatedAt.desc(), aiChatRoom.id.desc())
                .limit(limit)
                .fetch();
    }

    // search와 같은 필터를 쓰되 cursor·정렬·limit만 빼고 건수를 센다
    @Override
    public long count(Long groupTermId, String keyword) {
        Long count = queryFactory.select(aiChatRoom.count())
                .from(aiChatRoom)
                .where(baseConditions(groupTermId, keyword))
                .fetchOne();

        return count == null ? 0 : count;
    }

    // 기수·검색어 조건을 모은다. keyword가 없으면 null로 넘겨 QueryDSL이 조건을 자동으로 뺀다
    private BooleanExpression[] baseConditions(Long groupTermId, String keyword) {
        return new BooleanExpression[]{
                aiChatRoom.groupTerm.id.eq(groupTermId),
                titleContains(keyword)
        };
    }

    // 제목 검색 조건을 만든다. 제목이 아직 없는 방(첫 질문 전)은 LIKE가 NULL을 거르므로 결과에서 빠진다
    private BooleanExpression titleContains(String keyword) {
        return StringUtils.hasText(keyword) ? aiChatRoom.title.containsIgnoreCase(keyword) : null;
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
