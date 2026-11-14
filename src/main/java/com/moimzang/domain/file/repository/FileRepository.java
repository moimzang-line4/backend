package com.moimzang.domain.file.repository;

import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileRepository extends JpaRepository<File, Long>, FileRepositoryCustom {

    // 기수에서 해당 분류의 살아있는 파일 1건을 찾는다(장부 교체 판단용)
    @Query("""
            select f from File f
            where f.groupTerm.id = :groupTermId
              and f.category = :category
              and f.deletedAt is null
            """)
    Optional<File> findActiveFile(
            @Param("groupTermId") Long groupTermId,
            @Param("category") FileCategory category
    );

    // 삭제되지 않은 파일을 ID로 찾는다
    @Query("""
            select f from File f
            where f.id = :fileId
              and f.deletedAt is null
            """)
    Optional<File> findActiveById(@Param("fileId") Long fileId);

    // 일정 삭제 시 그 일정에서 올린 자료 행을 지운다. 이미 소프트 삭제된 행도 schedule_id FK를 쥐고 있어 함께 지운다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from File f where f.schedule.id = :scheduleId")
    void deleteAllByScheduleId(@Param("scheduleId") Long scheduleId);
}
