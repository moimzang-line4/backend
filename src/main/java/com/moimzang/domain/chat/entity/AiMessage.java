package com.moimzang.domain.chat.entity;

import com.moimzang.domain.chat.enums.MessageRole;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.entity.BaseCreatedEntity;
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

// AI 메시지 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "ai_message")
public class AiMessage extends BaseCreatedEntity {

    // 메시지 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 채팅방
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_chat_room_id", nullable = false)
    private AiChatRoom aiChatRoom;

    // 질의 대상 일정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_schedule_id")
    private Schedule targetSchedule;

    // 메시지 역할(사용자 질문/AI 응답)
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MessageRole role;

    // 메시지 내용
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 결과 카드 유형(예: 미입금자 조회)
    @Column(name = "card_type", length = 50)
    private String cardType;

    // 결과 카드 내용 스냅샷(JSON)
    @Column(name = "content_json", columnDefinition = "JSON")
    private String contentJson;
}
