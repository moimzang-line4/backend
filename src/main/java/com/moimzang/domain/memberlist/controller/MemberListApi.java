package com.moimzang.domain.memberlist.controller;

import com.moimzang.domain.memberlist.dto.request.MemberListCreateRequest;
import com.moimzang.domain.memberlist.dto.request.MemberListUpdateRequest;
import com.moimzang.domain.memberlist.dto.response.MemberListCreateResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListDetailResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListListResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListUpdateResponse;
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

@Tag(name = "부원 명단", description = "명단방·부원 관리 API")
public interface MemberListApi {

    @Operation(summary = "명단방 생성", description = "명단방을 만들고 등록 링크 토큰을 발급한다. 이름·학과는 항상 수집하고 나머지는 토글로 정한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "생성 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(이름·마감일 누락)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEMBER_LIST_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberListCreateResponse>> createMemberList(
            @Parameter(description = "기수 ID", example = "1") Long termId, @Valid MemberListCreateRequest request);

    @Operation(summary = "명단방 목록 조회", description = "부원 탭 홈의 명단방 카드. 등록 인원과 등록중/등록완료 상태를 함께 내려준다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "MEMBER_LIST_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberListListResponse>> listMemberLists(
            @Parameter(description = "기수 ID", example = "1") Long termId);

    @Operation(summary = "명단방 상세(링크 관리) 조회", description = "등록 상태·인원·등록 링크·수집 항목을 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "MEMBER_LIST_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberListDetailResponse>> getMemberList(
            @Parameter(description = "명단방 ID", example = "1") Long memberListId);

    @Operation(summary = "명단방 수정",
               description = "이름·마감일·수집 항목을 수정한다. 같은 명단방과 링크를 유지하고, 수집 항목 변경은 이후 등록자부터 적용된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(공백 이름·길이 초과)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEMBER_LIST_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberListUpdateResponse>> updateMemberList(
            @Parameter(description = "명단방 ID", example = "1") Long memberListId, @Valid MemberListUpdateRequest request);

    @Operation(summary = "명단방 삭제", description = "명단방·부원과 그 부원으로 등록된 일정 참가자·출석·정산 기록을 함께 삭제한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "MEMBER_LIST_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> deleteMemberList(@Parameter(description = "명단방 ID", example = "1") Long memberListId);
}
