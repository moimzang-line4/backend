package com.moimzang.domain.meeting.entity;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.meeting.enums.MeetingStatus;
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

    // 회의 카테고리(일정 카테고리와 별도 운영)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_category_id", nullable = false)
    private MeetingCategory meetingCategory;

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

    // 생성 직후에는 안건을 논의하지 않은 상태이므로 항상 진행중으로 시작한다
    public static Meeting create(GroupTerm groupTerm, MeetingCategory meetingCategory, String title,
                                  LocalDate meetingDate, String attendees, String listSummary) {
        return Meeting.builder()
                .groupTerm(groupTerm)
                .meetingCategory(meetingCategory)
                .title(title)
                .meetingDate(meetingDate)
                .attendees(attendees)
                .listSummary(listSummary)
                .status(MeetingStatus.IN_PROGRESS)
                .build();
    }

    // 회의 정보를 부분 수정한다 — 인자가 null인 필드는 바꾸지 않는다
    public void updateInfo(MeetingCategory meetingCategory, String title, LocalDate meetingDate,
                           String attendees, String listSummary) {
        if (meetingCategory != null) {
            this.meetingCategory = meetingCategory;
        }
        if (title != null) {
            this.title = title;
        }
        if (meetingDate != null) {
            this.meetingDate = meetingDate;
        }
        if (attendees != null) {
            this.attendees = attendees;
        }
        if (listSummary != null) {
            this.listSummary = listSummary;
        }
    }

    // 회의가 이미 종료됐는지 확인한다
    public boolean isEnded() {
        return status == MeetingStatus.COMPLETED;
    }

    // 회의를 종료한다 — 안건이 전부 완료되지 않았어도 허용한다
    public void end() {
        this.status = MeetingStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
    }

    // AI가 생성한 요약으로 덮어쓴다 — "다시 요약하기"도 이 메서드를 그대로 쓴다
    public void applySummary(String summaryContent) {
        this.summaryContent = summaryContent;
        this.summaryGeneratedAt = LocalDateTime.now();
    }

    // 사용자가 요약을 직접 고친 내용을 저장한다 — 생성 시각은 그대로 둔다
    public void updateSummaryContent(String summaryContent) {
        this.summaryContent = summaryContent;
    }
}
