package com.moimzang.domain.attendance.entity;

import com.moimzang.domain.attendance.enums.AttendeeSource;
import com.moimzang.domain.memberlist.entity.Member;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// 일정 참가자 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "schedule_attendee")
public class ScheduleAttendee extends BaseEntity {

    // 참가자 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 참가한 일정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    // 부원링크로 등록된 경우의 원본 부원(현장추가는 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    // 이름 스냅샷(명단 원본 변경과 무관)
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    // 학과·소속 스냅샷
    @Column(name = "affiliation", length = 100)
    private String affiliation;

    // 전화번호 뒤 4자리
    @Column(name = "phone_last4", length = 4)
    private String phoneLast4;

    // 등록 경로(부원링크/현장추가)
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AttendeeSource source;

    // 현장에서 직접 추가하는 참가자를 만든다(부원 원본과 무관해 member는 null)
    public static ScheduleAttendee createOnSite(Schedule schedule, String name, String affiliation, String phoneLast4) {
        return ScheduleAttendee.builder()
                .schedule(schedule)
                .name(name)
                .affiliation(affiliation)
                .phoneLast4(phoneLast4)
                .source(AttendeeSource.ON_SITE)
                .build();
    }

    // 명단방의 부원을 불러와 참가자로 만든다. 이름·소속·전화번호는 그 시점 부원 정보의
    // 스냅샷이고, member 연결을 남겨 중복 불러오기를 막는 기준으로 쓴다
    public static ScheduleAttendee createFromMember(Schedule schedule, Member member, String name, String affiliation, String phoneLast4) {
        return ScheduleAttendee.builder()
                .schedule(schedule)
                .member(member)
                .name(name)
                .affiliation(affiliation)
                .phoneLast4(phoneLast4)
                .source(AttendeeSource.LINK)
                .build();
    }
}
