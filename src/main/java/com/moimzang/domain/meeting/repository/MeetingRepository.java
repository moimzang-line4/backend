package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, Long>, MeetingRepositoryCustom {
}
