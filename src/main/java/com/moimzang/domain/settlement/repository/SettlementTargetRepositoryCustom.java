package com.moimzang.domain.settlement.repository;

import com.moimzang.domain.settlement.entity.SettlementTarget;
import java.time.LocalDateTime;
import java.util.List;

public interface SettlementTargetRepositoryCustom {

    // 일정의 정산 대상을 참가자·차수 정보와 함께 조회한다(AI 비서 조회용, 읽기 전용).
    // roundNo가 null이면 그 일정의 모든 차수를 본다
    List<SettlementTarget> findByScheduleForAssistant(Long scheduleId, Integer roundNo);

    // 정산 명단 조회(API-037)용 — limit건(size+1)을 cursor 다음부터 createdAt 오름차순으로
    // 가져온다. scheduleAttendee를 fetch join해서 이름·소속·전화번호를 한 번에 로딩한다.
    // cursorId가 null이면 첫 페이지
    List<SettlementTarget> search(Long roundId, String keyword, LocalDateTime cursorCreatedAt, Long cursorId, int limit);
}
