package com.moimzang.domain.chat.entity;

import com.moimzang.domain.chat.enums.MessageRole;
import com.moimzang.domain.chat.enums.NavigationType;
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

    // 답변 아래 '…로 이동' 버튼이 가리키는 화면(없으면 버튼을 띄우지 않는다)
    @Enumerated(EnumType.STRING)
    @Column(name = "navigation_type", length = 20)
    private NavigationType navigationType;

    // 이동 대상 ID. navigationType에 따라 일정 ID일 수도 회의 ID일 수도 있고,
    // 기수 단위 화면(자료실·장부)이면 비어 있다
    @Column(name = "navigation_target_id")
    private Long navigationTargetId;

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

    // 사용자가 보낸 질문
    public static AiMessage ofUser(AiChatRoom chatRoom, String content) {
        return AiMessage.builder()
                .aiChatRoom(chatRoom)
                .role(MessageRole.USER)
                .content(content)
                .build();
    }

    // AI 답변. 이동할 화면이나 결과 카드가 없는 일반 답변이면 navigation·cardType·contentJson이 전부 null이다
    public static AiMessage ofAssistant(AiChatRoom chatRoom, String content, NavigationType navigationType,
                                         Long navigationTargetId, String cardType, String contentJson) {
        return AiMessage.builder()
                .aiChatRoom(chatRoom)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .navigationType(navigationType)
                .navigationTargetId(navigationTargetId)
                .cardType(cardType)
                .contentJson(contentJson)
                .build();
    }
}
