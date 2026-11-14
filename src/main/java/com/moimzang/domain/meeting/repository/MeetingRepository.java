package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long>, MeetingRepositoryCustom {

    // 일정 삭제 시 그 일정에서 만든 회의록을 지운다. 일정과 무관한 회의록(schedule_id가 null)은 남는다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Meeting m where m.schedule.id = :scheduleId")
    void deleteAllByScheduleId(@Param("scheduleId") Long scheduleId);
}
