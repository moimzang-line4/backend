package com.moimzang.domain.schedule.enums;

// 일정 색상. 일정 등록 화면의 색상 스와치 8개와 같은 순서다.
// 엔티티는 이 이름을 문자열로 저장하고, 등록·수정 요청에서만 이 enum으로 받아 8개 외의 값을 막는다
public enum ScheduleColor {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    TEAL,
    BLUE,
    PURPLE,
    PINK
}
