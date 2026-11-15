package com.moimzang.domain.memberlist.controller;

import com.moimzang.domain.memberlist.dto.request.MemberAddRequest;
import com.moimzang.domain.memberlist.dto.request.MemberRegisterRequest;
import com.moimzang.domain.memberlist.dto.request.MemberUpdateRequest;
import com.moimzang.domain.memberlist.dto.response.MemberCreateResponse;
import com.moimzang.domain.memberlist.dto.response.MemberJoinFormResponse;
import com.moimzang.domain.memberlist.dto.response.MemberPageResponse;
import com.moimzang.domain.memberlist.dto.response.MemberUpdateResponse;
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
public interface MemberApi {

    @Operation(summary = "부원 등록 폼 조회(공개)", description = "공개 링크로 들어온 부원에게 보여줄 폼 정보. fields가 true인 항목만 폼에 노출하고 필수로 받는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "MEMBER_404_JOIN_LINK_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "410", description = "MEMBER_410_JOIN_CLOSED(닫힌 링크 또는 마감된 명단)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberJoinFormResponse>> getJoinForm(
            @Parameter(description = "등록 링크 토큰", example = "q3Xv9LmT2kRw8NaB") String joinToken);

    @Operation(summary = "부원 등록(공개)", description = "이름·학과는 항상 필수, 명단방에서 켠 항목도 필수. 꺼진 항목은 보내도 저장하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "등록 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400, MEMBER_400_REQUIRED_FIELD_MISSING, MEMBER_400_PRIVACY_CONSENT_REQUIRED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEMBER_404_JOIN_LINK_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "409", description = "MEMBER_409_ALREADY_REGISTERED(같은 명단방에 이름·전화번호가 같은 부원 있음)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "410", description = "MEMBER_410_JOIN_CLOSED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberCreateResponse>> registerMember(
            @Parameter(description = "등록 링크 토큰", example = "q3Xv9LmT2kRw8NaB") String joinToken,
            @Valid MemberRegisterRequest request);

    @Operation(summary = "부원 목록 조회", description = "명단 조회 탭. 등록한 순서대로 cursor 페이지 조회, 이름·학과·전화번호 검색. 수집하지 않은 항목은 null.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "MEMBER_400_INVALID_CURSOR",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEMBER_LIST_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberPageResponse>> listMembers(
            @Parameter(description = "명단방 ID", example = "2") Long memberListId,
            @Parameter(description = "검색어(이름·학과·전화번호)", example = "한지우") String keyword,
            @Parameter(description = "이전 응답의 nextCursor") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);

    @Operation(summary = "부원 추가(운영진)", description = "명단 조회 화면 '+ 추가'. 이름·학과 필수, 학교·전화번호 선택. 마감된 명단방에도 추가할 수 있다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "추가 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(이름·학과 누락)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEMBER_LIST_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberCreateResponse>> addMember(
            @Parameter(description = "명단방 ID", example = "2") Long memberListId, @Valid MemberAddRequest request);

    @Operation(summary = "부원 수정", description = "이름·학교·학과·전화번호 중 보낸 필드만 수정. 학교·전화번호에 빈 문자열을 보내면 비운다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(이름·학과 공백, 길이 초과)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "MEMBER_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<MemberUpdateResponse>> updateMember(
            @Parameter(description = "부원 ID", example = "101") Long memberId, @Valid MemberUpdateRequest request);

    @Operation(summary = "부원 삭제", description = "부원을 삭제한다. 이 부원으로 등록된 일정 참가자는 남기고 부원 연결만 끊는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "MEMBER_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> deleteMember(@Parameter(description = "부원 ID", example = "101") Long memberId);
}
