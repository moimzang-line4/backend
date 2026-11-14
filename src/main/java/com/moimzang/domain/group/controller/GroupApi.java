package com.moimzang.domain.group.controller;

import com.moimzang.domain.group.dto.request.GroupCreateRequest;
import com.moimzang.domain.group.dto.request.GroupUpdateRequest;
import com.moimzang.domain.group.dto.response.GroupCreateResponse;
import com.moimzang.domain.group.dto.response.GroupDetailResponse;
import com.moimzang.domain.group.dto.response.GroupUpdateResponse;
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

@Tag(name = "그룹", description = "그룹·기수 관리 API")
public interface GroupApi {

    @Operation(summary = "그룹 생성", description = "그룹명과 첫 기수명을 받아 그룹과 기수를 함께 생성한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "생성 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(필수 항목 누락·공백)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<GroupCreateResponse>> createGroup(@Valid GroupCreateRequest request);

    @Operation(summary = "그룹 정보 조회", description = "그룹명과 기수 목록을 조회한다. 설정 화면과 기수 선택 드롭다운에서 쓴다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "GROUP_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<GroupDetailResponse>> getGroup(
            @Parameter(description = "그룹 ID", example = "1") Long groupId);

    @Operation(summary = "그룹 정보 수정",
               description = "그룹명을 수정하고, termName을 보내면 새 기수를 만든다. 보낸 필드만 반영한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(공백만 입력·길이 초과)",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "GROUP_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<GroupUpdateResponse>> updateGroup(
            @Parameter(description = "그룹 ID", example = "1") Long groupId, @Valid GroupUpdateRequest request);
}
