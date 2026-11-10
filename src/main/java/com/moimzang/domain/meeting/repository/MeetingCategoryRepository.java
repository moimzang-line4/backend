package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.MeetingCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingCategoryRepository extends JpaRepository<MeetingCategory, Long> {
}
