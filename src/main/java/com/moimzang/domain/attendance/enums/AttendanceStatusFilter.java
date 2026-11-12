package com.moimzang.domain.attendance.enums;

// 차수 출석 현황 조회(API-029)용 상태 필터 — round_attendee.is_checked_in은 DB엔 boolean뿐이라
// ALL까지 포함하는 이 필터는 별도 enum으로 분리했다
public enum AttendanceStatusFilter {
    UNCHECKED,
    CHECKED,
    ALL;

    public Boolean toIsCheckedInOrNull() {
        if (this == ALL) {
            return null;
        }
        return this == CHECKED;
    }
}