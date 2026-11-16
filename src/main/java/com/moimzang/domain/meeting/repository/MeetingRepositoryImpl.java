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
import com.moimzang.global.text.SingleLineText;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class MeetingRepositoryImpl implements MeetingRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 회의 목록을 조건과 cursor로 조회한다. 카테고리명을 함께 내려줘야 해서 meetingCategory를 fetch join한다
    @Override
    public List<Meeting> search(Long groupTermId, MeetingStatus status, Long categoryId, String keyword,
                                 Long scheduleId, LocalDate cursorMeetingDate, Long cursorId, int limit) {

        return queryFactory.selectFrom(meeting)
                .leftJoin(meeting.meetingCategory, meetingCategory).fetchJoin()
                .where(baseConditions(groupTermId, status, categoryId, keyword, scheduleId))
                .where(cursorCondition(cursorMeetingDate, cursorId))
                .orderBy(meeting.meetingDate.desc(), meeting.id.desc())
                .limit(limit)
                .fetch();
    }

    // search와 같은 필터를 쓰되 cursor·정렬·limit만 빼고 건수를 센다
    @Override
    public long count(Long groupTermId, MeetingStatus status, Long categoryId, String keyword, Long scheduleId) {
        Long count = queryFactory.select(meeting.count())
                .from(meeting)
                .where(baseConditions(groupTermId, status, categoryId, keyword, scheduleId))
                .fetchOne();

        return count == null ? 0 : count;
    }

    // 일정 상세 화면의 회의록 카드와 같은 집합이다. 카테고리명을 함께 쓰므로 fetch join한다
    @Override
    public List<Meeting> findByScheduleForAssistant(Long scheduleId) {
        return queryFactory.selectFrom(meeting)
                .leftJoin(meeting.meetingCategory, meetingCategory).fetchJoin()
                .where(meeting.schedule.id.eq(scheduleId))
                .orderBy(meeting.meetingDate.desc(), meeting.id.desc())
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
    private BooleanExpression[] baseConditions(Long groupTermId, MeetingStatus status, Long categoryId,
                                               String keyword, Long scheduleId) {
        return new BooleanExpression[]{
                meeting.groupTerm.id.eq(groupTermId),
                statusEq(status),
                categoryIdEq(categoryId),
                keywordContains(keyword),
                scheduleIdEq(scheduleId)
        };
    }

    // 소속 일정 필터 조건을 만든다. 값이 없으면 조건을 걸지 않아 일정 소속 회의록까지 전부 포함한다
    private BooleanExpression scheduleIdEq(Long scheduleId) {
        return scheduleId == null ? null : meeting.schedule.id.eq(scheduleId);
    }

    // status 필터 조건을 만든다. 값이 없으면 조건을 걸지 않는다
    private BooleanExpression statusEq(MeetingStatus status) {
        return status == null ? null : meeting.status.eq(status);
    }

    // categoryId 필터 조건을 만든다. 값이 없으면 조건을 걸지 않는다
    private BooleanExpression categoryIdEq(Long categoryId) {
        return categoryId == null ? null : meeting.meetingCategory.id.eq(categoryId);
    }

    // keyword 검색 조건을 만든다. 제목·참석자 중 하나라도 일치하면 노출한다.
    // 검색어도 저장값과 같은 규칙으로 편다 — 제목은 연속 공백을 접어 저장하므로(decisions #84)
    // 검색어만 안 펴면 "가을  정기공연"처럼 공백을 두 번 친 입력이 0건이 된다
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalized = SingleLineText.normalize(keyword);
        return meeting.title.containsIgnoreCase(normalized)
                .or(meeting.attendees.containsIgnoreCase(normalized));
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
