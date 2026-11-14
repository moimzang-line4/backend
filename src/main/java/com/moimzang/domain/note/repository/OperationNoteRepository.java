package com.moimzang.domain.note.repository;

import com.moimzang.domain.note.entity.OperationNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationNoteRepository
        extends JpaRepository<OperationNote, Long>, OperationNoteRepositoryCustom {

    // 일정 삭제 시 그 일정의 운영 메모를 함께 지운다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OperationNote n where n.schedule.id = :scheduleId")
    void deleteAllByScheduleId(@Param("scheduleId") Long scheduleId);
}
