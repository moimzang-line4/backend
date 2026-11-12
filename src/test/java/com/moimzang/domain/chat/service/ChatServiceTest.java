package com.moimzang.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.moimzang.domain.chat.dto.request.ChatRoomCreateRequest;
import com.moimzang.domain.chat.dto.response.ChatRoomCreateResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomListResponse;
import com.moimzang.domain.chat.entity.AiChatRoom;
import com.moimzang.domain.chat.exception.ChatErrorCode;
import com.moimzang.domain.chat.mapper.ChatMapper;
import com.moimzang.domain.chat.repository.AiChatRoomRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private AiChatRoomRepository aiChatRoomRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    // mapper는 mock하지 않는다 — 매핑 누락을 테스트에서도 실제 생성 코드로 검증한다
    private final ChatMapper chatMapper = Mappers.getMapper(ChatMapper.class);

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(aiChatRoomRepository, groupTermRepository, chatMapper);
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

    private AiChatRoom chatRoom(Long id, String title, LocalDateTime updatedAt) {
        AiChatRoom room = AiChatRoom.create(mock(GroupTerm.class), title);
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "updatedAt", updatedAt);
        return room;
    }
}
