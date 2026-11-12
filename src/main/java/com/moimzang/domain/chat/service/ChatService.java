package com.moimzang.domain.chat.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.moimzang.domain.chat.dto.request.ChatMessageSendRequest;
import com.moimzang.domain.chat.dto.request.ChatRoomCreateRequest;
import com.moimzang.domain.chat.dto.response.ChatMessageListResponse;
import com.moimzang.domain.chat.dto.response.ChatMessageResponse;
import com.moimzang.domain.chat.dto.response.ChatNavigationResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomCreateResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomListResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.moimzang.domain.chat.dto.response.UnpaidLookupCard;
import com.moimzang.domain.chat.entity.AiChatRoom;
import com.moimzang.domain.chat.entity.AiMessage;
import com.moimzang.domain.chat.enums.MessageRole;
import com.moimzang.domain.chat.enums.NavigationType;
import com.moimzang.domain.chat.exception.ChatErrorCode;
import com.moimzang.domain.chat.mapper.ChatMapper;
import com.moimzang.domain.chat.repository.AiChatRoomRepository;
import com.moimzang.domain.chat.repository.AiMessageRepository;
import com.moimzang.domain.chat.service.tool.AttendanceStatusTool;
import com.moimzang.domain.chat.service.tool.BudgetSummaryTool;
import com.moimzang.domain.chat.service.tool.FileSearchTool;
import com.moimzang.domain.chat.service.tool.MeetingDetailTool;
import com.moimzang.domain.chat.service.tool.NoteSearchTool;
import com.moimzang.domain.chat.service.tool.ScheduleDetailTool;
import com.moimzang.domain.chat.service.tool.SettlementStatusTool;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.ai.AiClientException;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    // 프롬프트에 싣는 과거 대화 수. 늘리면 맥락은 좋아지지만 매 요청 토큰이 비례해 늘어난다
    private static final int HISTORY_SIZE = 20;

    private final AiChatRoomRepository aiChatRoomRepository;
    private final AiMessageRepository aiMessageRepository;
    private final GroupTermRepository groupTermRepository;
    private final ScheduleRepository scheduleRepository;
    private final ChatMapper chatMapper;
    private final ChatConversationStore conversationStore;
    private final ChatToolLoop chatToolLoop;
    private final ChatRoomTitleGenerator titleGenerator;
    private final UnpaidLookupCardFactory unpaidLookupCardFactory;
    private final ObjectMapper objectMapper;

    // 기수에 새 AI 대화방을 만든다. 대화방은 기수당 여러 개를 유지한다
    @Transactional
    public ChatRoomCreateResponse createChatRoom(Long termId, ChatRoomCreateRequest request) {
        log.info("[AI 대화방 생성] 생성을 시작합니다. termId = {}", termId);

        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[AI 대화방 생성] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(ChatErrorCode.CHAT_404_GROUP_TERM_NOT_FOUND);
                });

        String title = request == null ? null : request.getTitle();
        AiChatRoom chatRoom = aiChatRoomRepository.save(AiChatRoom.create(groupTerm, title));

        log.info("[AI 대화방 생성] 생성을 완료했습니다. termId = {}, chatRoomId = {}", termId, chatRoom.getId());

        return ChatRoomCreateResponse.builder()
                .chatRoomId(chatRoom.getId())
                .build();
    }

    // 기수의 대화방 목록을 최근 활동 순으로 cursor 페이지네이션 조회한다
    public ChatRoomListResponse listChatRooms(Long termId, String cursor, Integer size) {
        log.info("[AI 대화방 목록 조회] 조회를 시작합니다. termId = {}, cursor = {}", termId, cursor);

        groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[AI 대화방 목록 조회] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(ChatErrorCode.CHAT_404_GROUP_TERM_NOT_FOUND);
                });

        int limit = CursorPageConstants.resolveSize(size);

        // cursor 디코딩 — sortValue는 updatedAt을 ISO-8601 문자열로 직렬화한 것이다
        LocalDateTime cursorUpdatedAt = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorUpdatedAt = LocalDateTime.parse(decoded.getSortValue());
                cursorId = decoded.getId();
            } catch (RuntimeException e) {
                log.warn("[AI 대화방 목록 조회] cursor 형식이 올바르지 않습니다. termId = {}, cursor = {}", termId, cursor);
                throw new CustomException(ChatErrorCode.CHAT_400_INVALID_CURSOR);
            }
        }

        // size + 1건을 조회해서 그중 마지막 1건이 있으면 "다음 페이지가 있다"는 뜻이다
        List<AiChatRoom> fetched = aiChatRoomRepository.search(termId, cursorUpdatedAt, cursorId, limit + 1);

        boolean hasNext = fetched.size() > limit;
        List<AiChatRoom> page = hasNext ? fetched.subList(0, limit) : fetched;

        List<ChatRoomSummaryResponse> chatRooms = chatMapper.toChatRoomSummaryResponses(page);
        String nextCursor = hasNext ? buildCursor(page.get(page.size() - 1)) : null;

        log.info("[AI 대화방 목록 조회] 조회를 완료했습니다. termId = {}, count = {}, hasNext = {}",
                termId, chatRooms.size(), hasNext);

        return ChatRoomListResponse.builder()
                .chatRooms(chatRooms)
                .nextCursor(nextCursor)
                .build();
    }

    // 마지막 대화방을 기준으로 다음 페이지 cursor를 만든다
    private String buildCursor(AiChatRoom last) {
        return CursorCodec.encode(last.getUpdatedAt().toString(), last.getId());
    }

    /**
     * 질문을 받아 AI 답변을 만들고 대화에 남긴다.
     *
     * AI 호출은 도구를 여러 번 오가느라 수 초가 걸린다. 클래스 레벨 @Transactional이 걸려 있으면
     * 그동안 DB 커넥션을 잡고 있게 되므로 이 메서드만 트랜잭션에서 뺀다 —
     * DB 작업은 ChatConversationStore가 짧은 트랜잭션으로 처리한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatMessageResponse sendMessage(Long chatRoomId, ChatMessageSendRequest request) {
        // 컨트롤러의 @NotBlank가 먼저 걸러내지만, 서비스를 직접 호출하는 경로를 위한 방어다
        String question = request.getContent() == null ? null : request.getContent().trim();
        if (question == null || question.isEmpty()) {
            log.warn("[AI 메시지 전송] 질문이 비어 있습니다. chatRoomId = {}", chatRoomId);
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }

        log.info("[AI 메시지 전송] 전송을 시작합니다. chatRoomId = {}", chatRoomId);

        ChatConversationStore.ConversationContext context =
                conversationStore.openTurn(chatRoomId, question, HISTORY_SIZE);

        ChatToolLoop.Outcome outcome;
        try {
            outcome = chatToolLoop.run(context.termId(), context.messages());
        } catch (AiClientException | ChatToolLoopExceededException e) {
            log.warn("[AI 메시지 전송] AI 응답 생성에 실패했습니다. chatRoomId = {}", chatRoomId, e);
            throw new CustomException(ChatErrorCode.CHAT_502_AI_FAILED);
        }

        ChatConversationStore.Card card = buildCard(outcome);
        ChatConversationStore.Navigation navigation = buildNavigation(outcome);
        // 제목 생성 실패는 답변을 막지 않는다 — 생성기가 질문 원문으로 대체해 돌려준다
        String title = context.needsTitle() ? titleGenerator.generate(question) : null;

        AiMessage saved = conversationStore.saveAnswer(
                chatRoomId, outcome.content(), navigation, card.cardType(), card.contentJson(), title);

        log.info("[AI 메시지 전송] 전송을 완료했습니다. chatRoomId = {}, messageId = {}, cardType = {}, navigation = {}",
                chatRoomId, saved.getId(), card.cardType(), navigation.type());

        return ChatMessageResponse.builder()
                .messageId(saved.getId())
                .role(MessageRole.ASSISTANT.name())
                .content(outcome.content())
                .navigation(toNavigationResponse(navigation.type(), navigation.targetId()))
                .cardType(card.cardType())
                .card(card.card() == null ? null : unpaidLookupCardFactory.toJsonNode(card.card()))
                .createdAt(saved.getCreatedAt())
                .build();
    }

    // 대화방의 메시지를 대화 순서대로 cursor 페이지네이션 조회한다
    public ChatMessageListResponse listMessages(Long chatRoomId, String cursor, Integer size) {
        log.info("[AI 메시지 목록 조회] 조회를 시작합니다. chatRoomId = {}, cursor = {}", chatRoomId, cursor);

        if (!aiChatRoomRepository.existsById(chatRoomId)) {
            log.warn("[AI 메시지 목록 조회] 존재하지 않는 대화방입니다. chatRoomId = {}", chatRoomId);
            throw new CustomException(ChatErrorCode.CHAT_404_NOT_FOUND);
        }

        int limit = CursorPageConstants.resolveSize(size);

        // cursor 디코딩 — sortValue는 createdAt을 ISO-8601 문자열로 직렬화한 것이다
        LocalDateTime cursorCreatedAt = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorCreatedAt = LocalDateTime.parse(decoded.getSortValue());
                cursorId = decoded.getId();
            } catch (RuntimeException e) {
                log.warn("[AI 메시지 목록 조회] cursor 형식이 올바르지 않습니다. chatRoomId = {}, cursor = {}",
                        chatRoomId, cursor);
                throw new CustomException(ChatErrorCode.CHAT_400_INVALID_CURSOR);
            }
        }

        // size + 1건을 조회해서 그중 마지막 1건이 있으면 "다음 페이지가 있다"는 뜻이다
        List<AiMessage> fetched = aiMessageRepository.search(chatRoomId, cursorCreatedAt, cursorId, limit + 1);

        boolean hasNext = fetched.size() > limit;
        List<AiMessage> page = hasNext ? fetched.subList(0, limit) : fetched;

        List<ChatMessageResponse> messages = page.stream().map(this::toMessageResponse).toList();
        AiMessage last = page.isEmpty() ? null : page.get(page.size() - 1);
        String nextCursor = hasNext
                ? CursorCodec.encode(last.getCreatedAt().toString(), last.getId())
                : null;

        log.info("[AI 메시지 목록 조회] 조회를 완료했습니다. chatRoomId = {}, count = {}, hasNext = {}",
                chatRoomId, messages.size(), hasNext);

        return ChatMessageListResponse.builder()
                .messages(messages)
                .nextCursor(nextCursor)
                .build();
    }

    /**
     * 답변 아래 '…로 이동' 버튼이 가리킬 화면을 정한다.
     *
     * 아래 순서대로 훑어 먼저 걸리는 하나를 쓴다 — 일정 검색 → 정산 조회처럼 여러 도구가 연쇄로
     * 돌기 때문에, 질문의 의도에 가장 가까운(더 구체적인) 도구가 앞에 오도록 늘어놓았다.
     * 검색 도구(search_schedules·search_meetings)는 결과가 여러 건일 수 있어 대상이 되지 않는다.
     */
    private static final List<NavigationRule> NAVIGATION_RULES = List.of(
            new NavigationRule(SettlementStatusTool.NAME, NavigationType.SETTLEMENT, "scheduleId"),
            new NavigationRule(AttendanceStatusTool.NAME, NavigationType.ATTENDANCE, "scheduleId"),
            new NavigationRule(MeetingDetailTool.NAME, NavigationType.MEETING, "meetingId"),
            new NavigationRule(NoteSearchTool.NAME, NavigationType.NOTE, "scheduleId"),
            new NavigationRule(ScheduleDetailTool.NAME, NavigationType.SCHEDULE, "scheduleId"),
            new NavigationRule(BudgetSummaryTool.NAME, NavigationType.BUDGET, null),
            new NavigationRule(FileSearchTool.NAME, NavigationType.FILE, null));

    private ChatConversationStore.Navigation buildNavigation(ChatToolLoop.Outcome outcome) {
        for (NavigationRule rule : NAVIGATION_RULES) {
            // 자료실·장부처럼 가리킬 대상이 없는 화면은 도구가 몇 번 불렸든 보낼 곳이 하나다
            if (rule.idField() == null) {
                if (outcome.hasRun(rule.toolName())) {
                    return new ChatConversationStore.Navigation(rule.type(), null);
                }
                continue;
            }

            // 같은 도구가 서로 다른 인자로 여러 번 불렸으면 어느 일정·회의로 보낼지 정할 수 없다
            ChatToolLoop.ToolRun run = outcome.singleRun(rule.toolName());
            if (run == null) {
                continue;
            }
            Long targetId = readLong(run.argumentsJson(), rule.idField());
            // 대상 ID가 필요한 화면인데 인자에 없으면(예: 일정을 안 고른 메모 검색) 버튼을 띄우지 않는다
            if (targetId != null) {
                return new ChatConversationStore.Navigation(rule.type(), targetId);
            }
        }
        return ChatConversationStore.Navigation.none();
    }

    private ChatNavigationResponse toNavigationResponse(NavigationType type, Long targetId) {
        return type == null ? null : new ChatNavigationResponse(type.name(), targetId);
    }

    private record NavigationRule(String toolName, NavigationType type, String idField) {}

    // 입금 현황 도구가 실행됐고 미입금자가 있을 때만 카드를 만든다.
    // 카드 포맷이 확정된 건 '미입금자 조회' 하나뿐이라 나머지 도구는 텍스트 답변만 한다
    private ChatConversationStore.Card buildCard(ChatToolLoop.Outcome outcome) {
        ChatToolLoop.ToolRun run = outcome.singleRun(SettlementStatusTool.NAME);
        if (run == null) {
            return ChatConversationStore.Card.none();
        }

        Long scheduleId = readLong(run.argumentsJson(), "scheduleId");
        if (scheduleId == null) {
            return ChatConversationStore.Card.none();
        }

        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return ChatConversationStore.Card.none();
        }

        UnpaidLookupCard card = unpaidLookupCardFactory.create(schedule, run.resultJson());
        if (card == null) {
            // 전원 납부 완료 등으로 보여줄 대상이 없으면 카드 없이 텍스트로만 답한다
            return ChatConversationStore.Card.none();
        }
        return new ChatConversationStore.Card(schedule, UnpaidLookupCardFactory.CARD_TYPE, card,
                unpaidLookupCardFactory.toJsonString(card));
    }

    // 모델이 만든 도구 인자 JSON에서 ID를 꺼낸다
    private Long readLong(String argumentsJson, String field) {
        try {
            JsonNode node = objectMapper.readTree(argumentsJson).get(field);
            return node == null || node.isNull() ? null : node.asLong();
        } catch (JacksonException e) {
            log.warn("[AI 메시지 전송] 도구 인자에서 {}를 읽지 못했습니다. arguments = {}", field, argumentsJson);
            return null;
        }
    }

    private ChatMessageResponse toMessageResponse(AiMessage message) {
        return ChatMessageResponse.builder()
                .messageId(message.getId())
                .role(message.getRole().name())
                .content(message.getContent())
                .navigation(toNavigationResponse(message.getNavigationType(), message.getNavigationTargetId()))
                .cardType(message.getCardType())
                .card(readCard(message.getContentJson()))
                .createdAt(message.getCreatedAt())
                .build();
    }

    // 카드는 전송 시점 스냅샷이라 저장된 JSON을 그대로 되살려 내려준다
    private JsonNode readCard(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(contentJson);
        } catch (JacksonException e) {
            log.warn("[AI 메시지 목록 조회] 저장된 카드 JSON을 읽지 못했습니다.", e);
            return null;
        }
    }
}
