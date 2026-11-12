package com.moimzang.domain.chat.controller;

import com.moimzang.domain.chat.dto.request.ChatMessageSendRequest;
import com.moimzang.domain.chat.dto.request.ChatRoomCreateRequest;
import com.moimzang.domain.chat.dto.response.ChatMessageListResponse;
import com.moimzang.domain.chat.dto.response.ChatMessageResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomCreateResponse;
import com.moimzang.domain.chat.dto.response.ChatRoomListResponse;
import com.moimzang.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "AI 챗봇", description = "AI 채팅방·메시지 관리 API")
public interface ChatApi {

    @Operation(summary = "AI 대화방 생성",
               description = "새 대화를 시작한다. 제목은 선택이며, 비우면 첫 질문을 보낼 때 그 질문 텍스트로 채워진다. "
                           + "대화방은 기수당 여러 개를 유지한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "생성 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "CHAT_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ChatRoomCreateResponse>> createChatRoom(
            @Parameter(description = "기수 ID", example = "1") Long termId,
            @Valid ChatRoomCreateRequest request);

    @Operation(summary = "AI 대화방 목록 조회",
               description = "기수의 대화 기록을 최근 활동 순으로 cursor 페이지네이션 조회한다. "
                           + "로그인이 없어 그 기수 운영진 전체가 공유한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "CHAT_400_INVALID_CURSOR",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "CHAT_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ChatRoomListResponse>> listChatRooms(
            @Parameter(description = "기수 ID", example = "1") Long termId,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지",
                       example = "MjAyNi0xMS0xM1QyMTowMTowMA.8") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);

    @Operation(summary = "AI 메시지 전송",
               description = "자연어 질문을 받아 그 기수의 일정·정산 데이터를 조회해 답한다. "
                           + "미입금자 조회처럼 결과 카드가 있는 질문은 card에 구조화된 결과를 함께 내려준다. "
                           + "금액·인원은 서버가 집계한 값이라 AI 응답 문장과 무관하게 정확하다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "응답 성공"),
        @ApiResponse(responseCode = "400", description = "CHAT_400_EMPTY_CONTENT 또는 COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "CHAT_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "502", description = "CHAT_502_AI_FAILED(AI 호출 실패·도구 호출 상한 초과)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ChatMessageResponse>> sendMessage(
            @Parameter(description = "대화방 ID", example = "8") Long chatRoomId,
            @Valid ChatMessageSendRequest request);

    @Operation(summary = "AI 메시지 목록 조회",
               description = "대화방의 메시지를 대화 순서대로 cursor 페이지네이션 조회한다. "
                           + "card는 전송 시점 스냅샷이라 이후 입금 상태가 바뀌어도 갱신되지 않는다 — "
                           + "최신값이 필요하면 입금 현황 조회 API를 다시 호출한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "CHAT_400_INVALID_CURSOR",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "CHAT_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<ChatMessageListResponse>> listMessages(
            @Parameter(description = "대화방 ID", example = "8") Long chatRoomId,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지",
                       example = "MjAyNi0xMS0xM1QyMTowMTowMA.201") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);
}
