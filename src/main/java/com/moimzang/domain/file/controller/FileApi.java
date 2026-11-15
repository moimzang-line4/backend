package com.moimzang.domain.file.controller;

import com.moimzang.domain.file.dto.response.FileDownloadUrlResponse;
import com.moimzang.domain.file.dto.response.FileListResponse;
import com.moimzang.domain.file.dto.response.FilePolicyResponse;
import com.moimzang.domain.file.dto.response.FileRestoreResponse;
import com.moimzang.domain.file.dto.response.FileUploadResponse;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileCategoryFilter;
import com.moimzang.domain.file.enums.FileContentVariant;
import com.moimzang.domain.file.enums.FileDownloadMode;
import com.moimzang.domain.file.enums.FileSortOption;
import com.moimzang.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "문서함", description = "파일 업로드·조회·삭제 API")
public interface FileApi {

    @Operation(summary = "파일 업로드 정책 조회", description = "허용 확장자와 최대 용량을 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<BaseResponse<FilePolicyResponse>> getPolicy();

    @Operation(summary = "파일 업로드", description = "기수의 문서함에 파일을 1개 업로드한다. scheduleId가 있으면 해당 일정에 귀속된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "업로드 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패) 또는 FILE_400_EXTENSION_NOT_ALLOWED 또는 "
                + "FILE_400_SCHEDULE_TERM_MISMATCH 또는 FILE_400_REQUIRED_FIELD_MISSING",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "FILE_404_GROUP_TERM_NOT_FOUND 또는 FILE_404_SCHEDULE_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "413", description = "FILE_413_SIZE_EXCEEDED",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<FileUploadResponse>> uploadFile(
            @Parameter(description = "기수 ID", example = "1") Long termId,
            @Parameter(description = "업로드할 파일", content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
                    MultipartFile file,
            @Parameter(description = "파일 이름", example = "2024 예산안.xlsx") String name,
            @Parameter(description = "파일 카테고리") FileCategory category,
            @Parameter(description = "연결할 일정 ID(선택)", example = "1") Long scheduleId);

    @Operation(summary = "파일 목록 조회", description = "기수의 문서함 파일 목록을 cursor 페이지네이션으로 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON_400(검증 실패) 또는 FILE_400_INVALID_CURSOR",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(responseCode = "404", description = "FILE_404_GROUP_TERM_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<FileListResponse>> listFiles(
            @Parameter(description = "기수 ID", example = "1") Long termId,
            @Parameter(description = "파일 카테고리 필터(기본 ALL)") FileCategoryFilter category,
            @Parameter(description = "검색어(파일명 기준)", example = "예산") String keyword,
            @Parameter(description = "정렬 기준(기본 LATEST)") FileSortOption sort,
            @Parameter(description = "연결된 일정 ID 필터(선택)", example = "1") Long scheduleId,
            @Parameter(description = "이전 응답의 nextCursor. 생략하면 첫 페이지") String cursor,
            @Parameter(description = "페이지 크기(기본 20, 최대 100)", example = "20") Integer size);

    @Operation(summary = "다운로드·미리보기 URL 발급", description = "파일을 열람·다운로드할 수 있는 URL을 발급한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "발급 성공"),
        @ApiResponse(responseCode = "404", description = "FILE_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<FileDownloadUrlResponse>> getDownloadUrl(
            @Parameter(description = "파일 ID", example = "1") Long fileId,
            @Parameter(description = "다운로드·미리보기 모드(기본 DOWNLOAD)") FileDownloadMode mode);

    @Operation(summary = "파일 콘텐츠 서빙", description = "download-url이 돌려준 url이 실제로 가리키는 대상. "
            + "mode=DOWNLOAD면 Content-Disposition: attachment로, PREVIEW면 inline으로 응답한다. "
            + "variant=THUMBNAIL이면 활동사진 그리드용 축소본을 내려준다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "서빙 성공"),
        @ApiResponse(responseCode = "404", description = "FILE_404_NOT_FOUND, FILE_404_THUMBNAIL_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<StreamingResponseBody> getFileContent(
            @Parameter(description = "파일 ID", example = "1") Long fileId,
            @Parameter(description = "다운로드·미리보기 모드(기본 DOWNLOAD)") FileDownloadMode mode,
            @Parameter(description = "내려줄 바이트(기본 ORIGINAL). THUMBNAIL은 활동사진 그리드용 축소본이며, "
                                   + "썸네일이 없는 자료면 FILE_404_THUMBNAIL_NOT_FOUND가 난다")
            FileContentVariant variant);

    @Operation(summary = "파일 삭제", description = "파일을 소프트 삭제한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "404", description = "FILE_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<Void>> deleteFile(@Parameter(description = "파일 ID", example = "1") Long fileId);

    @Operation(summary = "파일 삭제 되돌리기", description = "소프트 삭제된 파일을 복구한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "복구 성공"),
        @ApiResponse(responseCode = "404", description = "FILE_404_NOT_FOUND",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<BaseResponse<FileRestoreResponse>> restoreFile(@Parameter(description = "파일 ID", example = "1") Long fileId);

    @Operation(summary = "파일 전체 다운로드", description = "현재 조건에 맞는 파일들을 ZIP으로 묶어 내려준다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "다운로드 성공"),
        @ApiResponse(responseCode = "404", description = "FILE_404_GROUP_TERM_NOT_FOUND 또는 FILE_404_NO_DOWNLOADABLE_FILES",
                     content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    ResponseEntity<StreamingResponseBody> downloadZip(
            @Parameter(description = "기수 ID", example = "1") Long termId,
            @Parameter(description = "파일 카테고리 필터(기본 ALL)") FileCategoryFilter category,
            @Parameter(description = "검색어(파일명 기준)", example = "예산") String keyword);
}
