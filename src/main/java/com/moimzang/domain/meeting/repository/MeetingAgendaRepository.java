package com.moimzang.domain.meeting.repository;

import com.moimzang.domain.meeting.entity.MeetingAgenda;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingAgendaRepository extends JpaRepository<MeetingAgenda, Long> {
}
