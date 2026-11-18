package com.moimzang.domain.meeting.controller;

import com.moimzang.domain.meeting.dto.request.MeetingAgendaCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingAgendaUpdateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCategoryCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCategoryUpdateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingSummaryUpdateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingUpdateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingAgendaCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingAgendaUpdateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingDetailResponse;
import com.moimzang.domain.meeting.dto.response.MeetingEndResponse;
import com.moimzang.domain.meeting.dto.response.MeetingLinkableAgendaListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryGenerateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryUpdateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingUpdateResponse;
import com.moimzang.domain.meeting.enums.MeetingStatus;
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

@Tag(name = "회의", description = "회의·안건 관리 API")
public interface MeetingApi {

    @Operation(summary = "회의 만들기", description = "제목·날짜·카테고리·안건 목록을 받아 회의와 안건을 한 번에 생성한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "생성 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEETING_404_GROUP_TERM_NOT_FOUND 또는 MEETING_404_CATEGORY_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingCreateResponse>> createMeeting(
            Long termId, @Valid MeetingCreateRequest request);

    @Operation(summary = "회의 목록 조회",
               description = "기수의 회의 목록을 상태·카테고리·검색어·소속 일정으로 필터링해 cursor 페이지네이션으로 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "MEETING_400_INVALID_CURSOR",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEETING_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingListResponse>> listMeetings(
            @Parameter(description = "기수 ID", example = "1") Long termId,
            @Parameter(description = "회의 상태 필터") MeetingStatus status,
            @Parameter(description = "회의 카테고리 ID 필터", example = "1") Long categoryId,
            @Parameter(description = "검색어(제목·목록 요약·참석자 중 하나라도 일치하면 조회)", example = "운영진") String keyword,
            @Parameter(description = "소속 일정 ID 필터. 주면 그 일정에 속한 회의록만, 생략하면 전부 조회", example = "1")
            Long scheduleId,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지", example = "MjAyNi0xMS0wNw.5") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);

    @Operation(summary = "연결 가능한 안건 목록 조회",
               description = "일정 상세의 '안건 연결하기' 모달에 띄울 후보 목록. 어느 일정에도 속하지 않은 "
                       + "안건(소속 일정이 없는 회의의 안건 중 아직 연결되지 않은 것)만 내려준다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "MEETING_400_INVALID_CURSOR",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEETING_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingLinkableAgendaListResponse>> listLinkableAgendas(
            @Parameter(description = "기수 ID", example = "1") Long termId,
            @Parameter(description = "안건 내용 검색어", example = "홍보") String keyword,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지", example = "Mw.7") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);

    @Operation(summary = "회의 상세 조회", description = "회의 진행 화면·정리 화면이 공통으로 쓰는 상세 조회. 안건 전체 목록과 진행률을 함께 내려준다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "MEETING_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingDetailResponse>> getMeetingDetail(
            @Parameter(description = "회의 ID", example = "1") Long meetingId);

    @Operation(summary = "회의 정보 수정", description = "제목·날짜·카테고리·참석자·목록 요약 중 보낸 필드만 수정한다. 안건은 별도 API로 처리한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEETING_404_NOT_FOUND 또는 MEETING_404_CATEGORY_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "MEETING_409_ALREADY_ENDED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingUpdateResponse>> updateMeeting(
            @Parameter(description = "회의 ID", example = "1") Long meetingId, @Valid MeetingUpdateRequest request);

    @Operation(summary = "회의 삭제", description = "회의 상태(진행중/완료)와 무관하게 삭제를 허용한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "MEETING_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> deleteMeeting(@Parameter(description = "회의 ID", example = "1") Long meetingId);

    @Operation(summary = "회의 카테고리 목록 조회", description = "기수의 회의 카테고리 목록을 조회한다. 일정 카테고리와는 별도로 운영된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "MEETING_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingCategoryListResponse>> listCategories(
            @Parameter(description = "기수 ID", example = "1") Long termId);

    @Operation(summary = "회의 카테고리 생성", description = "기수에 새 회의 카테고리를 만든다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "생성 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEETING_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingCategoryResponse>> createCategory(
            @Parameter(description = "기수 ID", example = "1") Long termId, @Valid MeetingCategoryCreateRequest request);

    @Operation(summary = "회의 카테고리 수정", description = "회의 만들기 화면 카테고리 선택의 연필 버튼. 이름만 바꾼다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEETING_404_CATEGORY_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingCategoryResponse>> updateCategory(
            @Parameter(description = "회의 카테고리 ID", example = "1") Long categoryId,
            @Valid MeetingCategoryUpdateRequest request);

    @Operation(summary = "안건 추가", description = "기존 안건 목록 끝에 새 안건을 추가한다(orderNo=마지막+1, status=PENDING).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "추가 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEETING_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "MEETING_409_ALREADY_ENDED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingAgendaCreateResponse>> addAgenda(
            @Parameter(description = "회의 ID", example = "1") Long meetingId, @Valid MeetingAgendaCreateRequest request);

    @Operation(summary = "안건 내용 저장", description = "안건 내용(content)·논의 내용·결정 사항·상태 중 보낸 필드만 수정한다. "
            + "'다음 안건' 버튼은 이 API를 status=COMPLETED로 호출하는 것이고, '회의 중단하기'는 status 없이(내용만) 호출한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장 성공"),
        @ApiResponse(responseCode = "404", description = "AGENDA_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "MEETING_409_ALREADY_ENDED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingAgendaUpdateResponse>> updateAgendaContent(
            @Parameter(description = "안건 ID", example = "3") Long agendaId, @Valid MeetingAgendaUpdateRequest request);

    @Operation(summary = "안건 삭제", description = "안건을 삭제한다. 안건이 1개만 남은 회의는 삭제할 수 없다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "AGENDA_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "MEETING_409_ALREADY_ENDED 또는 AGENDA_409_AT_LEAST_ONE_REQUIRED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> deleteAgenda(@Parameter(description = "안건 ID", example = "3") Long agendaId);

    @Operation(summary = "회의 종료", description = "회의를 종료한다. 안건이 전부 완료되지 않았어도 허용한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "종료 성공"),
        @ApiResponse(responseCode = "404", description = "MEETING_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "MEETING_409_ALREADY_ENDED(이미 종료)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingEndResponse>> endMeeting(@Parameter(description = "회의 ID", example = "1") Long meetingId);

    @Operation(summary = "AI 요약 생성", description = "안건의 논의 내용·결정 사항을 외부 AI로 종합 요약해 summaryContent를 덮어쓴다. "
            + "원본 안건은 항상 보존된다. '다시 요약하기'도 이 API를 그대로 재호출한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "생성 성공"),
        @ApiResponse(responseCode = "404", description = "MEETING_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "502", description = "MEETING_502_SUMMARY_FAILED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingSummaryGenerateResponse>> generateSummary(
            @Parameter(description = "회의 ID", example = "1") Long meetingId);

    @Operation(summary = "회의록 저장(요약 수정)", description = "사용자가 AI 요약을 직접 고친 내용을 그대로 저장한다. AI를 다시 호출하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEETING_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MeetingSummaryUpdateResponse>> updateSummary(
            @Parameter(description = "회의 ID", example = "1") Long meetingId, @Valid MeetingSummaryUpdateRequest request);
}
