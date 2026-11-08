package com.moimzang.domain.meeting.entity;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 회의 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "meeting")
public class Meeting extends BaseEntity {

    // 회의 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 기수
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_term_id", nullable = false)
    private GroupTerm groupTerm;

    // 일정 카테고리와 공유하는 카테고리
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_category_id", nullable = false)
    private ScheduleCategory scheduleCategory;

    // 회의 제목
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    // 회의 날짜
    @Column(name = "meeting_date")
    private LocalDate meetingDate;

    // 참석자(자유 텍스트)
    @Column(name = "attendees", length = 500)
    private String attendees;

    // AI 요약본(원본 안건과 별도, 직접 수정·재요약 가능)
    @Column(name = "summary_content", columnDefinition = "TEXT")
    private String summaryContent;

    // AI 요약 생성 일시
    @Column(name = "summary_generated_at")
    private LocalDateTime summaryGeneratedAt;

    // 회의 상태(진행중/완료)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MeetingStatus status;

    // 회의 종료 일시
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // 회의 목록 카드에 표시되는 한 줄 설명
    @Column(name = "list_summary", length = 100)
    private String listSummary;
}
