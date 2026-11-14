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

    // 할 일은 미완료로 시작하고, 다른 유형은 완료 여부를 쓰지 않아 비워둔다
    public static OperationNote create(Schedule schedule, NoteType type, String title, String assignee,
                                       String content) {
        return OperationNote.builder()
                .schedule(schedule)
                .type(type)
                .title(title)
                .assignee(assignee)
                .content(content)
                .isTodoDone(type == NoteType.TODO ? Boolean.FALSE : null)
                .build();
    }

    // 메모를 부분 수정한다 — 인자가 null인 필드는 바꾸지 않는다.
    // 담당자는 선택 항목이라 빈 문자열을 보내면 담당자를 지운다
    public void update(NoteType type, String title, String assignee, String content) {
        if (type != null) {
            changeType(type);
        }
        if (title != null) {
            this.title = title;
        }
        if (assignee != null) {
            this.assignee = assignee.isEmpty() ? null : assignee;
        }
        if (content != null) {
            this.content = content;
        }
    }

    public boolean isTodo() {
        return this.type == NoteType.TODO;
    }

    // 할 일의 완료 여부를 바꾼다. 할 일인지 확인은 호출하는 쪽에서 먼저 한다
    public void changeTodoDone(boolean isTodoDone) {
        this.isTodoDone = isTodoDone;
    }

    // 다른 유형에서 할 일로 바뀌면 미완료로 시작하고, 할 일에서 벗어나면 완료 여부를 비운다.
    // 할 일을 할 일로 다시 보내는 경우는 기존 완료 여부를 그대로 둔다
    private void changeType(NoteType newType) {
        if (newType == NoteType.TODO && this.type != NoteType.TODO) {
            this.isTodoDone = Boolean.FALSE;
        } else if (newType != NoteType.TODO) {
            this.isTodoDone = null;
        }
        this.type = newType;
    }
}
