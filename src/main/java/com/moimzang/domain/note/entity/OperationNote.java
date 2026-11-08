package com.moimzang.domain.note.entity;

import com.moimzang.domain.note.enums.NoteType;
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

// 운영 메모 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "operation_note")
public class OperationNote extends BaseEntity {

    // 메모 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 일정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    // 메모 유형(일반메모/할일/결정사항/아이디어)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NoteType type;

    // 제목
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    // 담당자(자유 텍스트)
    @Column(name = "assignee", length = 50)
    private String assignee;

    // 내용(100자 이하)
    @Column(name = "content", nullable = false, length = 100)
    private String content;

    // 할 일 완료 여부(type이 할일인 경우만 사용)
    @Column(name = "is_todo_done")
    private Boolean isTodoDone;
}
