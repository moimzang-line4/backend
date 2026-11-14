package com.moimzang.domain.attendance.repository;

import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleAttendeeRepository extends JpaRepository<ScheduleAttendee, Long> {

    // 새 차수 생성 시 기존 참가자 전원을 미출석으로 포함시키기 위한 조회
    List<ScheduleAttendee> findByScheduleId(Long scheduleId);

    // 현장 인원 추가(API-030) 동명이인 체크 — 같은 일정 전체 기준
    boolean existsByScheduleIdAndName(Long scheduleId, String name);

    // 일정의 참가자를 이름순으로 조회한다(AI 비서 조회용, 읽기 전용)
    List<ScheduleAttendee> findByScheduleIdOrderByNameAsc(Long scheduleId);

    // 일정 삭제 시 그 일정의 참가자를 지운다 — 출석·정산 대상을 먼저 지운 뒤 호출한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ScheduleAttendee a where a.schedule.id = :scheduleId")
    void deleteAllByScheduleId(@Param("scheduleId") Long scheduleId);

    // 명단방 삭제 시 그 명단방 부원으로 등록된 일정 참가자를 지운다 — 출석·정산 대상을 먼저 지운 뒤 호출한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ScheduleAttendee a
            where a.member.id in (select m.id from Member m where m.memberList.id = :memberListId)
            """)
    void deleteAllByMemberListId(@Param("memberListId") Long memberListId);
}
