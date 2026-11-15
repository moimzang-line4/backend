package com.moimzang.domain.file.controller;

import com.moimzang.domain.file.dto.response.FileContentTarget;
import com.moimzang.domain.file.dto.response.FileDownloadUrlResponse;
import com.moimzang.domain.file.dto.response.FileListResponse;
import com.moimzang.domain.file.dto.response.FilePolicyResponse;
import com.moimzang.domain.file.dto.response.FileRestoreResponse;
import com.moimzang.domain.file.dto.response.FileUploadResponse;
import com.moimzang.domain.file.dto.response.FileZipTarget;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileCategoryFilter;
import com.moimzang.domain.file.enums.FileContentVariant;
import com.moimzang.domain.file.enums.FileDownloadMode;
import com.moimzang.domain.file.enums.FileSortOption;
import com.moimzang.domain.file.service.FileService;
import com.moimzang.global.response.BaseResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequiredArgsConstructor
public class FileController implements FileApi {

    private final FileService fileService;

    // 허용 확장자·최대 용량(업로드 정책)을 조회한다
    @Override
    @GetMapping("/api/files/policy")
    public ResponseEntity<BaseResponse<FilePolicyResponse>> getPolicy() {
        FilePolicyResponse response = fileService.getPolicy();
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 기수의 문서함에 파일을 1개 업로드한다. scheduleId가 있으면 그 일정에 귀속된 자료로 저장된다
    @Override
    @PostMapping(value = "/api/terms/{termId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<FileUploadResponse>> uploadFile(
            @PathVariable Long termId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("category") FileCategory category,
            @RequestParam(value = "scheduleId", required = false) Long scheduleId
    ) {
        FileUploadResponse response = fileService.upload(termId, file, name, category, scheduleId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 기수의 문서함 파일 목록을 조건(카테고리/검색어/정렬/일정)에 맞게 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/files")
    public ResponseEntity<BaseResponse<FileListResponse>> listFiles(
            @PathVariable Long termId,
            @RequestParam(defaultValue = "ALL") FileCategoryFilter category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "LATEST") FileSortOption sort,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        FileListResponse response = fileService.listFiles(
                termId, category, keyword, sort, scheduleId, cursor, size);

        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 파일을 열람·다운로드할 수 있는 URL을 발급한다
    @Override
    @GetMapping("/api/files/{fileId}/download-url")
    public ResponseEntity<BaseResponse<FileDownloadUrlResponse>> getDownloadUrl(
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "DOWNLOAD") FileDownloadMode mode
    ) {
        FileDownloadUrlResponse response = fileService.getDownloadUrl(fileId, mode);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // download-url이 돌려준 url이 실제로 가리키는 대상 — mode에 따라 Content-Disposition을
    // attachment/inline으로 다르게 설정해 브라우저의 저장·미리보기 동작을 서버가 직접 강제한다
    @Override
    @GetMapping("/api/files/{fileId}/content")
    public ResponseEntity<StreamingResponseBody> getFileContent(
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "DOWNLOAD") FileDownloadMode mode,
            @RequestParam(defaultValue = "ORIGINAL") FileContentVariant variant
    ) {
        FileContentTarget target = fileService.getFileContent(fileId, variant);
        String fileName = target.getName() + "." + target.getExtension();
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        String dispositionType = mode == FileDownloadMode.PREVIEW ? "inline" : "attachment";
        StreamingResponseBody body = out -> fileService.writeFileContentTo(target, out);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.builder(dispositionType).filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    // 파일을 소프트 삭제한다(문서함 목록에서 제외)
    @Override
    @DeleteMapping("/api/files/{fileId}")
    public ResponseEntity<BaseResponse<Void>> deleteFile(@PathVariable Long fileId) {
        fileService.deleteFile(fileId);
        return ResponseEntity.ok(BaseResponse.success());
    }

    // 소프트 삭제된 파일을 되돌려 다시 문서함 목록에 보이게 한다
    @Override
    @PostMapping("/api/files/{fileId}/restore")
    public ResponseEntity<BaseResponse<FileRestoreResponse>> restoreFile(@PathVariable Long fileId) {
        FileRestoreResponse response = fileService.restoreFile(fileId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 조건에 맞는 문서함 파일을 ZIP으로 묶어 스트리밍으로 내려준다
    @Override
    @GetMapping("/api/terms/{termId}/files/zip")
    public ResponseEntity<StreamingResponseBody> downloadZip(
            @PathVariable Long termId,
            @RequestParam(defaultValue = "ALL") FileCategoryFilter category,
            @RequestParam(required = false) String keyword
    ) {

        // 대상 조회(DB)와 실제 압축(스토리지 읽기+스트리밍)을 분리한다
        // — 응답 헤더(파일명 등)는 압축이 끝나기 전에 미리 확정해야 함.
        FileZipTarget target = fileService.getZipTargets(termId, category, keyword);
        String fileName = "%s자료%d개.zip".formatted(target.getGroupTermName(), target.getEntries().size());
        StreamingResponseBody body = out -> fileService.writeZipTo(target, out);

        // 한글 파일명이 깨지지 않도록 UTF-8로 인코딩해서 Content-Disposition에 싣는다
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.builder("attachment").filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
