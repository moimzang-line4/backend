package com.moimzang.domain.chat.service;

import com.moimzang.domain.chat.dto.request.ChatRoomCreateRequest;
import com.moimzang.domain.chat.dto.response.ChatRoomCreateResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomListResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.moimzang.domain.chat.entity.AiChatRoom;
import com.moimzang.domain.chat.exception.ChatErrorCode;
import com.moimzang.domain.chat.mapper.ChatMapper;
import com.moimzang.domain.chat.repository.AiChatRoomRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final AiChatRoomRepository aiChatRoomRepository;
    private final GroupTermRepository groupTermRepository;
    private final ChatMapper chatMapper;

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
}
