package com.moimzang.domain.file.controller;

import com.moimzang.domain.file.dto.response.FileDownloadUrlResponse;
import com.moimzang.domain.file.dto.response.FileListResponse;
import com.moimzang.domain.file.dto.response.FilePolicyResponse;
import com.moimzang.domain.file.dto.response.FileRestoreResponse;
import com.moimzang.domain.file.dto.response.FileUploadResponse;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileCategoryFilter;
import com.moimzang.domain.file.enums.FileDownloadMode;
import com.moimzang.domain.file.enums.FileSortOption;
import com.moimzang.global.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "문서함", description = "파일 업로드·조회·삭제 API")
public interface FileApi {

    @Operation(summary = "파일 업로드 정책 조회", description = "허용 확장자와 최대 용량을 조회한다.")
    ResponseEntity<BaseResponse<FilePolicyResponse>> getPolicy();

    @Operation(summary = "파일 업로드", description = "기수의 문서함에 파일을 1개 업로드한다. scheduleId가 있으면 해당 일정에 귀속된다.")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
    ResponseEntity<BaseResponse<FileUploadResponse>> uploadFile(
            Long termId, MultipartFile file, String name, FileCategory category, Long scheduleId);

    @Operation(summary = "파일 목록 조회", description = "기수의 문서함 파일 목록을 cursor 페이지네이션으로 조회한다.")
    ResponseEntity<BaseResponse<FileListResponse>> listFiles(
            Long termId, FileCategoryFilter category, String keyword, FileSortOption sort, Long scheduleId,
            String cursor, Integer size);

    @Operation(summary = "다운로드·미리보기 URL 발급", description = "파일을 열람·다운로드할 수 있는 URL을 발급한다.")
    ResponseEntity<BaseResponse<FileDownloadUrlResponse>> getDownloadUrl(Long fileId, FileDownloadMode mode);

    @Operation(summary = "파일 콘텐츠 서빙", description = "download-url이 돌려준 url이 실제로 가리키는 대상. "
            + "mode=DOWNLOAD면 Content-Disposition: attachment로, PREVIEW면 inline으로 응답한다.")
    ResponseEntity<StreamingResponseBody> getFileContent(Long fileId, FileDownloadMode mode);

    @Operation(summary = "파일 삭제", description = "파일을 소프트 삭제한다.")
    ResponseEntity<BaseResponse<Void>> deleteFile(Long fileId);

    @Operation(summary = "파일 삭제 되돌리기", description = "소프트 삭제된 파일을 복구한다.")
    ResponseEntity<BaseResponse<FileRestoreResponse>> restoreFile(Long fileId);

    @Operation(summary = "파일 전체 다운로드", description = "현재 조건에 맞는 파일들을 ZIP으로 묶어 내려준다.")
    ResponseEntity<StreamingResponseBody> downloadZip(Long termId, FileCategoryFilter category, String keyword);
}
