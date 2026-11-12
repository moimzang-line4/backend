package com.moimzang.domain.meeting.repository;

import static com.moimzang.domain.meeting.entity.QMeeting.meeting;
import static com.moimzang.domain.meeting.entity.QMeetingCategory.meetingCategory;

import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class MeetingRepositoryImpl implements MeetingRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 회의 목록을 조건과 cursor로 조회한다. 카테고리명을 함께 내려줘야 해서 meetingCategory를 fetch join한다
    @Override
    public List<Meeting> search(Long groupTermId, MeetingStatus status, Long categoryId, String keyword,
                                 LocalDate cursorMeetingDate, Long cursorId, int limit) {

        return queryFactory.selectFrom(meeting)
                .leftJoin(meeting.meetingCategory, meetingCategory).fetchJoin()
                .where(baseConditions(groupTermId, status, categoryId, keyword))
                .where(cursorCondition(cursorMeetingDate, cursorId))
                .orderBy(meeting.meetingDate.desc(), meeting.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public Optional<Meeting> findForAssistant(Long groupTermId, Long meetingId) {
        return Optional.ofNullable(queryFactory.selectFrom(meeting)
                .leftJoin(meeting.meetingCategory, meetingCategory).fetchJoin()
                .where(meeting.id.eq(meetingId), meeting.groupTerm.id.eq(groupTermId))
                .fetchOne());
    }

    // 기수·상태·카테고리·키워드 조건을 모은다. status/categoryId/keyword 없는 필터는 null로 넘기면
    // QueryDSL이 조건을 자동으로 뺀다
    private BooleanExpression[] baseConditions(Long groupTermId, MeetingStatus status, Long categoryId, String keyword) {
        return new BooleanExpression[]{
                meeting.groupTerm.id.eq(groupTermId),
                statusEq(status),
                categoryIdEq(categoryId),
                keywordContains(keyword)
        };
    }

    // status 필터 조건을 만든다. 값이 없으면 조건을 걸지 않는다
    private BooleanExpression statusEq(MeetingStatus status) {
        return status == null ? null : meeting.status.eq(status);
    }

    // categoryId 필터 조건을 만든다. 값이 없으면 조건을 걸지 않는다
    private BooleanExpression categoryIdEq(Long categoryId) {
        return categoryId == null ? null : meeting.meetingCategory.id.eq(categoryId);
    }

    // keyword 검색 조건을 만든다. 제목·목록 요약·참석자 중 하나라도 일치하면 노출한다
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return meeting.title.containsIgnoreCase(keyword)
                .or(meeting.listSummary.containsIgnoreCase(keyword))
                .or(meeting.attendees.containsIgnoreCase(keyword));
    }

    // 다음 페이지 cursor 조건을 만든다 — (meetingDate, id) 복합 비교. 내림차순이라 lt를 쓴다.
    // 첫 페이지(cursorId 없음)는 조건 없음
    private BooleanExpression cursorCondition(LocalDate cursorMeetingDate, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return meeting.meetingDate.lt(cursorMeetingDate)
                .or(meeting.meetingDate.eq(cursorMeetingDate).and(meeting.id.lt(cursorId)));
    }
}
