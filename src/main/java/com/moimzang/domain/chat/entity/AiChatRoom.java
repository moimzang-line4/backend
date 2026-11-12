package com.moimzang.domain.chat.entity;

import com.moimzang.domain.group.entity.GroupTerm;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// AI 채팅방 entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "ai_chat_room")
public class AiChatRoom extends BaseEntity {

    // 채팅방 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 채팅방 제목
    @Column(name = "title", length = 100)
    private String title;

    // 소속 기수(기수당 여러 개 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_term_id", nullable = false)
    private GroupTerm groupTerm;

    public boolean hasNoTitle() {
        return title == null || title.isBlank();
    }

    public void applyTitle(String title) {
        this.title = title;
    }

    // title은 선택이라 비어 있을 수 있다 — 첫 질문을 보낼 때 그 질문으로 지은 제목으로 채운다
    public static AiChatRoom create(GroupTerm groupTerm, String title) {
        return AiChatRoom.builder()
                .groupTerm(groupTerm)
                .title(title)
                .build();
    }
}
