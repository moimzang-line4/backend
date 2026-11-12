package com.moimzang.domain.chat.enums;

// AI 답변 아래 '…로 이동' 버튼이 가리키는 화면.
// 경로(URL)는 내려주지 않는다 — 라우팅 규칙은 화면을 가진 쪽이 정하고, 서버는 어떤 화면의
// 어떤 대상인지만 알려준다
public enum NavigationType {

    // 아래 넷은 일정 하위 운영 메뉴라 대상이 scheduleId다
    SETTLEMENT,
    ATTENDANCE,
    NOTE,
    SCHEDULE,

    // 회의록 상세. 대상은 meetingId다
    MEETING,

    // 기수 단위 화면이라 가리킬 대상 ID가 없다
    FILE,
    BUDGET
}
