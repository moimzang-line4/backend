package com.moimzang.domain.schedule.entity;

import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.global.entity.BaseCreatedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 일정-안건 연결 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "schedule_agenda_link")
public class ScheduleAgendaLink extends BaseCreatedEntity {

    // 연결 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연결된 일정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    // 연결된 안건
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_agenda_id", nullable = false)
    private MeetingAgenda meetingAgenda;

    public static ScheduleAgendaLink create(Schedule schedule, MeetingAgenda meetingAgenda) {
        return ScheduleAgendaLink.builder()
                .schedule(schedule)
                .meetingAgenda(meetingAgenda)
                .build();
    }
}
