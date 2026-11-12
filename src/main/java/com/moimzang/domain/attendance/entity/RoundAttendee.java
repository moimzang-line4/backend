package com.moimzang.domain.attendance.entity;

import com.moimzang.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 차수 참석자 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "round_attendee")
public class RoundAttendee extends BaseEntity {

    // 출석기록 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 대상 차수
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_round_id", nullable = false)
    private ScheduleRound scheduleRound;

    // 대상 참가자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_attendee_id", nullable = false)
    private ScheduleAttendee scheduleAttendee;

    // 출석 완료 여부
    @Column(name = "is_checked_in", nullable = false)
    private Boolean isCheckedIn;

    // 출석 처리 일시
    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    // 차수에 참가자를 미출석 상태로 연결한다. 새 차수 생성 시 기존 참가자 전원을, 현장 추가 시
    // 그 차수 하나를 이 상태로 시작한다
    public static RoundAttendee create(ScheduleRound scheduleRound, ScheduleAttendee scheduleAttendee) {
        return RoundAttendee.builder()
                .scheduleRound(scheduleRound)
                .scheduleAttendee(scheduleAttendee)
                .isCheckedIn(false)
                .build();
    }
}
