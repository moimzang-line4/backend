package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.MeetingCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingCategoryRepository extends JpaRepository<MeetingCategory, Long> {

    // 기수의 회의 카테고리 목록을 생성 순서대로 조회한다
    List<MeetingCategory> findByGroupTermIdOrderByIdAsc(Long groupTermId);

    // 카테고리를 기수 조건과 함께 조회한다 — 다른 기수의 카테고리가 붙어 기수 경계를 넘는 걸 막는다
    Optional<MeetingCategory> findByIdAndGroupTermId(Long id, Long groupTermId);
}
