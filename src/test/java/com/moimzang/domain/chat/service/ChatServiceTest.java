package com.moimzang.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.chat.dto.request.ChatMessageSendRequest;
import com.moimzang.domain.chat.dto.request.ChatRoomCreateRequest;
import com.moimzang.domain.chat.dto.response.ChatMessageListResponse;
import com.moimzang.domain.chat.dto.response.ChatMessageResponse;
import com.moimzang.domain.chat.dto.response.ChatNavigationResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomCreateResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomListResponse;
import com.moimzang.domain.chat.entity.AiChatRoom;
import com.moimzang.domain.chat.entity.AiMessage;
import com.moimzang.domain.chat.enums.MessageRole;
import com.moimzang.domain.chat.exception.ChatErrorCode;
import com.moimzang.domain.chat.mapper.ChatMapper;
import com.moimzang.domain.chat.repository.AiChatRoomRepository;
import com.moimzang.domain.chat.repository.AiMessageRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.ai.AiClientException;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private AiChatRoomRepository aiChatRoomRepository;

    @Mock
    private AiMessageRepository aiMessageRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ChatConversationStore conversationStore;

    @Mock
    private ChatToolLoop chatToolLoop;

    @Mock
    private ChatRoomTitleGenerator titleGenerator;

    // mapper는 mock하지 않는다 — 매핑 누락을 테스트에서도 실제 생성 코드로 검증한다
    private final ChatMapper chatMapper = Mappers.getMapper(ChatMapper.class);

    // 순수 변환 로직이라 mock하지 않는다
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UnpaidLookupCardFactory unpaidLookupCardFactory = new UnpaidLookupCardFactory(objectMapper);

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(aiChatRoomRepository, aiMessageRepository, groupTermRepository,
                scheduleRepository, chatMapper, conversationStore, chatToolLoop, titleGenerator,
                unpaidLookupCardFactory, objectMapper);
    }

    @Test
    void createChatRoom_정상_요청이면_대화방을_생성한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        ChatRoomCreateRequest request = ChatRoomCreateRequest.builder()
                .title("가을공연 미입금자 확인")
                .build();

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(aiChatRoomRepository.save(any(AiChatRoom.class))).willAnswer(invocation -> {
            AiChatRoom saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 8L);
            return saved;
        });

        ChatRoomCreateResponse response = chatService.createChatRoom(1L, request);

        assertThat(response.getChatRoomId()).isEqualTo(8L);
    }

    @Test
    void createChatRoom_바디가_없어도_제목_없는_대화방을_생성한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(aiChatRoomRepository.save(any(AiChatRoom.class))).willAnswer(invocation -> {
            AiChatRoom saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });

        ChatRoomCreateResponse response = chatService.createChatRoom(1L, null);

        assertThat(response.getChatRoomId()).isEqualTo(9L);
    }

    @Test
    void createChatRoom_기수가_없으면_CHAT_404_GROUP_TERM_NOT_FOUND를_던진다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.createChatRoom(1L, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void listChatRooms_조회된_대화방이_size_이하면_nextCursor가_null이다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(aiChatRoomRepository.search(eq(1L), eq(null), eq(null), eq(21)))
                .willReturn(List.of(chatRoom(1L, "첫 질문", LocalDateTime.of(2026, 11, 13, 21, 1))));

        ChatRoomListResponse response = chatService.listChatRooms(1L, null, null);

        assertThat(response.getChatRooms()).hasSize(1);
        assertThat(response.getChatRooms().get(0).getChatRoomId()).isEqualTo(1L);
        assertThat(response.getChatRooms().get(0).getTitle()).isEqualTo("첫 질문");
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listChatRooms_size보다_1건_더_조회되면_마지막_방_기준_nextCursor를_내려준다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        LocalDateTime base = LocalDateTime.of(2026, 11, 13, 21, 1);
        // size=2로 요청하면 서비스는 3건을 조회한다 — 3번째가 있으면 다음 페이지가 있다는 뜻
        List<AiChatRoom> fetched = IntStream.rangeClosed(1, 3)
                .mapToObj(i -> chatRoom((long) i, "방" + i, base.minusMinutes(i)))
                .toList();

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(aiChatRoomRepository.search(eq(1L), eq(null), eq(null), eq(3))).willReturn(fetched);

        ChatRoomListResponse response = chatService.listChatRooms(1L, null, 2);

        assertThat(response.getChatRooms()).hasSize(2);
        // cursor는 잘라낸 페이지의 마지막(2번째) 방을 가리킨다
        assertThat(response.getNextCursor()).isEqualTo(CursorCodec.encode(base.minusMinutes(2).toString(), 2L));
    }

    @Test
    void listChatRooms_cursor를_주면_디코딩해_레포지토리에_전달한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        LocalDateTime cursorUpdatedAt = LocalDateTime.of(2026, 11, 13, 21, 1);
        String cursor = CursorCodec.encode(cursorUpdatedAt.toString(), 5L);

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(aiChatRoomRepository.search(eq(1L), eq(cursorUpdatedAt), eq(5L), eq(21))).willReturn(List.of());

        ChatRoomListResponse response = chatService.listChatRooms(1L, cursor, null);

        assertThat(response.getChatRooms()).isEmpty();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listChatRooms_cursor_형식이_잘못되면_CHAT_400_INVALID_CURSOR를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));

        assertThatThrownBy(() -> chatService.listChatRooms(1L, "깨진커서", null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_400_INVALID_CURSOR);
    }

    @Test
    void listChatRooms_기수가_없으면_CHAT_404_GROUP_TERM_NOT_FOUND를_던진다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.listChatRooms(1L, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void sendMessage_질문이_비어_있으면_COMMON_400을_던진다() {
        ChatMessageSendRequest request = ChatMessageSendRequest.builder().content("   ").build();

        assertThatThrownBy(() -> chatService.sendMessage(8L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);
    }

    @Test
    void sendMessage_도구를_쓰지_않은_답변은_카드_없이_텍스트만_내려준다() {
        givenTurn(8L, false);
        given(chatToolLoop.run(eq(1L), any()))
                .willReturn(new ChatToolLoop.Outcome("등록된 일정은 3건입니다", Map.of()));
        givenSavedAnswer(8L, 201L);

        ChatMessageResponse response = chatService.sendMessage(8L, send("등록된 일정 알려줘"));

        assertThat(response.getMessageId()).isEqualTo(201L);
        assertThat(response.getRole()).isEqualTo("ASSISTANT");
        assertThat(response.getContent()).isEqualTo("등록된 일정은 3건입니다");
        assertThat(response.getCardType()).isNull();
        assertThat(response.getCard()).isNull();
        assertThat(response.getNavigation()).isNull();
    }

    @Test
    void sendMessage_입금_현황_도구가_실행되면_미입금자_카드를_만든다() {
        givenTurn(8L, false);
        given(chatToolLoop.run(eq(1L), any())).willReturn(settlementOutcome("{\"scheduleId\":10}",
                "{\"totalUnpaidAmount\":47000,\"members\":[{\"attendeeId\":401,\"name\":\"강민준\","
                        + "\"affiliation\":\"광고홍보학과\",\"phoneLast4\":\"1140\","
                        + "\"totalBilledAmount\":47000,\"unpaidAmount\":47000,"
                        + "\"paymentStatus\":\"UNPAID\",\"rounds\":[]}]}"));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule(10L, "가을공연")));
        givenSavedAnswer(8L, 202L);

        ChatMessageResponse response = chatService.sendMessage(8L, send("가을공연 미입금자 알려줘"));

        assertThat(response.getCardType()).isEqualTo("미입금자 조회");
        assertThat(response.getNavigation()).isEqualTo(new ChatNavigationResponse("SETTLEMENT", 10L));
        assertThat(response.getCard().get("totalAmount").asInt()).isEqualTo(47000);
        assertThat(response.getCard().get("items").get(0).get("attendeeId").asLong()).isEqualTo(401L);
    }

    @Test
    void sendMessage_미입금자가_없으면_카드_없이_일정만_남긴다() {
        givenTurn(8L, false);
        given(chatToolLoop.run(eq(1L), any())).willReturn(settlementOutcome("{\"scheduleId\":10}",
                "{\"totalUnpaidAmount\":0,\"members\":[]}"));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule(10L, "가을공연")));
        givenSavedAnswer(8L, 203L);

        ChatMessageResponse response = chatService.sendMessage(8L, send("가을공연 미입금자 알려줘"));

        assertThat(response.getCardType()).isNull();
        assertThat(response.getCard()).isNull();
        // 카드가 없어도 정산 화면으로 보내는 버튼은 남는다
        assertThat(response.getNavigation().type()).isEqualTo("SETTLEMENT");
        assertThat(response.getNavigation().targetId()).isEqualTo(10L);
    }

    @Test
    void sendMessage_도구에_따라_이동할_화면을_정한다() {
        givenTurn(8L, false);
        givenSavedAnswer(8L, 210L);
        given(chatToolLoop.run(eq(1L), any()))
                .willReturn(outcome("get_attendance_status", "{\"scheduleId\":10}"))
                .willReturn(outcome("get_meeting_detail", "{\"meetingId\":900}"))
                .willReturn(outcome("search_notes", "{\"scheduleId\":10}"))
                .willReturn(outcome("get_schedule_detail", "{\"scheduleId\":10}"))
                .willReturn(outcome("get_budget_summary", "{}"))
                .willReturn(outcome("search_files", "{}"));

        assertThat(navigationOf(8L)).isEqualTo(new ChatNavigationResponse("ATTENDANCE", 10L));
        assertThat(navigationOf(8L)).isEqualTo(new ChatNavigationResponse("MEETING", 900L));
        assertThat(navigationOf(8L)).isEqualTo(new ChatNavigationResponse("NOTE", 10L));
        assertThat(navigationOf(8L)).isEqualTo(new ChatNavigationResponse("SCHEDULE", 10L));
        // 기수 단위 화면이라 가리킬 대상 ID가 없다
        assertThat(navigationOf(8L)).isEqualTo(new ChatNavigationResponse("BUDGET", null));
        assertThat(navigationOf(8L)).isEqualTo(new ChatNavigationResponse("FILE", null));
    }

    @Test
    void sendMessage_일정_검색에서_정산으로_이어지면_정산_화면으로_보낸다() {
        givenTurn(8L, false);
        givenSavedAnswer(8L, 211L);
        given(chatToolLoop.run(eq(1L), any())).willReturn(new ChatToolLoop.Outcome("답변", Map.of(
                "search_schedules", List.of(new ChatToolLoop.ToolRun("{}", "{}")),
                "get_settlement_status", List.of(new ChatToolLoop.ToolRun("{\"scheduleId\":10}", "{}")))));

        // 검색 도구는 결과가 여러 건일 수 있어 이동 대상이 되지 않는다
        assertThat(navigationOf(8L)).isEqualTo(new ChatNavigationResponse("SETTLEMENT", 10L));
    }

    @Test
    void sendMessage_같은_도구가_여러_번_불리면_이동할_화면을_정하지_않는다() {
        givenTurn(8L, false);
        givenSavedAnswer(8L, 212L);
        given(chatToolLoop.run(eq(1L), any())).willReturn(new ChatToolLoop.Outcome("답변", Map.of(
                "get_attendance_status", List.of(
                        new ChatToolLoop.ToolRun("{\"scheduleId\":10}", "{}"),
                        new ChatToolLoop.ToolRun("{\"scheduleId\":11}", "{}")))));

        // 어느 일정으로 보낼지 정할 수 없다
        assertThat(navigationOf(8L)).isNull();
    }

    @Test
    void sendMessage_일정을_고르지_않은_메모_검색은_이동할_화면이_없다() {
        givenTurn(8L, false);
        givenSavedAnswer(8L, 213L);
        given(chatToolLoop.run(eq(1L), any())).willReturn(outcome("search_notes", "{\"keyword\":\"티켓\"}"));

        // 기수 전체 메모 검색은 특정 일정 화면으로 보낼 근거가 없다
        assertThat(navigationOf(8L)).isNull();
    }

    @Test
    void sendMessage_도구를_쓰지_않은_답변은_이동할_화면이_없다() {
        givenTurn(8L, false);
        givenSavedAnswer(8L, 214L);
        given(chatToolLoop.run(eq(1L), any())).willReturn(new ChatToolLoop.Outcome("답변", Map.of()));

        assertThat(navigationOf(8L)).isNull();
    }

    @Test
    void sendMessage_첫_질문이면_제목을_지어_저장한다() {
        givenTurn(8L, true);
        given(chatToolLoop.run(eq(1L), any())).willReturn(new ChatToolLoop.Outcome("답변", Map.of()));
        given(titleGenerator.generate("가을공연 미입금자 알려줘")).willReturn("가을공연 미입금 현황");
        givenSavedAnswer(8L, 204L);

        chatService.sendMessage(8L, send("가을공연 미입금자 알려줘"));

        verify(conversationStore).saveAnswer(eq(8L), eq("답변"), eq(ChatConversationStore.Navigation.none()),
                eq(null), eq(null), eq("가을공연 미입금 현황"));
    }

    @Test
    void sendMessage_이미_제목이_있으면_제목을_다시_짓지_않는다() {
        givenTurn(8L, false);
        given(chatToolLoop.run(eq(1L), any())).willReturn(new ChatToolLoop.Outcome("답변", Map.of()));
        givenSavedAnswer(8L, 205L);

        chatService.sendMessage(8L, send("두 번째 질문"));

        verify(titleGenerator, never()).generate(anyString());
        verify(conversationStore).saveAnswer(eq(8L), eq("답변"), eq(ChatConversationStore.Navigation.none()),
                eq(null), eq(null), eq(null));
    }

    @Test
    void sendMessage_AI_호출이_실패하면_CHAT_502_AI_FAILED를_던진다() {
        givenTurn(8L, false);
        given(chatToolLoop.run(eq(1L), any())).willThrow(new AiClientException("타임아웃"));

        assertThatThrownBy(() -> chatService.sendMessage(8L, send("질문")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_502_AI_FAILED);
    }

    @Test
    void sendMessage_도구_호출_상한을_넘으면_CHAT_502_AI_FAILED를_던진다() {
        givenTurn(8L, false);
        given(chatToolLoop.run(eq(1L), any())).willThrow(new ChatToolLoopExceededException("상한 초과"));

        assertThatThrownBy(() -> chatService.sendMessage(8L, send("질문")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_502_AI_FAILED);
    }

    @Test
    void listMessages_대화_순서대로_내려주고_nextCursor를_만든다() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 13, 21, 0);
        given(aiChatRoomRepository.existsById(8L)).willReturn(true);
        given(aiMessageRepository.search(eq(8L), eq(null), eq(null), eq(3)))
                .willReturn(List.of(
                        message(200L, MessageRole.USER, "질문", base),
                        message(201L, MessageRole.ASSISTANT, "답변", base.plusSeconds(1)),
                        message(202L, MessageRole.USER, "다음 질문", base.plusSeconds(2))));

        ChatMessageListResponse response = chatService.listMessages(8L, null, 2);

        assertThat(response.getMessages()).extracting(ChatMessageResponse::getMessageId)
                .containsExactly(200L, 201L);
        assertThat(response.getNextCursor())
                .isEqualTo(CursorCodec.encode(base.plusSeconds(1).toString(), 201L));
    }

    @Test
    void listMessages_저장된_카드_JSON을_되살려_내려준다() {
        given(aiChatRoomRepository.existsById(8L)).willReturn(true);
        AiMessage withCard = message(201L, MessageRole.ASSISTANT, "확인했어요",
                LocalDateTime.of(2026, 11, 13, 21, 0));
        ReflectionTestUtils.setField(withCard, "cardType", "미입금자 조회");
        ReflectionTestUtils.setField(withCard, "contentJson", "{\"totalAmount\":47000}");
        given(aiMessageRepository.search(eq(8L), eq(null), eq(null), eq(21))).willReturn(List.of(withCard));

        ChatMessageListResponse response = chatService.listMessages(8L, null, null);

        assertThat(response.getMessages().get(0).getCardType()).isEqualTo("미입금자 조회");
        assertThat(response.getMessages().get(0).getCard().get("totalAmount").asInt()).isEqualTo(47000);
    }

    @Test
    void listMessages_대화방이_없으면_CHAT_404_NOT_FOUND를_던진다() {
        given(aiChatRoomRepository.existsById(8L)).willReturn(false);

        assertThatThrownBy(() -> chatService.listMessages(8L, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_404_NOT_FOUND);
    }

    @Test
    void listMessages_cursor_형식이_잘못되면_CHAT_400_INVALID_CURSOR를_던진다() {
        given(aiChatRoomRepository.existsById(8L)).willReturn(true);

        assertThatThrownBy(() -> chatService.listMessages(8L, "깨진커서", null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_400_INVALID_CURSOR);
    }

    private ChatNavigationResponse navigationOf(Long chatRoomId) {
        return chatService.sendMessage(chatRoomId, send("질문")).getNavigation();
    }

    private ChatMessageSendRequest send(String content) {
        return ChatMessageSendRequest.builder().content(content).build();
    }

    private void givenTurn(Long chatRoomId, boolean needsTitle) {
        given(conversationStore.openTurn(eq(chatRoomId), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .willReturn(new ChatConversationStore.ConversationContext(1L, needsTitle, List.of()));
    }

    private void givenSavedAnswer(Long chatRoomId, Long messageId) {
        given(conversationStore.saveAnswer(eq(chatRoomId), any(), any(), any(), any(), any()))
                .willAnswer(invocation -> message(messageId, MessageRole.ASSISTANT,
                        invocation.getArgument(1), LocalDateTime.of(2026, 11, 13, 21, 0)));
    }

    private ChatToolLoop.Outcome settlementOutcome(String argumentsJson, String resultJson) {
        return new ChatToolLoop.Outcome("확인했어요",
                Map.of("get_settlement_status", List.of(new ChatToolLoop.ToolRun(argumentsJson, resultJson))));
    }

    private ChatToolLoop.Outcome outcome(String toolName, String argumentsJson) {
        return new ChatToolLoop.Outcome("답변",
                Map.of(toolName, List.of(new ChatToolLoop.ToolRun(argumentsJson, "{}"))));
    }

    private AiMessage message(Long id, MessageRole role, String content, LocalDateTime createdAt) {
        AiMessage message = BeanUtils.instantiateClass(AiMessage.class);
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "role", role);
        ReflectionTestUtils.setField(message, "content", content);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }

    private Schedule schedule(Long id, String title) {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "id", id);
        ReflectionTestUtils.setField(schedule, "title", title);
        return schedule;
    }

    private AiChatRoom chatRoom(Long id, String title, LocalDateTime updatedAt) {
        AiChatRoom room = AiChatRoom.create(mock(GroupTerm.class), title);
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "updatedAt", updatedAt);
        return room;
    }
}
