package com.moimzang.domain.chat.controller;

import com.moimzang.domain.chat.dto.request.ChatRoomCreateRequest;
import com.moimzang.domain.chat.dto.response.ChatRoomCreateResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomListResponse;
import com.moimzang.domain.chat.service.ChatService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatController implements ChatApi {

    private final ChatService chatService;

    // 기수에 새 AI 대화방을 만든다
    @Override
    @PostMapping("/api/terms/{termId}/ai/chat-rooms")
    public ResponseEntity<BaseResponse<ChatRoomCreateResponse>> createChatRoom(
            @PathVariable Long termId,
            @Valid @RequestBody(required = false) ChatRoomCreateRequest request
    ) {
        // 제목이 전부 선택 필드라 FE가 바디 없이 '새 대화 시작'만 호출할 수 있다
        ChatRoomCreateResponse response = chatService.createChatRoom(termId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 기수의 AI 대화방 목록을 최근 활동 순으로 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/ai/chat-rooms")
    public ResponseEntity<BaseResponse<ChatRoomListResponse>> listChatRooms(
            @PathVariable Long termId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        ChatRoomListResponse response = chatService.listChatRooms(termId, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
