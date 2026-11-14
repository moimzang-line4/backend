package com.moimzang.domain.schedule.repository;

import static com.moimzang.domain.meeting.entity.QMeeting.meeting;
import static com.moimzang.domain.schedule.entity.QSchedule.schedule;

import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.schedule.enums.OperationSpaceSort;
import com.moimzang.domain.schedule.enums.OperationSpaceStatus;
import com.moimzang.domain.schedule.entity.Schedule;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

// 운영 공간 목록은 일정과 회의록 두 테이블을 한 목록으로 합쳐 보여준다.
// 테이블마다 따로 조회해서 서비스가 합치므로, 두 쿼리의 정렬·cursor 규칙이 정확히 같아야 한다.
// 정렬 키는 (날짜 또는 제목, 유형 순서, id) 세 칸이다. 유형 순서는 일정 0, 회의 1로 두 테이블의 id가 겹칠 때 순서를 가른다.
// 날짜 키는 일정은 시작 시각, 회의는 회의 날짜 0시로 맞춘다
@Repository
@RequiredArgsConstructor
public class OperationSpaceQueryRepository {

    public static final int SCHEDULE_TYPE_ORDER = 0;
    public static final int MEETING_TYPE_ORDER = 1;

    private final JPAQueryFactory queryFactory;

    // 운영 공간을 켠 일정만 대상이다 — 토글을 켜지 않은 일정은 운영 메뉴가 없다
    public List<Schedule> findSchedules(Long groupTermId, OperationSpaceStatus status, LocalDateTime now,
                                        String keyword, OperationSpaceSort sort, OperationSpaceCursor cursor,
                                        int limit) {
        return queryFactory.selectFrom(schedule)
                .where(schedule.groupTerm.id.eq(groupTermId),
                        schedule.hasOperationSpace.isTrue(),
                        status == OperationSpaceStatus.PAST ? schedule.endAt.loe(now) : schedule.endAt.gt(now),
                        StringUtils.hasText(keyword) ? schedule.title.containsIgnoreCase(keyword) : null,
                        scheduleCursor(sort, cursor))
                .orderBy(scheduleOrder(sort))
                .limit(limit)
                .fetch();
    }

    // 회의록은 토글 개념이 없어 일정 소속 회의록까지 전부 대상이다
    public List<Meeting> findMeetings(Long groupTermId, OperationSpaceStatus status, String keyword,
                                      OperationSpaceSort sort, OperationSpaceCursor cursor, int limit) {
        return queryFactory.selectFrom(meeting)
                .where(meeting.groupTerm.id.eq(groupTermId),
                        meeting.status.eq(status == OperationSpaceStatus.PAST
                                ? MeetingStatus.COMPLETED : MeetingStatus.IN_PROGRESS),
                        StringUtils.hasText(keyword) ? meeting.title.containsIgnoreCase(keyword) : null,
                        meetingCursor(sort, cursor))
                .orderBy(meetingOrder(sort))
                .limit(limit)
                .fetch();
    }

    private OrderSpecifier<?>[] scheduleOrder(OperationSpaceSort sort) {
        boolean desc = isDescending(sort);
        if (sort == OperationSpaceSort.NAME) {
            return new OrderSpecifier<?>[]{schedule.title.asc(), schedule.id.asc()};
        }
        return desc
                ? new OrderSpecifier<?>[]{schedule.startAt.desc(), schedule.id.desc()}
                : new OrderSpecifier<?>[]{schedule.startAt.asc(), schedule.id.asc()};
    }

    private OrderSpecifier<?>[] meetingOrder(OperationSpaceSort sort) {
        boolean desc = isDescending(sort);
        if (sort == OperationSpaceSort.NAME) {
            return new OrderSpecifier<?>[]{meeting.title.asc(), meeting.id.asc()};
        }
        return desc
                ? new OrderSpecifier<?>[]{meeting.meetingDate.desc(), meeting.id.desc()}
                : new OrderSpecifier<?>[]{meeting.meetingDate.asc(), meeting.id.asc()};
    }

    private BooleanExpression scheduleCursor(OperationSpaceSort sort, OperationSpaceCursor cursor) {
        if (cursor == null) {
            return null;
        }
        boolean desc = isDescending(sort);
        BooleanExpression strict;
        BooleanExpression same;
        if (sort == OperationSpaceSort.NAME) {
            strict = schedule.title.gt(cursor.sortValue());
            same = schedule.title.eq(cursor.sortValue());
        } else {
            LocalDateTime key = LocalDateTime.parse(cursor.sortValue());
            strict = desc ? schedule.startAt.lt(key) : schedule.startAt.gt(key);
            same = schedule.startAt.eq(key);
        }
        BooleanExpression idAfter = desc ? schedule.id.lt(cursor.id()) : schedule.id.gt(cursor.id());
        return afterCursor(strict, same, idAfter, SCHEDULE_TYPE_ORDER, cursor, desc);
    }

    // 회의의 날짜 키는 회의 날짜 0시다. cursor가 일정에서 온 시각(예: 17시)이면 같은 날짜의 회의는
    // 키가 cursor보다 작고, 0시 정각이면 키가 같다 — 이 차이를 날짜 비교로 옮긴다
    private BooleanExpression meetingCursor(OperationSpaceSort sort, OperationSpaceCursor cursor) {
        if (cursor == null) {
            return null;
        }
        boolean desc = isDescending(sort);
        BooleanExpression strict;
        BooleanExpression same;
        if (sort == OperationSpaceSort.NAME) {
            strict = meeting.title.gt(cursor.sortValue());
            same = meeting.title.eq(cursor.sortValue());
        } else {
            LocalDateTime key = LocalDateTime.parse(cursor.sortValue());
            boolean midnight = key.toLocalTime().equals(LocalTime.MIDNIGHT);
            if (desc) {
                strict = midnight ? meeting.meetingDate.lt(key.toLocalDate()) : meeting.meetingDate.loe(key.toLocalDate());
            } else {
                strict = meeting.meetingDate.gt(key.toLocalDate());
            }
            same = midnight ? meeting.meetingDate.eq(key.toLocalDate()) : null;
        }
        BooleanExpression idAfter = desc ? meeting.id.lt(cursor.id()) : meeting.id.gt(cursor.id());
        return afterCursor(strict, same, idAfter, MEETING_TYPE_ORDER, cursor, desc);
    }

    // 키가 같은 행은 유형 순서로, 유형까지 같으면 id로 cursor 앞뒤를 가른다
    private BooleanExpression afterCursor(BooleanExpression strict, BooleanExpression same, BooleanExpression idAfter,
                                          int typeOrder, OperationSpaceCursor cursor, boolean desc) {
        if (same == null) {
            return strict;
        }
        if (typeOrder == cursor.typeOrder()) {
            return strict.or(same.and(idAfter));
        }
        boolean sameKeyComesAfter = desc ? typeOrder < cursor.typeOrder() : typeOrder > cursor.typeOrder();
        return sameKeyComesAfter ? strict.or(same) : strict;
    }

    public static boolean isDescending(OperationSpaceSort sort) {
        return sort == OperationSpaceSort.LATEST;
    }
}
