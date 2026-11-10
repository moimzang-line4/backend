package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.MeetingCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingCategoryRepository extends JpaRepository<MeetingCategory, Long> {

    // 기수의 회의 카테고리 목록을 생성 순서대로 조회한다
    List<MeetingCategory> findByGroupTermIdOrderByIdAsc(Long groupTermId);
}
