package com.moimzang.domain.note.repository;

import static com.moimzang.domain.note.entity.QOperationNote.operationNote;
import static com.moimzang.domain.schedule.entity.QSchedule.schedule;

import com.moimzang.domain.note.entity.OperationNote;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class OperationNoteRepositoryImpl implements OperationNoteRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 메모는 일정에 딸려 있어 기수 조건이 일정을 거쳐야 걸린다. 어느 일정의 메모인지도
    // 응답에 써야 해서 schedule을 fetch join한다
    @Override
    public List<OperationNote> searchForAssistant(Long groupTermId, String keyword, Long scheduleId, int limit) {
        return queryFactory.selectFrom(operationNote)
                .join(operationNote.schedule, schedule).fetchJoin()
                .where(schedule.groupTerm.id.eq(groupTermId))
                .where(keywordContains(keyword), scheduleIdEq(scheduleId))
                .orderBy(operationNote.createdAt.desc(), operationNote.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public long countForAssistant(Long groupTermId, String keyword, Long scheduleId) {
        Long count = queryFactory.select(operationNote.count())
                .from(operationNote)
                .join(operationNote.schedule, schedule)
                .where(schedule.groupTerm.id.eq(groupTermId))
                .where(keywordContains(keyword), scheduleIdEq(scheduleId))
                .fetchOne();
        return count == null ? 0 : count;
    }

    // 제목만 보면 "회비 입금 확인" 같은 메모를 '회비'로 못 찾는 경우가 생겨 내용·담당자까지 본다
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return operationNote.title.containsIgnoreCase(keyword)
                .or(operationNote.content.containsIgnoreCase(keyword))
                .or(operationNote.assignee.containsIgnoreCase(keyword));
    }

    private BooleanExpression scheduleIdEq(Long scheduleId) {
        return scheduleId == null ? null : operationNote.schedule.id.eq(scheduleId);
    }
}
