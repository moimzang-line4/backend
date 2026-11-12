package com.moimzang.domain.settlement.repository;

import com.moimzang.domain.settlement.entity.SettlementTarget;
import java.util.List;

public interface SettlementTargetRepositoryCustom {

    // 일정의 정산 대상을 참가자·차수 정보와 함께 조회한다(AI 비서 조회용, 읽기 전용).
    // roundNo가 null이면 그 일정의 모든 차수를 본다
    List<SettlementTarget> findByScheduleForAssistant(Long scheduleId, Integer roundNo);
}
