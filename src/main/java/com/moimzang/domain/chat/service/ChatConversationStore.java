package com.moimzang.domain.chat.service;

import com.moimzang.domain.chat.dto.response.UnpaidLookupCard;
import com.moimzang.domain.chat.entity.AiChatRoom;
import com.moimzang.domain.chat.entity.AiMessage;
import com.moimzang.domain.chat.enums.MessageRole;
import com.moimzang.domain.chat.enums.NavigationType;
import com.moimzang.domain.chat.exception.ChatErrorCode;
import com.moimzang.domain.chat.repository.AiChatRoomRepository;
import com.moimzang.domain.chat.repository.AiMessageRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.ai.AiChatMessage;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 전송의 DB 작업만 담당한다.
 *
 * ChatService에서 같은 클래스의 메서드를 호출하면 프록시를 거치지 않아 @Transactional이 적용되지
 * 않는다 — 제목 변경(dirty checking)이 조용히 무시되고 LAZY 연관 접근도 깨진다. 그래서 트랜잭션이
 * 필요한 구간을 별도 빈으로 떼어, AI 호출(수 초)은 트랜잭션 밖에 두고 저장만 짧게 끊는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatConversationStore {

    private final AiChatRoomRepository aiChatRoomRepository;
    private final AiMessageRepository aiMessageRepository;

    /**
     * 질문을 저장하고 AI에 넘길 대화 맥락을 만든다.
     *
     * groupTerm·group이 LAZY라 시스템 프롬프트 조립을 트랜잭션 안에서 끝내야 한다.
     */
    @Transactional
    public ConversationContext openTurn(Long chatRoomId, String question, int historySize) {
        AiChatRoom chatRoom = aiChatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> {
                    log.warn("[AI 메시지 전송] 존재하지 않는 대화방입니다. chatRoomId = {}", chatRoomId);
                    return new CustomException(ChatErrorCode.CHAT_404_NOT_FOUND);
                });

        boolean needsTitle = chatRoom.hasNoTitle();
        // 이번 질문을 저장하기 전에 읽어야 같은 질문이 히스토리에 중복해서 들어가지 않는다
        List<AiMessage> history = aiMessageRepository.findRecentHistory(chatRoomId, historySize);
        aiMessageRepository.save(AiMessage.ofUser(chatRoom, question));

        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(AiChatMessage.system(ChatSystemPrompt.build(chatRoom.getGroupTerm())));
        // 최신순으로 조회했으므로 대화 순서대로 뒤집어 넣는다
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage message = history.get(i);
            messages.add(message.getRole() == MessageRole.USER
                    ? AiChatMessage.user(message.getContent())
                    : AiChatMessage.assistant(message.getContent()));
        }
        messages.add(AiChatMessage.user(question));

        return new ConversationContext(chatRoom.getGroupTerm().getId(), needsTitle, messages);
    }

    // AI 답변과 이동 대상·카드를 저장하고, 목록 정렬에 쓰는 활동 시각을 갱신한다
    @Transactional
    public AiMessage saveAnswer(Long chatRoomId, String content, Navigation navigation,
                                 String cardType, String contentJson, String title) {
        AiChatRoom chatRoom = aiChatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_404_NOT_FOUND));

        if (title != null) {
            chatRoom.applyTitle(title);
        }

        AiMessage saved = aiMessageRepository.save(AiMessage.ofAssistant(
                chatRoom, content, navigation.type(), navigation.targetId(), cardType, contentJson));

        aiChatRoomRepository.touchUpdatedAt(chatRoomId, LocalDateTime.now());

        return saved;
    }

    public record ConversationContext(Long termId, boolean needsTitle, List<AiChatMessage> messages) {}

    // 카드 재료를 한 덩어리로 넘기기 위한 타입. cardType이 null이면 카드 없이 텍스트로만 답한다
    public record Card(Schedule schedule, String cardType, UnpaidLookupCard card, String contentJson) {

        public static Card none() {
            return new Card(null, null, null, null);
        }
    }

    // 이동 버튼이 가리킬 화면. type이 null이면 버튼을 띄우지 않는다
    public record Navigation(NavigationType type, Long targetId) {

        public static Navigation none() {
            return new Navigation(null, null);
        }
    }
}
