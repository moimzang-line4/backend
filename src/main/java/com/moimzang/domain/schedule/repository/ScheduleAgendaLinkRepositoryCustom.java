package com.moimzang.domain.schedule.repository;

import com.moimzang.domain.schedule.entity.ScheduleAgendaLink;
import java.util.List;

public interface ScheduleAgendaLinkRepositoryCustom {

    // 일정 상세 '회의록 & 안건'의 안건 카드용 — 연결된 안건을 회의 정보까지 함께 조회한다
    List<ScheduleAgendaLink> findLinkedAgendas(Long scheduleId);

    // 일정에 연결된 안건을 회의 정보까지 함께 조회한다(AI 비서 조회용, 읽기 전용)
    List<ScheduleAgendaLink> findByScheduleForAssistant(Long scheduleId);
}
