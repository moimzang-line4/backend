package com.moimzang.domain.meeting.entity;

import com.moimzang.domain.meeting.enums.AgendaStatus;
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

// 안건 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "meeting_agenda")
public class MeetingAgenda extends BaseEntity {

    // 안건 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 회의
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    // 안건 내용(100자 이하)
    @Column(name = "content", nullable = false, length = 100)
    private String content;

    // 논의 내용
    @Column(name = "discussion_note", columnDefinition = "TEXT")
    private String discussionNote;

    // 결정 사항
    @Column(name = "decision", columnDefinition = "TEXT")
    private String decision;

    // 안건 상태(대기/진행중/완료, 진행률 계산용)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AgendaStatus status;

    // 노출 순서
    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    // 생성 시점에는 아직 논의 전이라 discussionNote/decision 없이 대기 상태로 시작한다
    public static MeetingAgenda create(Meeting meeting, String content, int orderNo) {
        return MeetingAgenda.builder()
                .meeting(meeting)
                .content(content)
                .status(AgendaStatus.PENDING)
                .orderNo(orderNo)
                .build();
    }

    // 안건 내용을 부분 수정한다 — 인자가 null이면 해당 필드는 바꾸지 않는다.
    // '다음 안건'은 status=COMPLETED로, '중단하기'는 status 없이 호출된다
    public void updateContent(String content, String discussionNote, String decision, AgendaStatus status) {
        if (content != null) {
            this.content = content;
        }
        if (discussionNote != null) {
            this.discussionNote = discussionNote;
        }
        if (decision != null) {
            this.decision = decision;
        }
        if (status != null) {
            this.status = status;
        }
    }
}
